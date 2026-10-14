package jp.cjbc.kakeibo.model

// Values derived from funds and transactions. None of them is stored.

/** The sum allocated to [fundId] by incomes whose budget month satisfies [isIncluded]. */
private fun List<Transaction>.allocatedTo(fundId: FundId, isIncluded: (YearMonth) -> Boolean): Long =
    filterIsInstance<Transaction.Income>()
        .filter { isIncluded(it.budgetMonth) }
        .sumOf { income -> income.allocations.filter { it.fundId == fundId }.sumOf { it.amount } }

/** The sum of expenses paid from [fundId] on dates satisfying [isIncluded]. */
private fun List<Transaction>.spentFrom(fundId: FundId, isIncluded: (LocalDate) -> Boolean): Long =
    filterIsInstance<Transaction.Expense>()
        .filter { it.fundId == fundId && isIncluded(it.date) }
        .sumOf { it.amount }

/**
 * The balance of [fund] at the end of [date]: the opening balance, plus allocations for budget
 * months up to the month of [date], minus expenses up to [date]. Negative when overspent.
 */
fun balance(fund: Fund, transactions: List<Transaction>, date: LocalDate): Long =
    fund.openingBalance +
        transactions.allocatedTo(fund.id) { it <= date.yearMonth } -
        transactions.spentFrom(fund.id) { it <= date }

/** What [fund] could spend in [month]: its balance as the month began plus the month's allocations. */
fun available(fund: Fund, transactions: List<Transaction>, month: YearMonth): Long =
    fund.openingBalance +
        transactions.allocatedTo(fund.id) { it <= month } -
        transactions.spentFrom(fund.id) { it < month.firstDay }

/** The balance of [fund] on the last day of [month]. */
fun endBalance(fund: Fund, transactions: List<Transaction>, month: YearMonth): Long =
    balance(fund, transactions, month.lastDay)

/**
 * How much [goal] plans to have saved by the end of [month], saving evenly from its start month
 * through its due month. Zero before the start month and the whole amount after the due month.
 */
fun planned(goal: FundGoal.Target, month: YearMonth): Long {
    val totalMonths = goal.startMonth.monthsUntil(goal.dueMonth) + 1
    if (totalMonths <= 0) return goal.amount
    val elapsedMonths = (goal.startMonth.monthsUntil(month) + 1).coerceIn(0, totalMonths)
    return goal.amount * elapsedMonths / totalMonths
}

/** How far [balance] is ahead of the plan of [goal] in [month]; negative when behind. */
fun planDifference(goal: FundGoal.Target, balance: Long, month: YearMonth): Long =
    balance - planned(goal, month)

/** What it takes to reach a goal from the month after the current one. */
sealed interface MonthlyRequirement {
    /** The balance already covers the goal. */
    data object Reached : MonthlyRequirement

    /** Saving [amount] a month for [months] months reaches the goal. */
    data class PerMonth(val amount: Long, val months: Int) : MonthlyRequirement

    /** No month is left before the due month; [shortfall] is still missing. */
    data class Overdue(val shortfall: Long) : MonthlyRequirement
}

/**
 * The monthly amount that reaches [goal] by its due month when saving from the month after
 * [month], given the current [balance]. Rounded up so that it is enough.
 */
fun monthlyRequirement(
    goal: FundGoal.Target,
    balance: Long,
    month: YearMonth,
): MonthlyRequirement {
    val shortfall = goal.amount - balance
    val months = month.monthsUntil(goal.dueMonth)
    return when {
        shortfall <= 0 -> MonthlyRequirement.Reached
        months <= 0 -> MonthlyRequirement.Overdue(shortfall)
        else -> MonthlyRequirement.PerMonth((shortfall + months - 1) / months, months)
    }
}
