package jp.cjbc.kakeibo.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId

/** Runs [block], which resumes on another thread, and waits for its result. */
private fun <T> blocking(block: suspend () -> T): T {
    val result = CompletableFuture<T>()
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome ->
        outcome.fold(result::complete, result::completeExceptionally)
    })
    return result.get(30, TimeUnit.SECONDS)
}

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

/** A device: its data directory, and the ledger in it. */
private class Device(val path: File) {
    val directory = GitDataDirectory(path)
    private val author = CommitAuthor("Me", "me@example.invalid")
    private val files = object : FileStorage {
        override suspend fun read(path: String) = directory.read(path)
        override suspend fun list(directory: String) = this@Device.directory.list(directory)
        override fun write(path: String, content: String) = directory.write(path, content)
        override fun delete(path: String) = directory.delete(path)
        override fun commit(message: String, author: CommitAuthor) = directory.commit(message, author)
    }

    fun open(): Ledger = blocking { Ledger.open(files, InMemoryStorage(), { author }) { "Preset ${it.legacyId}" } }

    fun sync(remote: GitRemote): SyncStatus? = blocking { directory.sync(author) { remote } }

    fun replaceWithRemote(remote: GitRemote): SyncStatus? = blocking { directory.replaceWithRemote(author) { remote } }

    fun pendingMerge(): PendingMerge? = blocking { directory.pendingMerge() }

    fun resolveMerge(
        merge: PendingMerge,
        choices: Map<RecordKey, Side>,
        remote: GitRemote,
        replacements: Map<String, String> = emptyMap(),
    ): SyncStatus? = blocking { directory.resolveMerge(merge, choices, replacements, author) { remote } }

    fun head(): ObjectId = git { it.repository.resolve("HEAD") }

    /** Runs [block] on the repository once the queued work is done, since reads run after it. */
    fun <T> git(block: (Git) -> T): T {
        blocking { directory.read("none") }
        return Git.open(path).use(block)
    }
}

private fun Ledger.expense(n: Int, memo: String = "", fund: String = "Preset food") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = LocalDate(2026, 10, 1),
    amount = 500,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = memo,
    fundId = data.funds.single { it.name == fund }.id,
    recurringItemId = null,
)

private val Ledger.memos: List<String> get() = data.transactions.map { (it as Transaction.Expense).memo }

/** Deletes the fund [name], moving the categories paid from it to the food fund. */
private fun Ledger.deleteFund(name: String) = update { data ->
    val deleted = data.funds.single { it.name == name }
    val food = data.funds.single { it.name == "Preset food" }
    data.copy(
        funds = data.funds - deleted,
        categories = data.categories.map {
            if (it is Category.Expense && it.defaultFundId == deleted.id) it.copy(defaultFundId = food.id) else it
        },
    )
}

class GitSyncTest {
    private val root: File = Files.createTempDirectory("sync").toFile()
    private val remoteDirectory = File(root, "remote.git").also {
        Git.init().setBare(true).setDirectory(it).setInitialBranch(LEDGER_BRANCH).call().close()
    }
    private val remote = GitRemote(remoteDirectory.toURI().toString(), credentials = null)
    private val a = Device(File(root, "a"))
    private val b = Device(File(root, "b"))

    @AfterTest
    fun deleteRoot() {
        root.deleteRecursively()
    }

    private fun remoteHead(): ObjectId? = Git.open(remoteDirectory).use { it.repository.resolve("refs/heads/$LEDGER_BRANCH") }

    /** Device a's ledger with an expense, pushed, and taken by device b. */
    private fun shared() {
        a.open().run { addTransaction(expense(1)) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        assertEquals(SyncStatus.Pulled, b.sync(remote))
    }

    @Test
    fun pushesToAnEmptyRemoteAndANewDeviceTakesItsHistory() {
        shared()

        assertEquals(a.head(), remoteHead())
        assertEquals(a.head(), b.head())
        assertEquals(listOf(ulid(1)), b.open().data.transactions.map { it.id.value })
        assertEquals(SyncStatus.UpToDate, b.sync(remote))
    }

    @Test
    fun takesInTheRemoteCommitsWhenOnlyTheRemoteChanged() {
        shared()
        a.open().run { addTransaction(expense(2)) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))

        assertEquals(SyncStatus.Pulled, b.sync(remote))
        assertEquals(a.head(), b.head())
        assertEquals(setOf(ulid(1), ulid(2)), b.open().data.transactions.map { it.id.value }.toSet())
    }

    @Test
    fun mergesTheRecordsBothSidesChangedAndPushesTheMerge() {
        shared()
        a.open().run { addTransaction(expense(2)) }
        b.open().run { addTransaction(expense(3)) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))

        assertEquals(SyncStatus.Pulled, b.sync(remote))

        val merge = b.git { it.log().call().first() }
        assertEquals(2, merge.parentCount)
        assertEquals(merge.id, remoteHead())
        assertEquals(setOf(ulid(1), ulid(2), ulid(3)), b.open().data.transactions.map { it.id.value }.toSet())
        // The other device takes the merge as it is.
        assertEquals(SyncStatus.Pulled, a.sync(remote))
        assertEquals(merge.id, a.head())
    }

    @Test
    fun stopsAtConflictsWithoutChangingAnything() {
        shared()
        a.open().run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Lunch"))) } }
        b.open().run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Dinner"))) } }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        val before = b.head()

        assertEquals(SyncStatus.Conflicts(1), b.sync(remote))
        assertEquals(before, b.head())
        assertEquals("Dinner", (b.open().data.transactions.single() as Transaction.Expense).memo)
        assertTrue(b.git { it.status().call().isClean })
    }

    /** Device a and b changed the memo of the shared expense, a pushed, and b stopped at the conflict. */
    private fun conflicting(): PendingMerge {
        shared()
        a.open().run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Lunch"))) } }
        b.open().run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Dinner"))) } }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        assertEquals(SyncStatus.Conflicts(1), b.sync(remote))
        return assertNotNull(b.pendingMerge())
    }

    @Test
    fun commitsAndPushesTheMergeWithTheOtherDevicesVersion() {
        val merge = conflicting()
        assertEquals(RecordKey("transactions", ulid(1)), merge.conflicts.single().key)

        assertEquals(SyncStatus.Pulled, b.resolveMerge(merge, mapOf(merge.conflicts.single().key to Side.THEIRS), remote))

        val commit = b.git { it.log().call().first() }
        assertEquals(2, commit.parentCount)
        assertEquals(commit.id, remoteHead())
        assertEquals(listOf("Lunch"), b.open().memos)
        assertNull(b.pendingMerge())
        assertTrue(b.git { it.status().call().isClean })
    }

    @Test
    fun commitsAndPushesTheMergeWithThisDevicesVersion() {
        val merge = conflicting()

        assertEquals(SyncStatus.Pulled, b.resolveMerge(merge, mapOf(merge.conflicts.single().key to Side.OURS), remote))

        assertEquals(b.head(), remoteHead())
        assertEquals(listOf("Dinner"), b.open().memos)
        assertEquals(SyncStatus.Pulled, a.sync(remote))
        assertEquals(listOf("Dinner"), a.open().memos)
    }

    @Test
    fun mergesAgainWhenTheRemoteMovedWhileChoosing() {
        val merge = conflicting()
        val before = b.head()
        a.open().run { addTransaction(expense(2, memo = "Coffee")) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))

        assertEquals(SyncStatus.Conflicts(1), b.resolveMerge(merge, mapOf(merge.conflicts.single().key to Side.THEIRS), remote))

        assertEquals(before, b.head())
        val again = assertNotNull(b.pendingMerge())
        assertNotEquals(merge.revision, again.revision)
        assertEquals(merge.conflicts, again.conflicts)
    }

    @Test
    fun stopsBeforeAMergeThatHidesRecords() {
        shared()
        a.open().deleteFund("Preset transport")
        b.open().run { addTransaction(expense(2, fund = "Preset transport")) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        val before = b.head()

        assertEquals(SyncStatus.HidesRecords(1), b.sync(remote))
        assertEquals(before, b.head())
        val merge = assertNotNull(b.pendingMerge())
        assertTrue(merge.conflicts.isEmpty())
        assertEquals(setOf(RecordKey("transactions", ulid(2))), merge.hiddenBy(emptyMap()).keys)

        assertEquals(SyncStatus.Pulled, b.resolveMerge(merge, emptyMap(), remote))
        assertEquals(b.head(), remoteHead())
        assertEquals(listOf(ulid(1)), b.open().data.transactions.map { it.id.value })
        // The hidden expense stays in the files.
        assertTrue(ulid(2) in b.git { File(it.repository.workTree, "transactions/2026-10.jsonl").readText() })
    }

    @Test
    fun movesTheRecordsOfADeletedFundToAnother() {
        shared()
        a.open().deleteFund("Preset transport")
        b.open().run { addTransaction(expense(2, fund = "Preset transport")) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        assertEquals(SyncStatus.HidesRecords(1), b.sync(remote))
        val merge = assertNotNull(b.pendingMerge())
        val deleted = merge.missingBy(emptyMap()).keys.single()
        val food = b.open().data.funds.single { it.name == "Preset food" }.id.value

        assertEquals(SyncStatus.Pulled, b.resolveMerge(merge, emptyMap(), remote, mapOf(deleted.id to food)))

        assertEquals(b.head(), remoteHead())
        val moved = b.open().data.transactions.single { it.id.value == ulid(2) } as Transaction.Expense
        assertEquals(food, moved.fundId.value)
        assertEquals(SyncStatus.Pulled, a.sync(remote))
        assertEquals(setOf(ulid(1), ulid(2)), a.open().data.transactions.map { it.id.value }.toSet())
    }

    @Test
    fun asksBeforeReplacingAnUnrelatedHistory() {
        a.open().run { addTransaction(expense(1)) }
        assertEquals(SyncStatus.Pushed, a.sync(remote))
        b.open().run { addTransaction(expense(2)) }
        val local = b.head()

        assertEquals(SyncStatus.UnrelatedHistories, b.sync(remote))
        assertEquals(local, b.head())

        assertEquals(SyncStatus.Pulled, b.replaceWithRemote(remote))
        assertEquals(a.head(), b.head())
        assertEquals(listOf(ulid(1)), b.open().data.transactions.map { it.id.value })
        val kept = b.git { git -> git.branchList().call().single { it.name.startsWith("refs/heads/local-") } }
        assertEquals(local, kept.objectId)
    }

    @Test
    fun pushesTheCommitsMadeSince() {
        shared()
        b.open().run { addTransaction(expense(2)) }
        val pushed = CompletableFuture<SyncStatus>()
        b.directory.push({ remote }, pushed::complete)

        assertEquals(SyncStatus.Pushed, pushed.get(30, TimeUnit.SECONDS))
        assertEquals(b.head(), remoteHead())
    }

    @Test
    fun reportsAFailureWithoutTheRemote() {
        a.open()
        val missing = GitRemote(File(root, "missing.git").toURI().toString(), credentials = null)
        assertTrue(a.sync(missing) is SyncStatus.Failed)
    }
}
