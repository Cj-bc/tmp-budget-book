package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun f(name: String) = FundId(name)

/** The design's rule for salary. */
private val SALARY_RULE = AllocationRule(
    incomeCategoryId = CategoryId("salary"),
    lines = listOf(
        RuleLine.Fixed(f("living"), 100_000),
        RuleLine.Percent(f("food"), 14),
        RuleLine.Fixed(f("fun"), 10_000),
        RuleLine.Fixed(f("daily"), 5_000),
        RuleLine.Fixed(f("transport"), 5_000),
        RuleLine.Fixed(f("utilities"), 12_000),
        RuleLine.Ask(f("transfer")),
    ),
    restFundId = f("savings"),
)

class AllocationCalculationTest {
    @Test
    fun theDesignsExampleLeaves83100ForSavings() {
        val preview = previewAllocation(SALARY_RULE, 285_000, asked = mapOf(6 to 30_000))
        assertEquals(39_900L, preview.lines[1].amount)
        assertEquals(83_100L, preview.rest)
        assertTrue(preview.isRecordable)
        val allocations = preview.allocations()
        assertEquals(285_000L, allocations.sumOf { it.amount })
        assertEquals(Allocation(f("savings"), 83_100), allocations.last())
    }

    @Test
    fun theDesignsShortageIs33000() {
        val preview = previewAllocation(SALARY_RULE, 150_000, asked = mapOf(6 to 30_000))
        assertEquals(33_000L, preview.shortage)
        assertFalse(preview.isRecordable)
    }

    @Test
    fun percentagesAreOfTheWholeIncomeRoundedDown() {
        val rule = AllocationRule(CategoryId("bonus"), listOf(RuleLine.Fixed(f("a"), 1_000), RuleLine.Percent(f("b"), 33)), f("c"))
        // 33% of 10,001 is 3,300.33; the fixed line is not subtracted first.
        assertEquals(3_300L, previewAllocation(rule, 10_001).lines[1].amount)
    }

    @Test
    fun withoutARuleEverythingGoesToTheChosenFund() {
        val preview = previewAllocation(null, 50_000, restFundId = f("food"))
        assertEquals(listOf(Allocation(f("food"), 50_000)), preview.allocations())
        assertFalse(previewAllocation(null, 50_000).isRecordable)
    }

    @Test
    fun anOverriddenRestFundWinsOverTheRules() {
        val preview = previewAllocation(SALARY_RULE, 300_000, restFundId = f("trip"))
        assertEquals(f("trip"), preview.allocations().last().fundId)
    }

    @Test
    fun zeroLinesAreNotStored() {
        val preview = previewAllocation(SALARY_RULE, 300_000)
        assertFalse(preview.allocations().any { it.fundId == f("transfer") })
    }

    @Test
    fun aFundsIncomeSourcesIncludeTheRest() {
        assertEquals(
            listOf(FundIncomeSource.Line(CategoryId("salary"), RuleLine.Fixed(f("fun"), 10_000))),
            fundIncomeSources(listOf(SALARY_RULE), f("fun")),
        )
        assertEquals(listOf(FundIncomeSource.Rest(CategoryId("salary"))), fundIncomeSources(listOf(SALARY_RULE), f("savings")))
    }

    @Test
    fun movementsListAllocationsInAndExpensesOutLatestFirst() {
        val income = Transaction.Income(
            TransactionId("i"), LocalDate(2026, 9, 25), 30_000, CategoryId("salary"), "", YearMonth(2026, 10),
            listOf(Allocation(f("trip"), 20_000), Allocation(f("food"), 10_000)),
        )
        val expense = Transaction.Expense(TransactionId("e"), LocalDate(2026, 10, 2), 3_000, CategoryId("food"), "", f("trip"), null)
        val other = expense.copy(id = TransactionId("o"), fundId = f("food"))
        assertEquals(
            listOf(-3_000L, 20_000L),
            fundMovements(listOf(income, expense, other), f("trip")).map { it.amount },
        )
        assertEquals(listOf(income), incomesFor(listOf(income, expense), YearMonth(2026, 10)))
    }
}
