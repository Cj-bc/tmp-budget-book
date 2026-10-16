package jp.cjbc.kakeibo.model

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.yearMonth

/** The [count] months ending with [last], oldest first. */
fun monthsEndingWith(last: YearMonth, count: Int = 12): List<YearMonth> =
    (count - 1 downTo 0).map { last.minus(it, DateTimeUnit.MONTH) }

/** Incomes and expenses dated in one month. */
data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

fun monthTotals(transactions: List<Transaction>, months: List<YearMonth>): List<MonthTotals> =
    months.map { month ->
        val inMonth = transactions.filter { it.date.yearMonth == month }
        MonthTotals(
            month,
            inMonth.filterIsInstance<Transaction.Income>().sumOf { it.amount },
            inMonth.filterIsInstance<Transaction.Expense>().sumOf { it.amount },
        )
    }

/**
 * The running total of incomes minus expenses at the end of each of [months], counting
 * everything before the first month too.
 */
fun cumulativeBalances(transactions: List<Transaction>, months: List<YearMonth>): List<Long> =
    months.map { month ->
        transactions.filter { it.date.yearMonth <= month }.sumOf { it.signedAmount }
    }

/** An amount spent, split by something (a category or a fund). */
data class Share<K>(val key: K, val amount: Long)

/** The expenses of [month] paid from [fundId], by category, largest first. */
fun fundSpendingByCategory(transactions: List<Transaction>, fundId: FundId, month: YearMonth): List<Share<CategoryId>> =
    transactions.filterIsInstance<Transaction.Expense>()
        .filter { it.fundId == fundId && it.date.yearMonth == month }
        .groupBy { it.categoryId }
        .map { (category, list) -> Share(category, list.sumOf { it.amount }) }
        .sortedByDescending { it.amount }

/** The expenses of [categoryId] in [month], by the fund paying them, largest first. */
fun categorySpendingByFund(transactions: List<Transaction>, categoryId: CategoryId, month: YearMonth): List<Share<FundId>> =
    transactions.filterIsInstance<Transaction.Expense>()
        .filter { it.categoryId == categoryId && it.date.yearMonth == month }
        .groupBy { it.fundId }
        .map { (fund, list) -> Share(fund, list.sumOf { it.amount }) }
        .sortedByDescending { it.amount }

/** The least records with the same memo that make a pattern. */
const val MIN_PATTERN_OCCURRENCES = 3

/** Expenses recorded repeatedly with the same memo. */
data class PurchasePattern(
    val memo: String,
    val count: Int,
    /** The average number of days between them. */
    val intervalDays: Int,
    val averageAmount: Long,
    /** The fund that paid most of them. */
    val fundId: FundId,
)

/**
 * Expenses grouped by memo (ignoring case and surrounding spaces), for memos used at least
 * [MIN_PATTERN_OCCURRENCES] times, most frequent first.
 */
fun purchasePatterns(transactions: List<Transaction>): List<PurchasePattern> =
    transactions.filterIsInstance<Transaction.Expense>()
        .filter { it.memo.isNotBlank() }
        .groupBy { it.memo.trim().lowercase() }
        .values
        .filter { it.size >= MIN_PATTERN_OCCURRENCES }
        .map { group ->
            val ordered = group.sortedBy { it.date }
            PurchasePattern(
                memo = ordered.last().memo.trim(),
                count = group.size,
                intervalDays = ordered.zipWithNext { a, b -> a.date.daysUntil(b.date) }.average().roundToInt(),
                averageAmount = group.map { it.amount }.average().roundToLong(),
                fundId = group.groupingBy { it.fundId }.eachCount().maxBy { it.value }.key,
            )
        }
        .sortedWith(compareByDescending<PurchasePattern> { it.count }.thenBy { it.intervalDays })
