package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

private val FOOD_FUND = FundId("food")
private val FUN_FUND = FundId("fun")

private fun expense(id: String, date: String, category: String, memo: String = "", fund: FundId = FOOD_FUND, amount: Long = 100) =
    Transaction.Expense(TransactionId(id), LocalDate.parse(date), amount, CategoryId(category), memo, fund, null)

private fun income(id: String, date: String, category: String, funds: List<FundId>) = Transaction.Income(
    TransactionId(id), LocalDate.parse(date), 100L * funds.size, CategoryId(category), "",
    YearMonth(2026, 11), funds.map { Allocation(it, 100) },
)

class HistoryFilterTest {
    private val transactions = listOf(
        expense("cafe", "2026-10-09", "food", memo = "Cafe", fund = FUN_FUND),
        expense("lunch", "2026-10-09", "food", memo = "Lunch"),
        expense("old", "2026-09-30", "other"),
        income("salary", "2026-10-05", "salary", listOf(FOOD_FUND, FUN_FUND)),
        income("other-income", "2026-10-06", "income-other", listOf(FOOD_FUND)),
    )

    private fun ids(filter: HistoryFilter) =
        transactions.filter { filter.matches(it) { id -> if (id.value == "food") "食費" else id.value } }.map { it.id.value }

    @Test
    fun noFilterShowsEverything() {
        assertEquals(transactions.map { it.id.value }, ids(HistoryFilter()))
    }

    @Test
    fun filtersByMonth() {
        assertEquals(listOf("old"), ids(HistoryFilter(month = YearMonth(2026, 9))))
    }

    @Test
    fun filtersByFundAcrossExpensesAndAllocations() {
        assertEquals(listOf("cafe", "salary"), ids(HistoryFilter(fundId = FUN_FUND)))
    }

    @Test
    fun filtersByCategory() {
        assertEquals(listOf("old"), ids(HistoryFilter(category = CategoryId("other"))))
        assertEquals(listOf("other-income"), ids(HistoryFilter(category = CategoryId("income-other"))))
    }

    @Test
    fun filtersIncomesOnly() {
        assertEquals(listOf("salary", "other-income"), ids(HistoryFilter(incomeOnly = true)))
    }

    @Test
    fun searchesMemoAndCategoryNameIgnoringCase() {
        assertEquals(listOf("cafe"), ids(HistoryFilter(query = "  cafe ")))
        assertEquals(listOf("cafe", "lunch"), ids(HistoryFilter(query = "食費")))
    }

    @Test
    fun conditionsCombine() {
        assertEquals(listOf("lunch"), ids(HistoryFilter(fundId = FOOD_FUND, category = CategoryId("food"))))
    }

    @Test
    fun daysAreLatestFirstWithTheirNet() {
        val days = groupByDay(transactions)
        assertEquals(listOf("2026-10-09", "2026-10-06", "2026-10-05", "2026-09-30"), days.map { it.date.toString() })
        assertEquals(-200L, days.first().net)
        assertEquals(listOf("cafe", "lunch"), days.first().transactions.map { it.id.value })
    }
}
