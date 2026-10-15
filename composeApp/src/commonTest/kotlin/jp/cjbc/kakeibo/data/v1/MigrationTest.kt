package jp.cjbc.kakeibo.data.v1

import jp.cjbc.kakeibo.model.BUILT_IN_EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.BuiltInCategory
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.DEFAULT_FUND_COLOR
import jp.cjbc.kakeibo.model.FundColor
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.problems
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private fun migrate(backup: V1Backup): MigrationResult =
    runSuspend { migrateFromV1(backup) { "Fund ${it.value}" } }

private fun v1Expense(id: String, category: String, amount: Double = 1000.0, description: String = "") =
    V1Transaction(id, "2026-09-10", amount, V1TransactionType.EXPENSE, category, description)

class MigrationTest {
    private val food = CategoryId("food")

    @Test
    fun everyExpenseCategoryGetsAFundOfItsNameAsItsDefault() {
        val result = migrate(V1Backup(version = 1, budgets = listOf(V1Budget("b1", "food", 30000.0))))
        val expenseCategories = result.data.categories.filterIsInstance<Category.Expense>()

        // Categories with and without a budget alike.
        assertEquals(BUILT_IN_EXPENSE_CATEGORIES.map { it.id }, expenseCategories.map { it.id })
        expenseCategories.forEach { category ->
            val fund = result.data.funds.single { it.id == category.defaultFundId }
            assertEquals("Fund ${category.id.value}", fund.name)
            assertEquals(0L, fund.openingBalance)
            assertEquals(FundGoal.None, fund.goal)
            assertTrue(fund.showInEntry)
        }
    }

    @Test
    fun fundsTakeTheColorOfTheirCategory() {
        val result = migrate(
            V1Backup(version = 1, settings = V1Settings(expenseCategories = listOf("food", "Pets"))),
        )
        val colors = result.data.funds.associate { it.name to it.color }
        assertEquals(FundColor(BuiltInCategory.FOOD.legacyColor), colors["Fund food"])
        assertEquals(FundColor(DEFAULT_FUND_COLOR), colors["Fund Pets"])
    }

    @Test
    fun pastExpensesArePaidFromTheirCategorysFund() {
        val result = migrate(
            V1Backup(version = 1, transactions = listOf(v1Expense("t1", "food", 1234.0, "Lunch"))),
        )
        val expense = result.data.transactions.single() as Transaction.Expense
        assertEquals(migratedFundId(food), expense.fundId)
        assertEquals(1234L, expense.amount)
        assertEquals("Lunch", expense.memo)
        assertEquals(LocalDate(2026, 9, 10), expense.date)
        assertTrue(result.data.problems().isEmpty())
    }

    @Test
    fun categoriesRemovedFromTheSettingsSurviveArchivedWhileUsed() {
        val result = migrate(
            V1Backup(
                version = 1,
                transactions = listOf(v1Expense("t1", "Old hobby")),
                settings = V1Settings(expenseCategories = listOf("food"), incomeCategories = listOf("salary")),
            ),
        )
        val old = result.data.categories.single { it.id == CategoryId("Old hobby") } as Category.Expense
        assertTrue(old.archived)
        assertTrue(result.data.funds.any { it.id == old.defaultFundId })
        assertTrue(result.data.problems().isEmpty())
    }

    @Test
    fun incomesSavingsAndRecurringIncomesAreDroppedWithALog() {
        val result = migrate(
            V1Backup(
                version = 1,
                transactions = listOf(
                    V1Transaction("i1", "2026-09-25", 300000.0, V1TransactionType.INCOME, "salary"),
                    v1Expense("t1", "food"),
                ),
                savings = listOf(kotlinx.serialization.json.JsonPrimitive("saving")),
                recurringItems = listOf(
                    V1RecurringItem("r1", "給与", 300000.0, V1TransactionType.INCOME, "salary", 25),
                ),
            ),
        )
        assertEquals(1, result.data.transactions.size)
        assertTrue(result.data.recurringItems.isEmpty())
        assertEquals(3, result.log.size)
    }

    @Test
    fun recurringExpensesArePaidFromTheirCategorysFund() {
        val result = migrate(
            V1Backup(
                version = 1,
                recurringItems = listOf(
                    V1RecurringItem("r1", "Rent", 80000.0, V1TransactionType.EXPENSE, "housing", 27, true, "2026-09"),
                ),
            ),
        )
        val item = result.data.recurringItems.single()
        assertEquals(migratedFundId(CategoryId("housing")), item.fundId)
        assertEquals(80000L, item.amount)
        assertEquals("Rent", item.name)
        assertEquals(YearMonth(2026, 9), item.lastAppliedMonth)
        assertTrue(item.enabled)
    }

    @Test
    fun recordsOfRecurringItemsReferToTheirItem() {
        val result = migrate(
            V1Backup(
                version = 1,
                transactions = listOf(
                    v1Expense("t1", "housing", description = "Rent（自動）").copy(isRecurring = true),
                ),
                recurringItems = listOf(
                    V1RecurringItem("r1", "Rent", 80000.0, V1TransactionType.EXPENSE, "housing", 27),
                ),
            ),
        )
        val expense = result.data.transactions.single() as Transaction.Expense
        assertEquals(RecurringItemId("r1"), expense.recurringItemId)
        // Existing memos are left as they are.
        assertEquals("Rent（自動）", expense.memo)
    }

    @Test
    fun theMostUsedSevenExpenseCategoriesArePinned() {
        val usage = mapOf("other" to 5, "communication" to 4, "education" to 4, "daily_goods" to 3, "clothing" to 3,
            "entertainment" to 2, "medical" to 2, "utilities" to 1)
        val transactions = usage.flatMap { (category, count) ->
            (1..count).map { v1Expense("$category-$it", category) }
        }
        val result = migrate(V1Backup(version = 1, transactions = transactions))
        val pinned = result.data.categories.filterIsInstance<Category.Expense>().filter { it.pinned }.map { it.id.value }
        assertEquals(
            setOf("other", "communication", "education", "daily_goods", "clothing", "entertainment", "medical"),
            pinned.toSet(),
        )
    }

    @Test
    fun zeroAmountsAndInvalidDatesAreDropped() {
        val result = migrate(
            V1Backup(
                version = 1,
                transactions = listOf(v1Expense("t1", "food", 0.2), v1Expense("t2", "food").copy(date = "2026-02-30")),
            ),
        )
        assertTrue(result.data.transactions.isEmpty())
        assertEquals(2, result.log.size)
    }
}
