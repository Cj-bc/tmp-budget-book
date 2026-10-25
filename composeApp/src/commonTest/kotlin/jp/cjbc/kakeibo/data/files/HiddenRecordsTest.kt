package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.data.PendingMerge
import jp.cjbc.kakeibo.data.Side
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
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

private fun InMemoryFiles.copy(): InMemoryFiles = InMemoryFiles().also { it.files += files }

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

private fun Ledger.expense(n: Int, fund: String = "Preset food", memo: String = "") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = LocalDate(2026, 10, 1),
    amount = 500,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = memo,
    fundId = data.funds.single { it.name == fund }.id,
    recurringItemId = null,
)

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

class HiddenRecordsTest {
    @Test
    fun findsTheExpensesPaidFromAFundThatTheOtherSideDeleted() {
        val base = InMemoryFiles()
        open(base)
        val ours = base.copy().also { open(it).run { addTransaction(expense(1, fund = "Preset transport")) } }
        val theirs = base.copy().also { open(it).deleteFund("Preset transport") }
        val baseRecords = readRecords(base.files)
        val ourRecords = readRecords(ours.files)
        val theirRecords = readRecords(theirs.files)

        val merge = mergeRecords(baseRecords, ourRecords, theirRecords)

        assertTrue(merge.conflicts.isEmpty())
        val hidden = hiddenByMerge(merge.merged, ourRecords, theirRecords)
        assertEquals(setOf(RecordKey(TRANSACTIONS_LIST, ulid(1))), hidden.keys)
        assertEquals(hidden, unreadableRecords(merge.merged))
    }

    @Test
    fun namesTheDeletedFundWithTheRecordsItHidesAndReplacesIt() {
        val base = InMemoryFiles()
        open(base)
        // Ours adds a category paid from the transport fund, and an expense of that category paid
        // from the food fund; theirs deletes the transport fund.
        val ours = base.copy().also {
            open(it).run {
                val transport = data.funds.single { f -> f.name == "Preset transport" }.id
                val taxi = Category.Expense(CategoryId(ulid(50)), "Taxi", pinned = false, sortOrder = 99, archived = false, defaultFundId = transport)
                update { d -> d.copy(categories = d.categories + taxi) }
                addTransaction(expense(1).copy(categoryId = taxi.id))
            }
        }
        val theirs = base.copy().also { open(it).deleteFund("Preset transport") }
        val ourRecords = readRecords(ours.files)
        val theirRecords = readRecords(theirs.files)
        val merged = mergeRecords(readRecords(base.files), ourRecords, theirRecords).merged
        val transport = ourRecords.keys.single { it.list == "funds" && "Preset transport" in ourRecords.getValue(it).toString() }

        val missing = missingReferences(hiddenByMerge(merged, ourRecords, theirRecords), merged)

        assertEquals(setOf(Reference(ReferenceKind.FUND, transport.id)), missing.keys)
        assertEquals(
            setOf(RecordKey("expense_categories", ulid(50)), RecordKey(TRANSACTIONS_LIST, ulid(1))),
            missing.values.single().toSet(),
        )
        val food = ourRecords.keys.single { it.list == "funds" && "Preset food" in ourRecords.getValue(it).toString() }
        val replaced = replaceReferences(merged, mapOf(transport.id to food.id))
        assertTrue(hiddenByMerge(replaced, ourRecords, theirRecords).isEmpty())
        // Only the record that referred to the fund changed.
        assertEquals(merged.filterKeys { it != RecordKey("expense_categories", ulid(50)) }, replaced.filterKeys { it != RecordKey("expense_categories", ulid(50)) })
    }

    @Test
    fun leavesOutTheRecordsThatASideCouldNotShowEither() {
        val files = InMemoryFiles()
        open(files).run { addTransaction(expense(1)) }
        files.files["transactions/2026-10.jsonl"] = files.files.getValue("transactions/2026-10.jsonl") + "not json\n"
        val records = readRecords(files.files)

        assertEquals(1, unreadableRecords(records).size)
        assertTrue(hiddenByMerge(records, records, emptyMap()).isEmpty())
    }

    @Test
    fun resolvesThePendingConflictsBySide() {
        val base = InMemoryFiles()
        open(base).run { addTransaction(expense(1)) }
        val ours = base.copy().also { open(it).run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Lunch"))) } } }
        val theirs = base.copy().also { open(it).run { update { d -> d.copy(transactions = listOf(expense(1, memo = "Dinner"))) } } }
        val pending = PendingMerge(
            mergeFiles(base.files, ours.files, theirs.files),
            readRecords(ours.files),
            readRecords(theirs.files),
            revision = "ours theirs",
        )
        val key = pending.conflicts.single().key

        fun memo(side: Side) = (decodeRecord(key, pending.resolve(mapOf(key to side)).getValue(key)) as TransactionRecord.Expense).memo
        assertEquals("Lunch", memo(Side.OURS))
        assertEquals("Dinner", memo(Side.THEIRS))
        assertTrue(pending.hiddenBy(mapOf(key to Side.THEIRS)).isEmpty())
    }

    @Test
    fun decodesARecordAsTheClassOfItsList() {
        val files = InMemoryFiles()
        open(files).run { addTransaction(expense(1)) }
        val records = readRecords(files.files)

        val fund = records.entries.first { it.key.list == "funds" }
        assertTrue(decodeRecord(fund.key, fund.value) is FundRecord)
        val expense = records.getValue(RecordKey(TRANSACTIONS_LIST, ulid(1)))
        assertEquals(ulid(1), (decodeRecord(RecordKey(TRANSACTIONS_LIST, ulid(1)), expense) as TransactionRecord.Expense).id)
        val newer = SavedRecord.TransactionLine("transactions/2026-10.jsonl", (expense as SavedRecord.TransactionLine).line.replace("\"v\":1", "\"v\":2"))
        assertNull(decodeRecord(RecordKey(TRANSACTIONS_LIST, ulid(1)), newer))
    }
}
