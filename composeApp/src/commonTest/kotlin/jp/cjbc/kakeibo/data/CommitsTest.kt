package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

private fun open(files: InMemoryFiles, legacy: InMemoryStorage = InMemoryStorage()): Ledger =
    runSuspend { Ledger.open(files, legacy) { "Preset ${it.legacyId}" } }

private fun Ledger.lunch(id: String = "01ARZ3NDEKTSV4RRFFQ69G5F01") = Transaction.Expense(
    id = TransactionId(id),
    date = LocalDate(2026, 10, 1),
    amount = 480,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = "Lunch",
    fundId = data.funds.single { it.name == "Preset food" }.id,
    recurringItemId = null,
)

class CommitsTest {
    @Test
    fun commitsEachChangeWithWhatItChanged() {
        val files = InMemoryFiles()
        val ledger = open(files)
        ledger.addTransaction(ledger.lunch())
        ledger.deleteTransaction(ledger.lunch().id)

        assertEquals(
            listOf(
                "Start the ledger",
                "Add expense of 480 yen on 2026-10-01 (Preset food)",
                "Delete expense of 480 yen on 2026-10-01 (Preset food)",
            ),
            files.commits.map { it.message },
        )
        assertEquals(setOf(CommitAuthor.DEFAULT), files.commits.map { it.author }.toSet())
        // Each commit has the files as that change left them.
        assertEquals(files.files, files.commits.last().files)
    }

    @Test
    fun commitsByTheAuthorFromTheSettings() {
        val files = InMemoryFiles()
        val legacy = InMemoryStorage()
        val ledger = open(files, legacy)
        val me = CommitAuthor("Me", "me@example.invalid")
        writeCommitAuthor(me, legacy)
        assertEquals(me, readCommitAuthor(legacy))

        ledger.addTransaction(ledger.lunch())

        assertEquals(me, files.commits.last().author)
    }

    @Test
    fun commitsARestoredBackup() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val backup = open(InMemoryFiles()).apply { addTransaction(lunch()) }.exportBackup()
        runSuspend { ledger.importBackup(backup) { "Preset ${it.legacyId}" } }
        assertEquals("Restore a backup", files.commits.last().message)
    }

    @Test
    fun commitsFilesChangedOutsideTheLedgerWhenOpened() {
        val files = InMemoryFiles()
        open(files)
        files.files["transactions/2026-10.jsonl"] = "not json\n"
        open(files)
        assertEquals("Record changes to the files that were not committed", files.commits.last().message)
    }

    @Test
    fun describesSeveralChangesInTheBody() {
        val ledger = open(InMemoryFiles())
        val before = ledger.data
        val food = before.funds.single { it.name == "Preset food" }
        val after = before.copy(
            transactions = listOf(ledger.lunch()),
            funds = before.funds.map { if (it == food) it.copy(name = "Meals") else it },
        )
        assertEquals(
            """
            Add expense of 480 yen on 2026-10-01 (Preset food), and 1 more

            - Add expense of 480 yen on 2026-10-01 (Preset food)
            - Update fund Meals
            """.trimIndent(),
            describeChanges(before, after),
        )
        assertEquals("Save the ledger", describeChanges(before, before))
        assertEquals("Delete fund Preset food", describeChanges(before, before.copy(funds = before.funds - food)))
        assertEquals("Add fund Preset food", describeChanges(LedgerData(), LedgerData(funds = listOf(food))))
    }
}
