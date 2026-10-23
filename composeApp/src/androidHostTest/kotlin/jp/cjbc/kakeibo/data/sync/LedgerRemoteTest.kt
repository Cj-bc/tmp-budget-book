package jp.cjbc.kakeibo.data.sync

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import jp.cjbc.kakeibo.data.CommitAuthor
import jp.cjbc.kakeibo.data.GitDataDirectory
import jp.cjbc.kakeibo.data.files.readRecords
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.eclipse.jgit.api.Git

private const val FUND_A = """{"id":"01JA0000000000000000000000","name":"A"}"""
private const val FUND_B = """{"id":"01JB0000000000000000000000","name":"B"}"""
private const val FUND_A_RENAMED = """{"id":"01JA0000000000000000000000","name":"A2"}"""
private const val FUND_A_OTHER = """{"id":"01JA0000000000000000000000","name":"A3"}"""
private const val LINE_1 = """{"v":1,"id":"01JT0000000000000000000001","date":"2026-10-01"}"""
private const val LINE_2 = """{"v":1,"id":"01JT0000000000000000000002","date":"2026-10-02"}"""

/** Runs [block], which resumes on another thread, and waits for its result. */
private fun <T> blocking(block: suspend () -> T): T {
    val result = CompletableFuture<T>()
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome ->
        outcome.fold(result::complete, result::completeExceptionally)
    })
    return result.get(10, TimeUnit.SECONDS)
}

private fun master(vararg funds: String) = """{"version":1,"funds":[${funds.joinToString(",")}]}""" + "\n"

class LedgerRemoteTest {
    private val root: File = Files.createTempDirectory("remote").toFile()
    private val author = CommitAuthor("Me", "me@example.invalid")
    private val remoteUrl: String = File(root, "remote.git").also {
        Git.init().setBare(true).setDirectory(it).call().close()
    }.toURI().toString()

    @AfterTest
    fun deleteRoot() {
        root.deleteRecursively()
    }

    /** A device's ledger directory, connected to the remote. */
    private inner class Device(name: String) {
        val directory = GitDataDirectory(File(root, name))

        fun save(master: String, transactions: String? = null, message: String = "change") {
            directory.write("master.json", master)
            if (transactions != null) directory.write("transactions/2026-10.jsonl", transactions)
            directory.commit(message, author)
        }

        fun <T> git(task: (Git) -> T): T = blocking { directory.inRepository(task) }

        fun connect() = git { setRemoteUrl(it, remoteUrl) }

        fun fetch() = git { fetch(it, null) }

        fun push() = git { push(it, null) }

        fun analyze() = git { analyze(it) }

        fun merge() = git { merge(it, author) }

        fun files(): Map<String, String> = git { git ->
            listOf("master.json", "transactions/2026-10.jsonl")
                .mapNotNull { path -> File(git.repository.workTree, path).takeIf { it.isFile }?.let { path to it.readText() } }
                .toMap()
        }

        fun headParents(): Int = git { it.log().setMaxCount(1).call().single().parentCount }

        fun isClean(): Boolean = git { it.status().call().isClean }
    }

    @Test
    fun pushesToAnEmptyRemote() {
        val phone = Device("phone")
        phone.save(master(FUND_A), message = "Start the ledger")
        phone.connect()
        phone.fetch()

        assertEquals(Analysis(Divergence.REMOTE_EMPTY, localUntouched = true), phone.analyze())
        assertEquals(PushOutcome.PUSHED, phone.push())
        phone.fetch()
        assertEquals(Divergence.UP_TO_DATE, phone.analyze().divergence)
        assertEquals(PushOutcome.UP_TO_DATE, phone.push())
    }

    @Test
    fun tellsAnUntouchedLedgerFromOneWithData() {
        val phone = Device("phone")
        phone.save(master(FUND_A), message = "Start the ledger")
        phone.connect()
        phone.push()

        val tablet = Device("tablet")
        tablet.save(master(FUND_B), message = "Start the ledger")
        tablet.connect()
        tablet.fetch()
        assertEquals(Analysis(Divergence.UNRELATED, localUntouched = true), tablet.analyze())
        assertEquals(MergeOutcome.Unrelated, tablet.merge())

        tablet.save(master(FUND_B), LINE_1)
        assertEquals(Analysis(Divergence.UNRELATED, localUntouched = false), tablet.analyze())
    }

    @Test
    fun usesTheRemoteKeepingTheLocalCommitsAside() {
        val phone = Device("phone")
        phone.save(master(FUND_A), LINE_1)
        phone.connect()
        phone.push()

        val tablet = Device("tablet")
        tablet.save(master(FUND_B), LINE_2)
        tablet.connect()
        tablet.fetch()
        val local = tablet.git { it.repository.resolve("HEAD") }
        val setAside = tablet.git { useRemote(it, now = 1234) }

        assertEquals("${SET_ASIDE_REF_PREFIX}1234", setAside)
        assertEquals(local, tablet.git { it.repository.resolve(checkNotNull(setAside)) })
        assertEquals(phone.files(), tablet.files())
        assertEquals(Divergence.UP_TO_DATE, tablet.analyze().divergence)
    }

    @Test
    fun fastForwardsToTheRemote() {
        val phone = Device("phone")
        phone.save(master(FUND_A))
        phone.connect()
        phone.push()
        val tablet = Device("tablet")
        tablet.connect()
        tablet.fetch()
        tablet.git { useRemote(it, now = 1) }

        phone.save(master(FUND_A, FUND_B), LINE_1)
        phone.push()
        tablet.fetch()

        assertEquals(Divergence.REMOTE_AHEAD, tablet.analyze().divergence)
        assertEquals(MergeOutcome.FastForwarded, tablet.merge())
        assertEquals(phone.files(), tablet.files())
        assertEquals(MergeOutcome.NothingToDo, tablet.merge())
    }

    @Test
    fun mergesChangesToDifferentRecords() {
        val phone = Device("phone")
        phone.save(master(FUND_A), LINE_1)
        phone.connect()
        phone.push()
        val tablet = Device("tablet")
        tablet.connect()
        tablet.fetch()
        tablet.git { useRemote(it, now = 1) }

        phone.save(master(FUND_A_RENAMED), LINE_1)
        phone.push()
        tablet.save(master(FUND_A, FUND_B), LINE_1 + "\n" + LINE_2 + "\n")
        tablet.fetch()

        assertEquals(Divergence.DIVERGED, tablet.analyze().divergence)
        assertEquals(PushOutcome.REJECTED, tablet.push())
        assertEquals(MergeOutcome.Merged, tablet.merge())
        assertEquals(2, tablet.headParents())
        assertTrue(tablet.isClean())
        assertEquals(
            readRecords(mapOf("master.json" to master(FUND_A_RENAMED, FUND_B), "transactions/2026-10.jsonl" to "$LINE_1\n$LINE_2\n")),
            readRecords(tablet.files()),
        )
        assertEquals(Divergence.LOCAL_AHEAD, tablet.analyze().divergence)
        assertEquals(PushOutcome.PUSHED, tablet.push())
    }

    @Test
    fun leavesConflictsUnmerged() {
        val phone = Device("phone")
        phone.save(master(FUND_A))
        phone.connect()
        phone.push()
        val tablet = Device("tablet")
        tablet.connect()
        tablet.fetch()
        tablet.git { useRemote(it, now = 1) }

        phone.save(master(FUND_A_RENAMED))
        phone.push()
        tablet.save(master(FUND_A_OTHER))
        val before = tablet.files()
        tablet.fetch()

        assertEquals(MergeOutcome.Conflicted(1), tablet.merge())
        assertEquals(before, tablet.files())
        assertEquals(1, tablet.headParents())
        assertFalse(tablet.git { File(it.repository.directory, "MERGE_HEAD").exists() })
    }
}
