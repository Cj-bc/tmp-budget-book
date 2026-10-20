package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** What the history shows. Every condition that is set must hold. */
data class HistoryFilter(
    val month: YearMonth? = null,
    /** Expenses paid from it and incomes allocated to it. */
    val fundId: FundId? = null,
    val category: CategoryKey? = null,
    val incomeOnly: Boolean = false,
    /** Matched against the memo and the category's name, ignoring case. */
    val query: String = "",
) {
    val isEmpty: Boolean get() = this == HistoryFilter(month = month)

    fun matches(transaction: Transaction, categoryName: (CategoryKey) -> String): Boolean {
        val text = query.trim()
        return (month == null || transaction.date.yearMonth == month) &&
            (fundId == null || fundId in transaction.fundIds) &&
            (category == null || transaction.categoryKey == category) &&
            (!incomeOnly || transaction is Transaction.Income) &&
            (
                text.isEmpty() ||
                    transaction.memo.contains(text, ignoreCase = true) ||
                    categoryName(transaction.categoryKey).contains(text, ignoreCase = true)
                )
    }
}

/** The funds a transaction moves money of. */
val Transaction.fundIds: List<FundId>
    get() = when (this) {
        is Transaction.Expense -> listOf(fundId)
        is Transaction.Income -> allocations.map { it.fundId }
    }

/** The transactions of one day and their net. */
data class Day(val date: LocalDate, val transactions: List<Transaction>) {
    val net: Long get() = transactions.sumOf { it.signedAmount }
}

/** [transactions] grouped by date, latest first; their order within a day is kept. */
fun groupByDay(transactions: List<Transaction>): List<Day> =
    transactions.groupBy { it.date }.map { (date, list) -> Day(date, list) }.sortedByDescending { it.date }
