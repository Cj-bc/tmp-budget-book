package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

private val FOOD = FundId("food")
private val FUN = FundId("fun")

private fun expense(id: String, date: String, amount: Long, category: String = "food", fund: FundId = FOOD, memo: String = "") =
    Transaction.Expense(TransactionId(id), LocalDate.parse(date), amount, CategoryId(category), memo, fund, null)

private fun income(id: String, date: String, amount: Long) = Transaction.Income(
    TransactionId(id), LocalDate.parse(date), amount, CategoryId("salary"), "", YearMonth(2026, 11),
    listOf(Allocation(FOOD, amount)),
)

class AnalyticsTest {
    private val transactions = listOf(
        income("i1", "2026-09-25", 300_000),
        expense("e1", "2026-09-30", 1_000),
        expense("e2", "2026-10-02", 2_000, memo = "Cafe", fund = FUN),
        expense("e3", "2026-10-05", 3_000, category = "fun"),
        expense("e4", "2026-10-12", 500, memo = "cafe "),
        expense("e5", "2026-10-22", 600, memo = "Cafe", fund = FUN),
    )

    @Test
    fun monthsEndWithTheGivenOneAcrossTheYear() {
        assertEquals(
            listOf(YearMonth(2025, 11), YearMonth(2025, 12), YearMonth(2026, 1)),
            monthsEndingWith(YearMonth(2026, 1), 3),
        )
        assertEquals(12, monthsEndingWith(YearMonth(2026, 10)).size)
    }

    @Test
    fun monthTotalsAndCumulativeBalances() {
        val months = listOf(YearMonth(2026, 9), YearMonth(2026, 10))
        assertEquals(
            listOf(MonthTotals(YearMonth(2026, 9), 300_000, 1_000), MonthTotals(YearMonth(2026, 10), 0, 6_100)),
            monthTotals(transactions, months),
        )
        assertEquals(listOf(299_000L, 292_900L), cumulativeBalances(transactions, months))
    }

    @Test
    fun spendingSplitsByCategoryAndByFund() {
        assertEquals(
            listOf(Share(CategoryId("fun"), 3_000L), Share(CategoryId("food"), 500L)),
            fundSpendingByCategory(transactions, FOOD, YearMonth(2026, 10)),
        )
        assertEquals(
            listOf(Share(FUN, 2_600L), Share(FOOD, 500L)),
            categorySpendingByFund(transactions, CategoryId("food"), YearMonth(2026, 10)),
        )
    }

    @Test
    fun patternsGroupMemosIgnoringCaseAndSpaces() {
        val pattern = purchasePatterns(transactions).single()
        assertEquals(3, pattern.count)
        // 10/2 → 10/12 → 10/22
        assertEquals(10, pattern.intervalDays)
        assertEquals(1_033L, pattern.averageAmount)
        // Two of the three were paid from FUN.
        assertEquals(FUN, pattern.fundId)
    }
}
