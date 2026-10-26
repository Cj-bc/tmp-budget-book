package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.CommitAuthor
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.data.SavedFiles
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun open(files: InMemoryFiles): Ledger =
    runSuspend { Ledger.open(files, InMemoryStorage()) { "Preset ${it.legacyId}" } }

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

private const val OCTOBER = "transactions/2026-10.jsonl"

private fun Ledger.fundId(name: String): String = data.funds.single { it.name == name }.id.value

private fun Ledger.expense(n: Int, fund: String = "Preset food") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = LocalDate(2026, 10, 1),
    amount = 500,
    categoryId = checkNotNull(data.categoryNamed("Preset food", true)).id,
    memo = "",
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

/** Files of a ledger with the expense [n], whose line [edit] changes as an edit in the repository could. */
private fun editedExpense(n: Int = 1, edit: (String) -> String): InMemoryFiles {
    val files = InMemoryFiles()
    open(files).run { addTransaction(expense(n)) }
    files.files[OCTOBER] = edit(files.files.getValue(OCTOBER))
    return files
}

private val transaction1 = RecordKey(TRANSACTIONS_LIST, ulid(1))

class RecordCheckTest {
    @Test
    fun findsTheRecordsOfAFundThatTheRemoteDeletedAndMovesThemToAnother() {
        // This device recorded an expense from the transport fund, and another deleted the fund:
        // taking the other's master.json in hides the expense.
        val files = InMemoryFiles()
        val ledger = open(files)
        val other = InMemoryFiles().also { it.files += files.files }
        open(other).deleteFund("Preset transport")
        ledger.addTransaction(ledger.expense(1, fund = "Preset transport"))
        val transport = ledger.fundId("Preset transport")
        val food = ledger.fundId("Preset food")
        files.files[MASTER_FILE] = other.files.getValue(MASTER_FILE)
        assertTrue(open(files).data.transactions.isEmpty())

        val saved = runSuspend { SavedFiles.read(files) }
        val check = RecordsCheck(assertNotNull(saved.records))

        assertEquals(mapOf(Reference(ReferenceKind.FUND, transport) to listOf(transaction1)), check.missing)
        assertTrue(check.invalid.isEmpty())
        assertTrue(check.unfixable.isEmpty())
        assertTrue(check.others.isEmpty())
        assertTrue(food in check.candidates(ReferenceKind.FUND).map { it.first })

        saved.save(replaceReferences(check.records, mapOf(transport to food)), "Fix", CommitAuthor("Me", "me@example.invalid"))

        val moved = open(files).data.transactions.single() as Transaction.Expense
        assertEquals(food, moved.fundId.value)
        assertEquals("Fix", files.commits.last().message)
    }

    @Test
    fun pointsOutAnAmountThatIsNotANumberAndTakesTheFixedValue() {
        val files = editedExpense { it.replace("\"amount\":500", "\"amount\":\"five hundred\"") }
        val check = RecordsCheck(readRecords(files.files))

        val field = InvalidField(FieldPath(listOf("amount")), FieldType.NUMBER, FieldProblem.WRONG_FORM)
        assertEquals(mapOf(transaction1 to listOf(field)), check.invalid)
        assertTrue(check.missing.isEmpty())

        val wrong = RecordEdit(transaction1, check.records.getValue(transaction1), mapOf(field.path to (FieldType.NUMBER to "5OO")))
        assertEquals(setOf(field.path), wrong.problems.keys)
        assertEquals(check.records.getValue(transaction1), wrong.record)

        val fixed = RecordEdit(transaction1, check.records.getValue(transaction1), mapOf(field.path to (FieldType.NUMBER to "700")))
        assertTrue(fixed.problems.isEmpty())
        files.files += layoutRecords(check.records + (transaction1 to fixed.record))
        assertEquals(700, open(files).data.transactions.single().amount)
    }

    @Test
    fun pointsOutAnAmountThatIsNotPositive() {
        val files = editedExpense { it.replace("\"amount\":500", "\"amount\":0") }

        val check = RecordsCheck(readRecords(files.files))

        assertEquals(listOf(FieldProblem.NOT_POSITIVE), check.invalid.getValue(transaction1).map { it.problem })
    }

    @Test
    fun pointsOutAMissingValueAndAddsIt() {
        val files = editedExpense { it.replace(",\"memo\":\"\"", "") }
        val check = RecordsCheck(readRecords(files.files))
        val memo = FieldPath(listOf("memo"))

        assertEquals(listOf(InvalidField(memo, FieldType.TEXT, FieldProblem.MISSING)), check.invalid.getValue(transaction1))
        val fixed = RecordEdit(transaction1, check.records.getValue(transaction1), mapOf(memo to (FieldType.TEXT to "Lunch")))
        files.files += layoutRecords(check.records + (transaction1 to fixed.record))
        assertEquals("Lunch", (open(files).data.transactions.single() as Transaction.Expense).memo)
    }

    @Test
    fun pointsOutTheAmountsOfAnIncomeThatDoNotAddUp() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val category = ledger.data.categories.filterIsInstance<Category.Income>().first().id.value
        val income = buildJsonObject {
            put("v", 1)
            put("type", "income")
            put("id", ulid(2))
            put("transaction_date", "2026-10-25")
            put("amount", 1000)
            put("category_id", category)
            put("memo", "")
            put("budget_month", "2026-11")
            put(
                "allocations",
                buildJsonArray {
                    add(buildJsonObject { put("fund_id", ledger.fundId("Preset food")); put("amount", 600) })
                    add(buildJsonObject { put("fund_id", ledger.fundId("Preset transport")); put("amount", 300) })
                },
            )
        }
        files.files[OCTOBER] = "$income\n"
        val key = RecordKey(TRANSACTIONS_LIST, ulid(2))

        val check = RecordsCheck(readRecords(files.files))

        assertEquals(
            listOf(listOf("amount"), listOf("allocations", "0", "amount"), listOf("allocations", "1", "amount")),
            check.invalid.getValue(key).map { it.path.segments },
        )
        assertTrue(check.invalid.getValue(key).all { it.problem == FieldProblem.ALLOCATIONS_DO_NOT_ADD_UP })
        val fixed = RecordEdit(key, check.records.getValue(key), mapOf(FieldPath(listOf("amount")) to (FieldType.NUMBER to "900")))
        assertTrue(fixed.problems.isEmpty())
        files.files += layoutRecords(check.records + (key to fixed.record))
        assertEquals(900, open(files).data.transactions.single().amount)
    }

    @Test
    fun pointsOutADayOfTheMonthOutOfRange() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val item = buildJsonObject {
            put("id", ulid(3))
            put("name", "Rent")
            put("amount", 80000)
            put("category_id", checkNotNull(ledger.data.categoryNamed("Preset food", true)).id.value)
            put("fund_id", ledger.fundId("Preset food"))
            put("day_of_month", 31)
            put("enabled", true)
            put("last_applied_month", null as String?)
        }
        val records = readRecords(files.files) + (RecordKey(RECURRING_ITEMS, ulid(3)) to SavedRecord.Master(item))

        val check = RecordsCheck(records)

        val field = check.invalid.getValue(RecordKey(RECURRING_ITEMS, ulid(3))).single()
        assertEquals(FieldProblem.OUT_OF_RANGE, field.problem)
        assertEquals(listOf("day_of_month"), field.path.segments)
    }

    @Test
    fun tellsTheRecordsThatOnlyTheFilesCanFix() {
        val files = editedExpense {
            it + "{ broken\n" +
                it.replace("\"v\":1", "\"v\":2").replace(ulid(1), ulid(4)) +
                it.replace("\"amount\":500", "\"amount\":600")
        }

        val check = RecordsCheck(readRecords(files.files))

        assertEquals(
            setOf(UnfixableReason.NOT_JSON, UnfixableReason.UNKNOWN_VERSION, UnfixableReason.DUPLICATE_ID),
            check.unfixable.values.toSet(),
        )
        assertTrue(check.invalid.isEmpty())
        assertEquals(1, open(files).data.transactions.size)
    }

    @Test
    fun onlyLooksIntoTheRecordsItConcerns() {
        val files = editedExpense { it + "{ broken\n" }
        val records = readRecords(files.files)
        val before = unreadableRecords(records).map { (key, record) -> key to record }.toSet()

        assertTrue(RecordsCheck(records) { key, record -> (key to record) !in before }.hidden.isEmpty())
        assertEquals(1, RecordsCheck(records).hidden.size)
    }

    @Test
    fun replacesAValueInAListAndAddsAMissingKey() {
        val record = SavedRecord.Master(
            buildJsonObject {
                put("id", "a")
                put("lines", buildJsonArray { add(buildJsonObject { put("amount", 1) }) })
            },
        )

        val edited = record.withValues(
            mapOf(
                FieldPath(listOf("lines", "0", "amount")) to JsonPrimitive(2),
                FieldPath(listOf("name")) to JsonPrimitive("A"),
                FieldPath(listOf("lines", "5", "amount")) to JsonPrimitive(3),
            ),
        )

        assertEquals("""{"id":"a","lines":[{"amount":2}],"name":"A"}""", (edited as SavedRecord.Master).element.toString())
    }
}
