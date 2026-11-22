package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.transactionsFile
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.PRESET_EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryName
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import jp.cjbc.kakeibo.util.isUlid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun open(files: InMemoryFiles = InMemoryFiles(), legacy: InMemoryStorage = InMemoryStorage()): Ledger =
    runSuspend { Ledger.open(files, legacy) { "Preset ${it.legacyId}" } }

/** The [n]th of some ULIDs. Backups of IDs that are not ULIDs get new ones when imported. */
private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

/** The fund created with the preset category food. */
private val Ledger.foodFund: FundId get() = data.funds.single { it.name == "Preset food" }.id

/** The ID of the preset category named [name] in this ledger. */
private fun Ledger.categoryId(name: String, isExpense: Boolean = true): CategoryId =
    checkNotNull(data.categoryNamed(name, isExpense)).id

private fun Ledger.expense(n: Int, memo: String = "", amount: Long = 500) = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = LocalDate(2026, 10, 1),
    amount = amount,
    categoryId = categoryId("Preset food"),
    memo = memo,
    fundId = foodFund,
    recurringItemId = null,
)

private fun v1ExpenseJson(id: String, description: String) = buildJsonObject {
    put("id", id)
    put("date", "2026-09-10")
    put("amount", 1200.0)
    put("type", "EXPENSE")
    put("category", "food")
    put("description", description)
    put("isRecurring", false)
}

/** Data saved by version 2: presets, a category the user added, and `other` of both kinds. */
private fun v2LedgerJson() = Json.parseToJsonElement(
    """
    {
      "funds": [{"id": "fund-food", "name": "Food", "color": 4290270525, "openingBalance": 0,
                 "goal": {"type": "none"}, "showInEntry": true, "sortOrder": 0, "archived": false}],
      "categories": [
        {"type": "expense", "id": "food", "pinned": true, "sortOrder": 0, "archived": false, "defaultFundId": "fund-food"},
        {"type": "expense", "id": "Hobby", "pinned": false, "sortOrder": 1, "archived": false, "defaultFundId": "fund-food"},
        {"type": "expense", "id": "other", "pinned": false, "sortOrder": 2, "archived": false, "defaultFundId": "fund-food"},
        {"type": "income", "id": "salary", "pinned": false, "sortOrder": 0, "archived": false},
        {"type": "income", "id": "other", "pinned": false, "sortOrder": 1, "archived": false}
      ],
      "transactions": [{"type": "expense", "id": "t1", "date": "2026-09-10", "amount": 1200, "categoryId": "Hobby",
                        "memo": "Lunch", "fundId": "fund-food", "recurringItemId": null}]
    }
    """,
).jsonObject

/** The names of the expense categories, then those of the income ones, each in their order. */
private fun LedgerData.categoryNamesInOrder(): List<String> =
    categories.sortedWith(compareBy({ it is Category.Income }, { it.sortOrder })).map { it.name }

/** Deletes the fund [id] from master.json in [files], as another device or a hand edit may. */
private fun deleteFundFromMaster(files: InMemoryFiles, id: FundId) {
    val master = Json.parseToJsonElement(files.files.getValue(MASTER_FILE)).jsonObject
    val funds = master.getValue("funds") as JsonArray
    val kept = JsonArray(funds.filter { (it as JsonObject)["id"] != JsonPrimitive(id.value) })
    assertEquals(funds.size - 1, kept.size)
    files.files[MASTER_FILE] = JsonObject(master + ("funds" to kept)).toString()
}

/** A transaction line of a version this app does not know. */
private const val FUTURE_LINE = """{"v":99,"id":"01ARZ3NDEKTSV4RRFFQ69G5F99","transaction_date":"2026-10-01"}"""

private fun Ledger.import(raw: String): ImportResult = runSuspend { importBackup(raw) { "Preset ${it.legacyId}" } }

class LedgerTest {
    @Test
    fun aNewLedgerHasThePresetCategoriesEachWithAFund() {
        val ledger = open()
        assertEquals(
            PRESET_EXPENSE_CATEGORIES.map { "Preset ${it.legacyId}" },
            ledger.data.categories.filterIsInstance<Category.Expense>().sortedBy { it.sortOrder }.map { it.name },
        )
        assertEquals(ledger.foodFund, (ledger.data.categoryNamed("Preset food", true) as Category.Expense).defaultFundId)
        assertTrue(ledger.data.transactions.isEmpty())
    }

    @Test
    fun dataSurvivesReopening() {
        val files = InMemoryFiles()
        val ledger = open(files).apply { addTransaction(expense(1, "Lunch")) }
        val reopened = open(files)
        assertEquals(listOf(reopened.expense(1, "Lunch")), reopened.data.transactions)
        assertEquals(ledger.data, reopened.data)
    }

    @Test
    fun migratesDataSavedByVersion1() {
        val files = InMemoryFiles()
        val legacy = InMemoryStorage()
        legacy.values["kakeibo:v1:transactions"] =
            buildJsonArray { add(v1ExpenseJson("t1", "Lunch")) }.toString()
        legacy.values["kakeibo:v1:budgets"] = "not json"

        val ledger = open(files, legacy)

        val expense = ledger.data.transactions.single() as Transaction.Expense
        assertEquals("Lunch", expense.memo)
        assertEquals(ledger.foodFund, expense.fundId)
        assertTrue(isUlid(expense.id.value))
        assertTrue(isUlid(expense.fundId.value))
        assertTrue(MASTER_FILE in files.files)
        // Reopening reads the files instead of migrating again.
        legacy.values.remove("kakeibo:v1:transactions")
        assertEquals(ledger.data, open(files, legacy).data)
    }

    @Test
    fun keepsUnreadableSavedDataAside() {
        val legacy = InMemoryStorage()
        legacy.values["kakeibo:v3:ledger"] = "{ broken"
        val ledger = open(legacy = legacy)
        assertTrue(ledger.data.transactions.isEmpty())
        assertEquals("{ broken", legacy.values["kakeibo:v3:ledger:unreadable"])
    }

    @Test
    fun namesTheCategoriesOfDataSavedByVersion2() {
        val files = InMemoryFiles()
        val legacy = InMemoryStorage()
        legacy.values["kakeibo:v2:ledger"] = v2LedgerJson().toString()

        val ledger = open(files, legacy)

        assertEquals(
            listOf("Preset food", "Hobby", "Preset other", "Preset salary", "Preset other"),
            ledger.data.categoryNamesInOrder(),
        )
        // Each category got a new ID, unique even between the two kinds of `other`.
        assertEquals(5, ledger.data.categories.map { it.id }.toSet().size)
        assertTrue(ledger.data.categories.all { it.id.value.length == 26 })
        val transaction = ledger.data.transactions.single()
        assertEquals("Lunch", transaction.memo)
        assertEquals("Hobby", ledger.data.categoryName(transaction.categoryId))
        // Reopening reads the files instead of migrating again.
        legacy.values.remove("kakeibo:v2:ledger")
        assertEquals(ledger.data, open(files, legacy).data)
    }

    @Test
    fun importsAVersion2Backup() {
        val ledger = open()
        val backup = buildJsonObject {
            put("version", 2)
            put("exportedAt", "2026-10-01")
            put("data", v2LedgerJson())
        }
        assertIs<ImportResult.Success>(ledger.import(backup.toString()))
        assertEquals(
            listOf("Preset food", "Hobby", "Preset other", "Preset salary", "Preset other"),
            ledger.data.categoryNamesInOrder(),
        )
    }

    @Test
    fun categoriesAddedByTheUserAreLikePresets() {
        val ledger = open()
        val hobby = Category.Expense(CategoryId("c1"), "Hobby", pinned = false, sortOrder = 99, archived = false, ledger.foodFund)
        ledger.update { it.copy(categories = it.categories + hobby) }
        ledger.addTransaction(ledger.expense(1).copy(categoryId = hobby.id))
        assertEquals("Hobby", ledger.data.categoryName(ledger.data.transactions.single().categoryId))
        assertEquals("Preset food", ledger.data.categoryName(ledger.categoryId("Preset food")))
    }

    @Test
    fun categoryIdsAreUniqueAcrossKinds() {
        val ledger = open()
        val expenseOther = ledger.categoryId("Preset other", isExpense = true)
        val incomeOther = ledger.categoryId("Preset other", isExpense = false)
        assertTrue(expenseOther != incomeOther)
        assertFailsWith<IllegalArgumentException> {
            ledger.update { data ->
                data.copy(categories = data.categories.map { if (it.id == incomeOther) (it as Category.Income).copy(id = expenseOther) else it })
            }
        }
    }

    @Test
    fun rejectsAnIncomeWhoseAllocationsDoNotAddUp() {
        val ledger = open()
        val income = Transaction.Income(
            id = TransactionId(ulid(1)),
            date = LocalDate(2026, 10, 25),
            amount = 1000,
            categoryId = ledger.categoryId("Preset salary", isExpense = false),
            memo = "",
            budgetMonth = YearMonth(2026, 11),
            allocations = listOf(Allocation(ledger.foodFund, 900)),
        )
        assertFailsWith<IllegalArgumentException> { ledger.addTransaction(income) }
        assertTrue(ledger.data.transactions.isEmpty())
        ledger.addTransaction(income.copy(allocations = listOf(Allocation(ledger.foodFund, 1000))))
        assertEquals(1, ledger.data.transactions.size)
    }

    @Test
    fun rejectsAnExpenseFromAnUnknownFund() {
        val ledger = open()
        assertFailsWith<IllegalArgumentException> {
            ledger.addTransaction(ledger.expense(1).copy(fundId = FundId("nowhere")))
        }
    }

    @Test
    fun deletedTransactionsCanBeRestored() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        ledger.addTransaction(ledger.expense(2))
        val removed = ledger.deleteTransaction(TransactionId(ulid(1)))
        assertEquals(listOf(ledger.expense(2)), ledger.data.transactions)
        ledger.addTransaction(removed!!)
        assertEquals(setOf(ledger.expense(1), ledger.expense(2)), ledger.data.transactions.toSet())
        assertNull(ledger.deleteTransaction(TransactionId("missing")))
    }

    @Test
    fun exportedBackupsHaveVersion4AndRestoreIntoAnotherLedger() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1, "quote \" backslash \\ newline \n"))
        val backup = ledger.exportBackup()
        assertEquals(4, Json.parseToJsonElement(backup).jsonObject.getValue("version").jsonPrimitive.int)

        val other = open()
        assertIs<ImportResult.Success>(other.import(backup))
        assertEquals(ledger.data, other.data)
    }

    @Test
    fun importsAVersion3Backup() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        val backup = Json.parseToJsonElement(ledger.exportBackup()).jsonObject
        val version3 = JsonObject(backup - "unreadable" + ("version" to JsonPrimitive(3)))

        val other = open()
        assertIs<ImportResult.Success>(other.import(version3.toString()))
        assertEquals(ledger.data, other.data)
    }

    @Test
    fun backupsKeepTheRecordsThatCannotBeRead() {
        val files = InMemoryFiles()
        open(files).apply { addTransaction(expense(1)) }
        val file = transactionsFile(YearMonth(2026, 10))
        files.files[file] = files.files.getValue(file) + FUTURE_LINE + "\n"
        val ledger = open(files)
        val backup = ledger.exportBackup()

        val otherFiles = InMemoryFiles()
        val other = open(otherFiles)
        assertIs<ImportResult.Success>(other.import(backup))
        assertEquals(ledger.data, other.data)
        assertEquals(files.files, otherFiles.files)
        assertEquals(ledger.data, open(otherFiles).data)
        // A backup of the restored ledger keeps them again.
        assertTrue(FUTURE_LINE.replace("\"", "\\\"") in other.exportBackup())
    }

    @Test
    fun restoringABackupDropsTheRecordsThatCouldNotBeReadBefore() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val transport = ledger.data.funds.first().copy(id = FundId(ulid(50)), name = "Transport")
        ledger.update { it.copy(funds = it.funds + transport) }
        val backup = ledger.exportBackup()
        // Paid from Transport, though its category is paid from another fund by default.
        ledger.addTransaction(ledger.expense(1).copy(fundId = transport.id))
        deleteFundFromMaster(files, transport.id)
        val hiding = open(files)
        assertTrue(hiding.data.transactions.isEmpty())

        assertIs<ImportResult.Success>(hiding.import(backup))
        assertTrue(hiding.data.transactions.isEmpty())
        // The backup has Transport again, but not the expense hidden before restoring it.
        val reopened = open(files)
        assertTrue(reopened.data.transactions.isEmpty())
        assertEquals(hiding.data, reopened.data)
        assertTrue(files.files.values.none { ulid(1) in it })
    }

    @Test
    fun backupsWithRecordsThatCannotBeSavedAreRejected() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        val before = ledger.data
        val backup = Json.parseToJsonElement(ledger.exportBackup()).jsonObject
        listOf(
            """{"master": {"unknown_list": [{}]}}""",
            """{"transactions": {"transactions/../escape.jsonl": ["{}"]}}""",
            """{"transactions": {"elsewhere/2026-10.jsonl": ["{}"]}}""",
            """{"transactions": {"transactions/2026-10.jsonl": ["{}\n{}"]}}""",
            """{"transactions": {"transactions/2026-10.jsonl": [" "]}}""",
        ).forEach { unreadable ->
            val bad = JsonObject(backup + ("unreadable" to Json.parseToJsonElement(unreadable)))
            assertEquals(ImportResult.Failure, ledger.import(bad.toString()), unreadable)
        }
        assertEquals(before, ledger.data)
    }

    @Test
    fun identicalTransactionsStaySeparate() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        ledger.addTransaction(ledger.expense(2))
        val other = open()
        other.import(ledger.exportBackup())
        assertEquals(2, other.data.transactions.size)
        other.deleteTransaction(TransactionId(ulid(1)))
        assertEquals(listOf(ledger.expense(2)), other.data.transactions)
    }

    @Test
    fun importsAVersion1Backup() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        val backup = buildJsonObject {
            put("version", 1)
            put("exportedAt", "2026-09-30")
            put("transactions", buildJsonArray { add(v1ExpenseJson("t1", "Lunch")) })
            put("someFutureKey", true)
        }
        val result = ledger.import(backup.toString())
        assertIs<ImportResult.Success>(result)
        assertEquals(listOf("Lunch"), ledger.data.transactions.map { it.memo })
    }

    @Test
    fun invalidBackupsAreRejectedAndKeepTheData() {
        val ledger = open()
        ledger.addTransaction(ledger.expense(1))
        val before = ledger.data
        listOf("", "{}", "not json", """{"version": 99, "data": {}}""", """{"version": 2}""").forEach {
            assertEquals(ImportResult.Failure, ledger.import(it), it)
        }
        // Well-formed, but the expense refers to a fund that does not exist.
        val dangling = ledger.exportBackup().replace(
            "\"fundId\": \"${ledger.foodFund.value}\"",
            "\"fundId\": \"nowhere\"",
        )
        assertTrue("nowhere" in dangling)
        assertEquals(ImportResult.Failure, ledger.import(dangling))
        assertEquals(before, ledger.data)
    }

    @Test
    fun recurringItemsAreRecordedOncePerMonthWithoutAnAutoSuffix() {
        val ledger = open()
        val rent = RecurringItem(
            id = RecurringItemId("r1"),
            name = "Rent",
            amount = 80_000,
            categoryId = ledger.categoryId("Preset food"),
            fundId = ledger.foodFund,
            dayOfMonth = 27,
            enabled = true,
            lastAppliedMonth = null,
        )
        ledger.update { it.copy(recurringItems = listOf(rent, rent.copy(id = RecurringItemId("r2"), enabled = false))) }

        ledger.applyRecurringItems(LocalDate(2026, 10, 26))
        assertTrue(ledger.data.transactions.isEmpty())

        ledger.applyRecurringItems(LocalDate(2026, 10, 27))
        ledger.applyRecurringItems(LocalDate(2026, 10, 31))
        val expense = ledger.data.transactions.single() as Transaction.Expense
        assertEquals("Rent", expense.memo)
        assertEquals(LocalDate(2026, 10, 27), expense.date)
        assertEquals(RecurringItemId("r1"), expense.recurringItemId)
        assertEquals(ledger.foodFund, expense.fundId)
        assertEquals(YearMonth(2026, 10), ledger.data.recurringItems.first().lastAppliedMonth)

        ledger.applyRecurringItems(LocalDate(2026, 11, 28))
        assertEquals(2, ledger.data.transactions.size)
    }
}
