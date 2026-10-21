package jp.cjbc.kakeibo.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk

/** Runs [block], which resumes on another thread, and waits for its result. */
private fun <T> blocking(block: suspend () -> T): T {
    val result = CompletableFuture<T>()
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome ->
        outcome.fold(result::complete, result::completeExceptionally)
    })
    return result.get(10, TimeUnit.SECONDS)
}

class GitDataDirectoryTest {
    private val root: File = Files.createTempDirectory("ledger").toFile()
    private val directory = GitDataDirectory(File(root, "ledger"))
    private val author = CommitAuthor("Me", "me@example.invalid")

    @AfterTest
    fun deleteRoot() {
        root.deleteRecursively()
    }

    /** Waits for the queued work, since reads run after it. */
    private fun settle() = blocking { directory.read("none") }

    private fun log(): List<RevCommit> =
        Git.open(File(root, "ledger")).use { git -> git.log().call().toList() }

    private fun filesOf(commit: RevCommit): Map<String, String> =
        Git.open(File(root, "ledger")).use { git ->
            TreeWalk(git.repository).use { walk ->
                walk.addTree(commit.tree)
                walk.isRecursive = true
                buildMap {
                    while (walk.next()) {
                        put(walk.pathString, git.repository.open(walk.getObjectId(0)).bytes.decodeToString())
                    }
                }
            }
        }

    @Test
    fun commitsTheFilesOnTheMainBranch() {
        directory.write("master.json", "{}\n")
        directory.write("transactions/2026-10.jsonl", "a\n")
        directory.commit("Start the ledger", author)
        settle()

        val commit = log().single()
        assertEquals("Start the ledger", commit.fullMessage)
        assertEquals("Me", commit.authorIdent.name)
        assertEquals("me@example.invalid", commit.committerIdent.emailAddress)
        assertEquals(
            mapOf(
                ".gitattributes" to "*.json text eol=lf\n*.jsonl text eol=lf\n",
                "master.json" to "{}\n",
                "transactions/2026-10.jsonl" to "a\n",
            ),
            filesOf(commit),
        )
        assertEquals(LEDGER_BRANCH, Git.open(File(root, "ledger")).use { it.repository.branch })
    }

    @Test
    fun commitsDeletionsAndSkipsCommitsWithoutChanges() {
        directory.write("master.json", "{}\n")
        directory.write("transactions/2026-10.jsonl", "a\n")
        directory.commit("first", author)
        directory.delete("transactions/2026-10.jsonl")
        directory.commit("second", author)
        directory.commit("nothing changed", author)
        settle()

        assertEquals(listOf("second", "first"), log().map { it.fullMessage })
        assertFalse("transactions/2026-10.jsonl" in filesOf(log().first()))
    }

    @Test
    fun readsWhatWasWrittenBefore() {
        directory.write("transactions/2026-10.jsonl", "a\n")
        assertEquals("a\n", blocking { directory.read("transactions/2026-10.jsonl") })
        assertEquals(listOf("2026-10.jsonl"), blocking { directory.list("transactions") })
        assertNull(blocking { directory.read("master.json") })
    }
}
