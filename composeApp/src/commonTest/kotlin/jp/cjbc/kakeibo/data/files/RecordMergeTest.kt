package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

private fun open(files: InMemoryFiles): Ledger =
    runSuspend { Ledger.open(files, InMemoryStorage()) { "Preset ${it.legacyId}" } }

/** A copy of [files], to be changed as another device would. */
private fun InMemoryFiles.copy(): InMemoryFiles = InMemoryFiles().also { it.files += files }

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

private fun Ledger.expense(n: Int, date: LocalDate = LocalDate(2026, 10, 1), memo: String = "") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = date,
    amount = 500,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = memo,
    fundId = data.funds.single { it.name == "Preset food" }.id,
    recurringItemId = null,
)

/** Adds a recurring item of rent, paid on the 27th. */
private fun Ledger.addRent() = update {
    it.copy(
        recurringItems = listOf(
            RecurringItem(
                id = RecurringItemId(ulid(99)),
                name = "Rent",
                amount = 80_000,
                categoryId = checkNotNull(it.categoryNamed("Preset food", true)).id,
                fundId = it.funds.single { fund -> fund.name == "Preset food" }.id,
                dayOfMonth = 27,
                enabled = true,
                lastAppliedMonth = null,
            ),
        ),
    )
}

class RecordMergeTest {
    @Test
    fun takesTheChangesOfEitherSideAndReportsConflicts() {
        val base = mapOf("ours" to 1, "theirs" to 1, "same" to 1, "both" to 1, "deleted" to 1, "changedDeleted" to 1, "bothDeleted" to 1)
        val ours = mapOf("ours" to 2, "theirs" to 1, "same" to 2, "both" to 2, "changedDeleted" to 2, "added" to 1)
        val theirs = mapOf("ours" to 1, "theirs" to 3, "same" to 2, "both" to 3, "deleted" to 1, "addedThere" to 1)

        val merge = mergeRecords(base, ours, theirs)

        assertEquals(mapOf("ours" to 2, "theirs" to 3, "same" to 2, "added" to 1, "addedThere" to 1), merge.merged)
        assertEquals(
            listOf(Conflict("both", 1, 2, 3), Conflict("changedDeleted", 1, 2, null)),
            merge.conflicts,
        )
        assertEquals(merge.merged + ("both" to 3), merge.resolve { it.theirs })
        assertEquals(merge.merged + mapOf("both" to 2, "changedDeleted" to 2), merge.resolve { it.ours })
    }

    @Test
    fun mergesTheFilesOfTwoDevicesRecordByRecord() {
        val base = InMemoryFiles()
        open(base).run { addTransaction(expense(1)) }
        val ours = base.copy()
        val theirs = base.copy()
        // Ours adds an expense and moves the first to November; theirs renames a fund.
        open(ours).run {
            addTransaction(expense(2))
            val moved = expense(1, LocalDate(2026, 11, 3))
            update { data -> data.copy(transactions = data.transactions.map { if (it.id == moved.id) moved else it }) }
        }
        open(theirs).update { data -> data.copy(funds = data.funds.map { if (it.name == "Preset transport") it.copy(name = "Commute") else it }) }

        val merge = mergeFiles(base.files, ours.files, theirs.files)
        assertTrue(merge.conflicts.isEmpty())
        val merged = InMemoryFiles().apply { files += layoutRecords(merge.merged) }

        val ledger = open(merged)
        assertEquals(listOf(LocalDate(2026, 11, 3), LocalDate(2026, 10, 1)), ledger.data.transactions.map { it.date })
        assertTrue(ledger.data.funds.any { it.name == "Commute" })
        assertEquals(setOf(MASTER_FILE, "transactions/2026-10.jsonl", "transactions/2026-11.jsonl"), merged.files.keys)
        // The merged files are laid out as the ledger itself saves them.
        val resaved = merged.copy()
        open(resaved).update { it }
        assertEquals(merged.files, resaved.files)
    }

    @Test
    fun reportsATransactionBothSidesChanged() {
        val base = InMemoryFiles()
        open(base).run { addTransaction(expense(1)) }
        val ours = base.copy().also { open(it).run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Lunch"))) } } }
        val theirs = base.copy().also { open(it).run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Dinner"))) } } }

        val conflict = mergeFiles(base.files, ours.files, theirs.files).conflicts.single()

        assertEquals(RecordKey(TRANSACTIONS_LIST, ulid(1)), conflict.key)
        assertTrue("\"memo\":\"Lunch\"" in (conflict.ours as SavedRecord.TransactionLine).line)
        assertTrue("\"memo\":\"Dinner\"" in (conflict.theirs as SavedRecord.TransactionLine).line)
    }

    @Test
    fun recurringExpensesRecordedOfflineOnTwoDevicesMergeIntoOne() {
        val base = InMemoryFiles()
        open(base).addRent()
        val ours = base.copy().also { open(it).applyRecurringItems(LocalDate(2026, 10, 27)) }
        val theirs = base.copy().also { open(it).applyRecurringItems(LocalDate(2026, 10, 30)) }

        val merge = mergeFiles(base.files, ours.files, theirs.files)

        assertTrue(merge.conflicts.isEmpty())
        val ledger = open(InMemoryFiles().apply { files += layoutRecords(merge.merged) })
        val rent = ledger.data.transactions.single() as Transaction.Expense
        assertEquals(80_000, rent.amount)
        assertEquals(LocalDate(2026, 10, 27), rent.date)
    }

    @Test
    fun reportsARecurringExpenseOneDeviceChangedAfterRecordingIt() {
        val base = InMemoryFiles()
        open(base).addRent()
        val ours = base.copy().also {
            open(it).run {
                applyRecurringItems(LocalDate(2026, 10, 27))
                update { d -> d.copy(transactions = d.transactions.map { t -> (t as Transaction.Expense).copy(amount = 82_000) }) }
            }
        }
        val theirs = base.copy().also { open(it).applyRecurringItems(LocalDate(2026, 10, 27)) }

        val conflict = mergeFiles(base.files, ours.files, theirs.files).conflicts.single()

        assertEquals(TRANSACTIONS_LIST, conflict.key.list)
        assertNull(conflict.base)
        assertTrue("\"amount\":82000" in (conflict.ours as SavedRecord.TransactionLine).line)
        assertTrue("\"amount\":80000" in (conflict.theirs as SavedRecord.TransactionLine).line)
    }

    @Test
    fun withoutACommonAncestorTakesTheRecordsOfBoth() {
        val ours = InMemoryFiles().also { open(it).run { addTransaction(expense(1)) } }
        val theirs = InMemoryFiles().also { open(it).run { addTransaction(expense(2)) } }

        val merge = mergeFiles(emptyMap(), ours.files, theirs.files)

        // The two ledgers were started apart, so they share no fund or category; only the two
        // expenses, which refer to their own ledger's records, have different IDs too.
        assertTrue(merge.conflicts.isEmpty())
        val records = merge.merged
        assertEquals(2, records.keys.count { it.list == TRANSACTIONS_LIST })
        assertEquals(readRecords(ours.files).size + readRecords(theirs.files).size, records.size)
    }

    @Test
    fun readingAndLayingOutRecordsKeepsTheFiles() {
        val files = InMemoryFiles()
        open(files).run {
            addTransaction(expense(1))
            addTransaction(expense(2, LocalDate(2026, 9, 30)))
        }
        files.files["transactions/2026-10.jsonl"] = files.files.getValue("transactions/2026-10.jsonl") + "not json\n"
        assertEquals(files.files, layoutRecords(readRecords(files.files)))
        assertTrue(readRecords(emptyMap()).isEmpty())
    }
}
