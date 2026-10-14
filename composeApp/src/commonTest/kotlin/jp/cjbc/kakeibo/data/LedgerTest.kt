package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.v1.migratedFundId
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LocalDate
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.YearMonth
import jp.cjbc.kakeibo.runSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FOOD = CategoryId("food")
private val FOOD_FUND = migratedFundId(FOOD)

private fun open(storage: InMemoryStorage = InMemoryStorage()): Ledger =
    runSuspend { Ledger.open(storage) { "Fund ${it.value}" } }

private fun expense(id: String, memo: String = "", amount: Long = 500) = Transaction.Expense(
    id = TransactionId(id),
    date = LocalDate(2026, 10, 1),
    amount = amount,
    categoryId = FOOD,
    memo = memo,
    fundId = FOOD_FUND,
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

private fun Ledger.import(raw: String): ImportResult = runSuspend { importBackup(raw) { "Fund ${it.value}" } }

class LedgerTest {
    @Test
    fun aNewLedgerHasTheDefaultCategoriesEachWithAFund() {
        val ledger = open()
        assertTrue(ledger.data.categories.isNotEmpty())
        assertEquals("Fund food", ledger.data.funds.single { it.id == FOOD_FUND }.name)
        assertTrue(ledger.data.transactions.isEmpty())
    }

    @Test
    fun dataSurvivesReopening() {
        val storage = InMemoryStorage()
        open(storage).addTransaction(expense("t1", "Lunch"))
        assertEquals(listOf(expense("t1", "Lunch")), open(storage).data.transactions)
    }

    @Test
    fun migratesDataSavedByVersion1() {
        val storage = InMemoryStorage()
        storage.values["kakeibo:v1:transactions"] =
            buildJsonArray { add(v1ExpenseJson("t1", "Lunch")) }.toString()
        storage.values["kakeibo:v1:budgets"] = "not json"

        val ledger = open(storage)

        val expense = ledger.data.transactions.single() as Transaction.Expense
        assertEquals("Lunch", expense.memo)
        assertEquals(FOOD_FUND, expense.fundId)
        assertTrue("kakeibo:v2:ledger" in storage.values)
        // Reopening reads the migrated data instead of migrating again.
        storage.values.remove("kakeibo:v1:transactions")
        assertEquals(ledger.data, open(storage).data)
    }

    @Test
    fun rejectsAnIncomeWhoseAllocationsDoNotAddUp() {
        val ledger = open()
        val income = Transaction.Income(
            id = TransactionId("i1"),
            date = LocalDate(2026, 10, 25),
            amount = 1000,
            categoryId = CategoryId("salary"),
            memo = "",
            budgetMonth = YearMonth(2026, 11),
            allocations = listOf(Allocation(FOOD_FUND, 900)),
        )
        assertFailsWith<IllegalArgumentException> { ledger.addTransaction(income) }
        assertTrue(ledger.data.transactions.isEmpty())
        ledger.addTransaction(income.copy(allocations = listOf(Allocation(FOOD_FUND, 1000))))
        assertEquals(1, ledger.data.transactions.size)
    }

    @Test
    fun rejectsAnExpenseFromAnUnknownFund() {
        val ledger = open()
        assertFailsWith<IllegalArgumentException> {
            ledger.addTransaction(expense("t1").copy(fundId = FundId("nowhere")))
        }
    }

    @Test
    fun deletedTransactionsCanBeRestored() {
        val ledger = open()
        ledger.addTransaction(expense("t1"))
        ledger.addTransaction(expense("t2"))
        val removed = ledger.deleteTransaction(TransactionId("t1"))
        assertEquals(listOf(expense("t2")), ledger.data.transactions)
        ledger.addTransaction(removed!!)
        assertEquals(setOf(expense("t1"), expense("t2")), ledger.data.transactions.toSet())
        assertNull(ledger.deleteTransaction(TransactionId("missing")))
    }

    @Test
    fun exportedBackupsHaveVersion2AndRestoreIntoAnotherLedger() {
        val ledger = open()
        ledger.addTransaction(expense("t1", "quote \" backslash \\ newline \n"))
        val backup = ledger.exportBackup()
        assertEquals(2, Json.parseToJsonElement(backup).jsonObject.getValue("version").jsonPrimitive.int)

        val other = open()
        assertIs<ImportResult.Success>(other.import(backup))
        assertEquals(ledger.data, other.data)
    }

    @Test
    fun identicalTransactionsStaySeparate() {
        val ledger = open()
        ledger.addTransaction(expense("t1"))
        ledger.addTransaction(expense("t2"))
        val other = open()
        other.import(ledger.exportBackup())
        assertEquals(2, other.data.transactions.size)
        other.deleteTransaction(TransactionId("t1"))
        assertEquals(listOf(expense("t2")), other.data.transactions)
    }

    @Test
    fun importsAVersion1Backup() {
        val ledger = open()
        ledger.addTransaction(expense("existing"))
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
        ledger.addTransaction(expense("t1"))
        val before = ledger.data
        listOf("", "{}", "not json", """{"version": 99, "data": {}}""", """{"version": 2}""").forEach {
            assertEquals(ImportResult.Failure, ledger.import(it), it)
        }
        // Well-formed, but the expense refers to a fund that does not exist.
        val dangling = ledger.exportBackup().replace(
            "\"fundId\": \"${FOOD_FUND.value}\"",
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
            categoryId = FOOD,
            fundId = FOOD_FUND,
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
        assertEquals(FOOD_FUND, expense.fundId)
        assertEquals(YearMonth(2026, 10), ledger.data.recurringItems.first().lastAppliedMonth)

        ledger.applyRecurringItems(LocalDate(2026, 11, 28))
        assertEquals(2, ledger.data.transactions.size)
    }
}
