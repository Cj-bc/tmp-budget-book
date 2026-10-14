package jp.cjbc.kakeibo.model

import kotlin.test.Test
import kotlin.test.assertEquals

private val FOOD = FundId("food")
private val TRIP = FundId("trip")

private fun fund(id: FundId, openingBalance: Long = 0, goal: FundGoal = FundGoal.None) = Fund(
    id = id,
    name = id.value,
    color = FundColor(0xFF5A616D),
    openingBalance = openingBalance,
    goal = goal,
    showInEntry = defaultShowInEntry(goal),
    sortOrder = 0,
    archived = false,
)

private fun expense(date: String, amount: Long, fundId: FundId = FOOD) = Transaction.Expense(
    id = TransactionId("e-$date-$amount"),
    date = LocalDate.parse(date),
    amount = amount,
    categoryId = CategoryId("food"),
    memo = "",
    fundId = fundId,
    recurringItemId = null,
)

private fun income(date: String, budgetMonth: String, vararg allocations: Pair<FundId, Long>) =
    Transaction.Income(
        id = TransactionId("i-$date"),
        date = LocalDate.parse(date),
        amount = allocations.sumOf { it.second },
        categoryId = CategoryId("salary"),
        memo = "",
        budgetMonth = YearMonth.parse(budgetMonth),
        allocations = allocations.map { Allocation(it.first, it.second) },
    )

class FundCalculationsTest {
    // Salary on 9/25 funds October, salary on 10/25 funds November.
    private val transactions = listOf(
        income("2026-09-25", "2026-10", FOOD to 40_000, TRIP to 20_000),
        income("2026-10-25", "2026-11", FOOD to 40_000, TRIP to 20_000),
        expense("2026-09-30", 5_000),
        expense("2026-10-03", 12_000),
        expense("2026-10-20", 30_000),
        expense("2026-11-02", 1_000),
    )

    @Test
    fun balanceCountsAllocationsByBudgetMonthAndExpensesByDate() {
        val food = fund(FOOD, openingBalance = 1_000)
        // Before October: only the September expense.
        assertEquals(1_000 - 5_000L, balance(food, transactions, LocalDate(2026, 9, 30)))
        // October's allocation is usable from October 1, though received in September.
        assertEquals(1_000 + 40_000 - 5_000 - 12_000L, balance(food, transactions, LocalDate(2026, 10, 3)))
        // The 10/25 salary is November's money, so it does not count on 10/31.
        assertEquals(1_000 + 40_000 - 47_000L, balance(food, transactions, LocalDate(2026, 10, 31)))
    }

    @Test
    fun balanceMayBeNegative() {
        val food = fund(FOOD, openingBalance = -10_000)
        assertEquals(-10_000 + 40_000 - 47_000L, balance(food, transactions, LocalDate(2026, 10, 31)))
    }

    @Test
    fun availableIsTheBalanceAtTheMonthStartPlusTheMonthsAllocations() {
        val food = fund(FOOD, openingBalance = 1_000)
        assertEquals(1_000 - 5_000 + 40_000L, available(food, transactions, YearMonth(2026, 10)))
        // The surplus or shortfall of October carries over to November.
        assertEquals(1_000 - 5_000 + 40_000 - 42_000 + 40_000L, available(food, transactions, YearMonth(2026, 11)))
    }

    @Test
    fun endBalanceIsTheBalanceOnTheLastDay() {
        val food = fund(FOOD)
        assertEquals(40_000 - 47_000L, endBalance(food, transactions, YearMonth(2026, 10)))
        assertEquals(80_000 - 48_000L, endBalance(food, transactions, YearMonth(2026, 11)))
    }

    @Test
    fun fundsOnlyCountTheirOwnMoney() {
        val trip = fund(TRIP)
        assertEquals(20_000L, balance(trip, transactions, LocalDate(2026, 10, 31)))
    }

    private val goal = FundGoal.Target(240_000, YearMonth(2026, 4), YearMonth(2027, 3))

    @Test
    fun plannedFollowsTheExampleOfTheSpecification() {
        // 2026-04 to 2027-03 is 12 months; October is the 7th: 240,000 × 7 / 12.
        assertEquals(140_000L, planned(goal, YearMonth(2026, 10)))
    }

    @Test
    fun plannedIsZeroBeforeTheStartAndTheWholeAmountAfterTheDueMonth() {
        assertEquals(0L, planned(goal, YearMonth(2026, 3)))
        assertEquals(20_000L, planned(goal, YearMonth(2026, 4)))
        assertEquals(240_000L, planned(goal, YearMonth(2027, 3)))
        assertEquals(240_000L, planned(goal, YearMonth(2028, 1)))
    }

    @Test
    fun planDifferenceIsNegativeWhenBehind() {
        assertEquals(-40_000L, planDifference(goal, 100_000, YearMonth(2026, 10)))
        assertEquals(10_000L, planDifference(goal, 150_000, YearMonth(2026, 10)))
    }

    @Test
    fun monthlyRequirementSpreadsTheShortfallOverTheMonthsAfterTheCurrentOne() {
        // From November through March: 5 months for 240,000 − 100,000.
        assertEquals(
            MonthlyRequirement.PerMonth(28_000, 5),
            monthlyRequirement(goal, 100_000, YearMonth(2026, 10)),
        )
        // Rounded up, so that it is enough.
        assertEquals(
            MonthlyRequirement.PerMonth(28_001, 5),
            monthlyRequirement(goal, 99_996, YearMonth(2026, 10)),
        )
    }

    @Test
    fun monthlyRequirementReportsReachedAndOverdueGoals() {
        assertEquals(MonthlyRequirement.Reached, monthlyRequirement(goal, 240_000, YearMonth(2026, 10)))
        assertEquals(MonthlyRequirement.Overdue(40_000), monthlyRequirement(goal, 200_000, YearMonth(2027, 3)))
    }
}
