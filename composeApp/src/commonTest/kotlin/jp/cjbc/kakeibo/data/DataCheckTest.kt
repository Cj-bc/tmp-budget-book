package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.files.FieldIssue
import jp.cjbc.kakeibo.data.files.FieldKind
import jp.cjbc.kakeibo.data.files.FieldProblem
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecordTrouble
import jp.cjbc.kakeibo.data.files.Reference
import jp.cjbc.kakeibo.data.files.ReferenceKind
import jp.cjbc.kakeibo.data.files.TRANSACTIONS_LIST
import jp.cjbc.kakeibo.data.files.fieldText
import jp.cjbc.kakeibo.data.files.parse
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.JsonPrimitive

private fun open(files: InMemoryFiles): Ledger = runSuspend { Ledger.open(files, InMemoryStorage()) { "Preset ${it.legacyId}" } }

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

private const val OCTOBER = "transactions/2026-10.jsonl"

private fun Ledger.expense(n: Int, fund: String = "Preset food") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = LocalDate(2026, 10, 1),
    amount = 500,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = "",
    fundId = data.funds.single { it.name == fund }.id,
    recurringItemId = null,
)

/** A ledger with one expense, whose files the test then changes as someone editing them would. */
private fun filesWithExpense(): InMemoryFiles = InMemoryFiles().also { files -> open(files).run { addTransaction(expense(1)) } }

private fun InMemoryFiles.edit(path: String, change: (String) -> String) {
    files[path] = change(files.getValue(path))
}

private val expenseKey = RecordKey(TRANSACTIONS_LIST, ulid(1))

class DataCheckTest {
    @Test
    fun findsNothingInFilesTheAppWrote() {
        assertTrue(open(filesWithExpense()).check().isClean)
    }

    @Test
    fun movesTheRecordsOfAFundThatIsGoneAndShowsThem() {
        val files = filesWithExpense()
        val ledger = open(files)
        val food = ledger.data.funds.single { it.name == "Preset food" }.id.value
        val transport = ledger.data.funds.single { it.name == "Preset transport" }.id.value
        // The expense is paid from a fund that master.json does not have, as after pulling another
        // device's deletion of it.
        files.edit(OCTOBER) { it.replace(food, ulid(90)) }
        val reopened = open(files)
        val check = reopened.check()

        assertEquals(Reference(ReferenceKind.FUND, ulid(90)), check.missing.keys.single())
        assertTrue(expenseKey in check.missing.values.single())

        reopened.saveRecords(check.fixed(mapOf(ulid(90) to transport), emptyMap()), "Move the records")
        val fixed = open(files)
        assertTrue(fixed.check().isClean)
        assertEquals(transport, (fixed.data.transactions.single() as Transaction.Expense).fundId.value)
    }

    @Test
    fun pointsAtAWrongAmountAndSavesTheEditedOne() {
        val files = filesWithExpense()
        files.edit(OCTOBER) { it.replace("\"amount\":500", "\"amount\":0") }
        val ledger = open(files)
        assertTrue(ledger.data.transactions.isEmpty())
        val check = ledger.check()

        val trouble = assertIs<RecordTrouble.Fields>(check.troubles.getValue(expenseKey))
        assertEquals(listOf(FieldProblem("amount", FieldKind.WHOLE_NUMBER, FieldIssue.NOT_POSITIVE)), trouble.problems)
        assertEquals(listOf("amount" to FieldKind.WHOLE_NUMBER), trouble.editable)
        assertEquals("0", fieldText(check.records.getValue(expenseKey), "amount"))

        val amount = FieldKind.WHOLE_NUMBER.parse(" 700 ")!!
        ledger.saveRecords(check.fixed(emptyMap(), mapOf(expenseKey to mapOf("amount" to amount))), "Fix the amount")
        assertEquals(700, open(files).data.transactions.single().amount)
    }

    @Test
    fun pointsAtFieldsOfTheWrongKindOrRange() {
        val files = filesWithExpense()
        files.edit(OCTOBER) { it.replace("\"transaction_date\":\"2026-10-01\"", "\"transaction_date\":\"2026-13-01\"").replace(",\"memo\":\"\"", "") }
        val trouble = assertIs<RecordTrouble.Fields>(open(files).check().troubles.getValue(expenseKey))

        assertEquals(
            listOf(
                FieldProblem("transaction_date", FieldKind.DATE, FieldIssue.WRONG_TYPE),
                FieldProblem("memo", FieldKind.TEXT, FieldIssue.MISSING),
            ),
            trouble.problems,
        )
        assertNull(FieldKind.DATE.parse("2026-13-01"))
        assertEquals(JsonPrimitive("2026-12-01"), FieldKind.DATE.parse("2026-12-01"))
    }

    @Test
    fun letsTheAmountAndAllocationsOfAnIncomeBeEditedWhenTheyDoNotAddUp() {
        val files = InMemoryFiles()
        open(files).run {
            val food = data.funds.single { it.name == "Preset food" }.id
            val transport = data.funds.single { it.name == "Preset transport" }.id
            addTransaction(
                Transaction.Income(
                    id = TransactionId(ulid(1)),
                    date = LocalDate(2026, 10, 25),
                    amount = 1000,
                    categoryId = checkNotNull(data.categoryNamed("Preset salary", false)).id,
                    memo = "",
                    budgetMonth = YearMonth(2026, 11),
                    allocations = listOf(Allocation(food, 600), Allocation(transport, 400)),
                ),
            )
        }
        files.edit(OCTOBER) { it.replace("\"amount\":1000", "\"amount\":1100") }

        val trouble = assertIs<RecordTrouble.Fields>(open(files).check().troubles.getValue(expenseKey))

        assertEquals(listOf(FieldProblem("amount", FieldKind.WHOLE_NUMBER, FieldIssue.ALLOCATIONS_DO_NOT_ADD_UP)), trouble.problems)
        assertEquals(
            listOf("amount", "allocations.0.amount", "allocations.1.amount"),
            trouble.editable.map { it.first },
        )
    }

    @Test
    fun tellsWhatTheAppCannotFix() {
        val files = filesWithExpense()
        val line = files.files.getValue(OCTOBER).trim()
        files.files[OCTOBER] = line + "\n" + "not json\n" + line.replace("\"v\":1", "\"v\":2").replace(ulid(1), ulid(2)) + "\n" + line.replace("\"memo\":\"\"", "\"memo\":\"Copy\"") + "\n"

        val troubles = open(files).check().troubles.values.toList()

        assertEquals(3, troubles.size)
        assertEquals(2, troubles.count { it == RecordTrouble.Unreadable })
        assertEquals(1, troubles.count { it == RecordTrouble.DuplicateId })
    }

    @Test
    fun findsTheRecordsHiddenByAnotherThatCannotBeShown() {
        val files = filesWithExpense()
        val ledger = open(files)
        val category = checkNotNull(ledger.data.categoryNamed("Preset food", true)).id.value
        // The food category's order is no longer a number, which hides the expense of the category too.
        files.edit(MASTER_FILE) { master ->
            val start = master.indexOf("\"id\": \"$category\"")
            val order = master.indexOf("\"sort_order\": ", start)
            master.substring(0, order) + "\"sort_order\": \"first\"" + master.substring(master.indexOf(',', order))
        }
        val check = open(files).check()

        val categoryKey = RecordKey("expense_categories", category)
        assertEquals(
            listOf(FieldProblem("sort_order", FieldKind.WHOLE_NUMBER, FieldIssue.WRONG_TYPE)),
            assertIs<RecordTrouble.Fields>(check.troubles.getValue(categoryKey)).problems,
        )
        assertEquals(RecordTrouble.Dependent(listOf(categoryKey)), check.troubles.getValue(expenseKey))
    }
}
