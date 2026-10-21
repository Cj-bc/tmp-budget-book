package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.AllocationRule
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundColor
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.runSuspend
import jp.cjbc.kakeibo.util.isUlid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val OCTOBER = "transactions/2026-10.jsonl"
private const val SEPTEMBER = "transactions/2026-09.jsonl"

private fun open(files: InMemoryFiles, legacy: InMemoryStorage = InMemoryStorage()): Ledger =
    runSuspend { Ledger.open(files, legacy) { "Preset ${it.legacyId}" } }

private fun ulid(n: Int): String = "01ARZ3NDEKTSV4RRFFQ69G5F" + n.toString().padStart(2, '0')

private val Ledger.foodFund: FundId get() = data.funds.single { it.name == "Preset food" }.id

private val Ledger.food: CategoryId get() = checkNotNull(data.categoryNamed("Preset food", true)).id

private fun Ledger.expense(n: Int, date: LocalDate, memo: String = "") = Transaction.Expense(
    id = TransactionId(ulid(n)),
    date = date,
    amount = 500,
    categoryId = food,
    memo = memo,
    fundId = foodFund,
    recurringItemId = null,
)

private fun InMemoryFiles.lines(path: String): List<String> = files.getValue(path).lines().dropLast(1)

private fun keys(json: String): List<String> = Json.parseToJsonElement(json).jsonObject.keys.toList()

class LedgerFilesTest {
    @Test
    fun writesEachTransactionAsALineInTheFileOfItsMonthSortedByDateAndId() {
        val files = InMemoryFiles()
        val ledger = open(files)
        ledger.addTransaction(ledger.expense(3, LocalDate(2026, 10, 19)))
        ledger.addTransaction(ledger.expense(2, LocalDate(2026, 10, 1), "Lunch"))
        ledger.addTransaction(ledger.expense(1, LocalDate(2026, 10, 19)))
        ledger.addTransaction(ledger.expense(4, LocalDate(2026, 9, 30)))

        assertEquals(setOf(MASTER_FILE, SEPTEMBER, OCTOBER), files.files.keys)
        assertTrue(files.files.getValue(OCTOBER).endsWith("}\n"))
        val october = files.lines(OCTOBER)
        assertEquals(
            """{"v":1,"type":"expense","id":"${ulid(2)}","transaction_date":"2026-10-01","amount":500,""" +
                """"category_id":"${ledger.food.value}","memo":"Lunch","fund_id":"${ledger.foodFund.value}",""" +
                """"recurring_item_id":null}""",
            october[0],
        )
        assertEquals(listOf(ulid(2), ulid(1), ulid(3)), october.map { Json.parseToJsonElement(it).jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals(1, files.lines(SEPTEMBER).size)
    }

    @Test
    fun writesIncomesWithTheirBudgetMonthAndAllocations() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val salary = checkNotNull(ledger.data.categoryNamed("Preset salary", false)).id
        ledger.addTransaction(
            Transaction.Income(
                id = TransactionId(ulid(1)),
                date = LocalDate(2026, 10, 25),
                amount = 1000,
                categoryId = salary,
                memo = "",
                budgetMonth = YearMonth(2026, 11),
                allocations = listOf(Allocation(ledger.foodFund, 1000)),
            ),
        )
        val line = files.lines(OCTOBER).single()
        assertEquals(
            listOf("v", "type", "id", "transaction_date", "amount", "category_id", "memo", "budget_month", "allocations"),
            keys(line),
        )
        assertTrue(""""budget_month":"2026-11","allocations":[{"fund_id":"${ledger.foodFund.value}","amount":1000}]""" in line)
    }

    @Test
    fun masterIsIndentedJsonOfEverythingButTheTransactionsSortedById() {
        val files = InMemoryFiles()
        val ledger = open(files)
        val rule = AllocationRule(
            incomeCategoryId = checkNotNull(ledger.data.categoryNamed("Preset salary", false)).id,
            lines = listOf(RuleLine.Percent(ledger.foodFund, 10)),
            restFundId = ledger.foodFund,
        )
        ledger.update { it.copy(allocationRules = listOf(rule)) }

        val master = files.files.getValue(MASTER_FILE)
        assertTrue(master.startsWith("{\n  \"version\": 1,\n  \"funds\": [\n    {\n"), master)
        assertTrue(master.endsWith("}\n"))
        assertEquals(
            listOf("version", "funds", "expense_categories", "income_categories", "allocation_rules", "recurring_items"),
            keys(master),
        )
        val json = Json.parseToJsonElement(master).jsonObject
        val fundIds = json.getValue("funds").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(fundIds.sorted(), fundIds)
        assertEquals(
            listOf("id", "name", "color", "opening_balance", "goal", "show_in_entry", "sort_order", "archived"),
            json.getValue("funds").jsonArray.first().jsonObject.keys.toList(),
        )
        assertEquals(
            listOf("id", "name", "pinned", "sort_order", "archived", "default_fund_id"),
            json.getValue("expense_categories").jsonArray.first().jsonObject.keys.toList(),
        )
        val savedRule = json.getValue("allocation_rules").jsonArray.single().jsonObject
        assertEquals(listOf("id", "income_category_id", "lines", "rest_fund_id"), savedRule.keys.toList())
        assertEquals(rule.id.value, savedRule.getValue("id").jsonPrimitive.content)
        assertEquals(ledger.data, open(files).data)
    }

    @Test
    fun deletingTheLastTransactionOfAMonthDeletesItsFile() {
        val files = InMemoryFiles()
        val ledger = open(files)
        ledger.addTransaction(ledger.expense(1, LocalDate(2026, 10, 1)))
        ledger.deleteTransaction(TransactionId(ulid(1)))
        assertFalse(OCTOBER in files.files)
    }

    @Test
    fun writesOnlyTheFilesThatChanged() {
        val files = InMemoryFiles()
        val ledger = open(files)
        ledger.addTransaction(ledger.expense(1, LocalDate(2026, 10, 1)))
        files.writes.clear()
        ledger.addTransaction(ledger.expense(2, LocalDate(2026, 9, 1)))
        assertEquals(listOf(SEPTEMBER), files.writes)

        files.writes.clear()
        open(files).addTransaction(ledger.expense(3, LocalDate(2026, 9, 2)))
        assertEquals(listOf(SEPTEMBER), files.writes)
    }

    @Test
    fun skipsTransactionsItCannotReadAndKeepsThem() {
        val files = InMemoryFiles()
        val ledger = open(files)
        ledger.addTransaction(ledger.expense(1, LocalDate(2026, 10, 1)))
        val newerVersion = buildJsonObject {
            put("v", 2)
            put("type", "expense")
            put("id", ulid(5))
            put("transaction_date", "2026-10-05")
        }.toString()
        val unknownFund = Json.parseToJsonElement(files.lines(OCTOBER).single()).jsonObject.let {
            JsonObject(it + mapOf("id" to JsonPrimitive(ulid(9)), "fund_id" to JsonPrimitive("nowhere")))
        }.toString()
        files.files[OCTOBER] = files.files.getValue(OCTOBER) + "not json\n$newerVersion\n$unknownFund\n"

        val reopened = open(files)
        assertEquals(ledger.data, reopened.data)

        reopened.addTransaction(reopened.expense(7, LocalDate(2026, 10, 20)))
        val lines = files.lines(OCTOBER)
        assertEquals(5, lines.size)
        // Sorted by date and ID, with the line that has neither at the end.
        assertEquals(unknownFund, lines[1])
        assertEquals(newerVersion, lines[2])
        assertEquals("not json", lines[4])
        assertEquals(2, open(files).data.transactions.size)
    }

    @Test
    fun skipsMasterRecordsItCannotReadAndKeepsThem() {
        val files = InMemoryFiles()
        open(files)
        val master = Json.parseToJsonElement(files.files.getValue(MASTER_FILE)).jsonObject
        val categories = master.getValue("expense_categories").jsonArray
        val dangling = JsonObject(
            categories.first().jsonObject +
                mapOf("id" to JsonPrimitive("ZZZ"), "default_fund_id" to JsonPrimitive("nowhere")),
        )
        files.files[MASTER_FILE] = JsonObject(
            master + ("expense_categories" to JsonArray(categories + dangling + JsonPrimitive("garbage"))),
        ).toString()

        val ledger = open(files)
        assertTrue(ledger.data.categories.none { it.id.value == "ZZZ" })
        ledger.addTransaction(ledger.expense(1, LocalDate(2026, 10, 1)))

        val saved = Json.parseToJsonElement(files.files.getValue(MASTER_FILE)).jsonObject
            .getValue("expense_categories").jsonArray
        assertEquals(dangling, saved[saved.size - 2])
        assertEquals(JsonPrimitive("garbage"), saved.last())
    }

    @Test
    fun keepsAMasterOfAnUnknownVersionAsideAndTheTransactionsAsTheyAre() {
        val files = InMemoryFiles()
        open(files).run { addTransaction(expense(1, LocalDate(2026, 10, 1))) }
        val newer = files.files.getValue(MASTER_FILE).replace("\"version\": 1", "\"version\": 2")
        files.files[MASTER_FILE] = newer
        val october = files.files.getValue(OCTOBER)

        val ledger = open(files)

        assertEquals(newer, files.files["$MASTER_FILE.unreadable"])
        assertTrue(files.files.getValue(MASTER_FILE).startsWith("{\n  \"version\": 1,"))
        assertTrue(ledger.data.transactions.isEmpty())
        assertEquals(october, files.files[OCTOBER])
    }

    @Test
    fun migrationFromTheKeyValueStorageGivesEveryIdAUlid() {
        val legacy = InMemoryStorage()
        val food = CategoryId("food")
        val salary = CategoryId("salary")
        val fund = FundId("fund-food")
        val old = LedgerData(
            funds = listOf(
                Fund(fund, "Food", FundColor(0xFF5A616D), 0, FundGoal.None, showInEntry = true, sortOrder = 0, archived = false),
            ),
            categories = listOf(
                Category.Expense(food, "Food", pinned = true, sortOrder = 0, archived = false, defaultFundId = fund),
                Category.Income(salary, "Salary", pinned = false, sortOrder = 0, archived = false),
            ),
            transactions = listOf(
                Transaction.Expense(TransactionId("20261001-123456"), LocalDate(2026, 10, 1), 500, food, "", fund, RecurringItemId("r1")),
            ),
            allocationRules = listOf(AllocationRule(salary, listOf(RuleLine.Ask(fund)), fund)),
            recurringItems = listOf(
                RecurringItem(RecurringItemId("r1"), "Rent", 80_000, food, fund, 1, enabled = true, lastAppliedMonth = null),
            ),
        )
        // Version 3 saved rules without IDs.
        val json = Json.encodeToJsonElement(LedgerData.serializer(), old).jsonObject.let { data ->
            val rules = data.getValue("allocationRules").jsonArray.map { JsonObject(it.jsonObject - "id") }
            JsonObject(data + ("allocationRules" to JsonArray(rules)))
        }
        legacy.values["kakeibo:v3:ledger"] = json.toString()

        val data = open(InMemoryFiles(), legacy).data

        val newFund = data.funds.single().id
        val newFood = data.categories.first().id
        val expense = data.transactions.single() as Transaction.Expense
        val item = data.recurringItems.single()
        val rule = data.allocationRules.single()
        listOf(newFund.value, newFood.value, data.categories.last().id.value, expense.id.value, item.id.value, rule.id.value)
            .forEach { assertTrue(isUlid(it), it) }
        assertEquals(newFund, (data.categories.first() as Category.Expense).defaultFundId)
        assertEquals(Transaction.Expense(expense.id, LocalDate(2026, 10, 1), 500, newFood, "", newFund, item.id), expense)
        assertEquals(listOf(newFood, newFund), listOf(item.categoryId, item.fundId))
        assertEquals(AllocationRule(data.categories.last().id, listOf(RuleLine.Ask(newFund)), newFund, rule.id), rule)
    }
}
