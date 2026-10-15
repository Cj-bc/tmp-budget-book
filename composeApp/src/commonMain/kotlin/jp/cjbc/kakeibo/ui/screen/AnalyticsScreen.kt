package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.analytics_cumulative_title
import jp.cjbc.kakeibo.resources.analytics_monthly_title
import jp.cjbc.kakeibo.resources.analytics_pattern_count
import jp.cjbc.kakeibo.resources.analytics_pattern_summary
import jp.cjbc.kakeibo.resources.analytics_patterns_empty
import jp.cjbc.kakeibo.resources.analytics_patterns_title
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.last_applied
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val ANALYZED_MONTH_COUNT = 12
private const val MIN_PATTERN_OCCURRENCES = 3
private const val MAX_SHOWN_PATTERNS = 12

private data class MonthlySummary(val yearMonth: YearMonth, val income: Long, val expense: Long)

private data class PurchasePattern(
    val memo: String,
    val count: Int,
    val intervalDays: Int,
    /** Negative for expenses. */
    val averageAmount: Long,
    val lastDate: LocalDate,
)

@Composable
internal fun AnalyticsScreen(ledger: Ledger) {
    val transactions = ledger.data.transactions
    val summaries = summarizeRecentMonths(transactions, currentMonth())
    val maxValue = max(1L, summaries.maxOf { max(it.income, it.expense) })
    val patterns = findPurchasePatterns(transactions)

    ScreenColumn {
        SectionHeader(stringResource(Res.string.analytics_monthly_title))
        SectionCard {
            Text(stringResource(Res.string.income), color = AppTheme.colors.positive, fontSize = 11.sp)
            Text(stringResource(Res.string.expense), color = AppTheme.colors.negative, fontSize = 11.sp)
            summaries.forEach { (yearMonth, income, expense) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMonth(yearMonth),
                        color = AppTheme.colors.onSurfaceMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        AmountProgressBar(income, maxValue, AppTheme.colors.positive)
                        Spacer(Modifier.height(2.dp))
                        AmountProgressBar(expense, maxValue, AppTheme.colors.negative)
                    }
                    Text(
                        " ${formatAmount(income - expense, signed = true)}",
                        fontSize = 10.sp,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        SectionHeader(stringResource(Res.string.analytics_cumulative_title))
        SectionCard {
            var cumulative = 0L
            summaries.forEach { (yearMonth, income, expense) ->
                cumulative += income - expense
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        formatYearMonth(yearMonth),
                        color = AppTheme.colors.onSurfaceMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    AmountText(cumulative, signed = true)
                }
            }
        }
        SectionHeader(stringResource(Res.string.analytics_patterns_title))
        SectionCard {
            if (patterns.isEmpty()) {
                EmptyMessage(stringResource(Res.string.analytics_patterns_empty))
            } else {
                patterns.take(MAX_SHOWN_PATTERNS).forEach { pattern ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(pattern.memo, fontWeight = FontWeight.Medium)
                            Text(
                                listOf(
                                    stringResource(
                                        Res.string.analytics_pattern_summary,
                                        pattern.intervalDays,
                                        pluralStringResource(
                                            Res.plurals.analytics_pattern_count,
                                            pattern.count,
                                            pattern.count,
                                        ),
                                    ),
                                    stringResource(Res.string.last_applied, pattern.lastDate.toString()),
                                ).joinToString(" · "),
                                color = AppTheme.colors.onSurfaceMuted,
                                fontSize = 11.sp,
                            )
                        }
                        AmountText(pattern.averageAmount, signed = true)
                    }
                }
            }
        }
    }
}

private fun summarizeRecentMonths(transactions: List<Transaction>, current: YearMonth): List<MonthlySummary> =
    (0 until ANALYZED_MONTH_COUNT)
        .map { current.minus(ANALYZED_MONTH_COUNT - 1 - it, DateTimeUnit.MONTH) }
        .map { yearMonth ->
            val monthTransactions = transactions.filter { it.date.yearMonth == yearMonth }
            MonthlySummary(
                yearMonth = yearMonth,
                income = monthTransactions.filterIsInstance<Transaction.Income>().sumOf { it.amount },
                expense = monthTransactions.filterIsInstance<Transaction.Expense>().sumOf { it.amount },
            )
        }

/** Groups transactions by memo and summarizes those recorded repeatedly. */
private fun findPurchasePatterns(transactions: List<Transaction>): List<PurchasePattern> =
    transactions
        .filter { it.memo.isNotBlank() }
        .groupBy { it.memo.trim().lowercase() }
        .values
        .filter { it.size >= MIN_PATTERN_OCCURRENCES }
        .map { group ->
            val ordered = group.sortedBy { it.date }
            val gaps = ordered.zipWithNext { a, b -> a.date.daysUntil(b.date) }
            PurchasePattern(
                memo = ordered.first().memo,
                count = group.size,
                intervalDays = gaps.average().roundToInt(),
                averageAmount = group
                    .map { if (it is Transaction.Expense) -it.amount else it.amount }
                    .average()
                    .roundToLong(),
                lastDate = ordered.last().date,
            )
        }
        .sortedBy { it.intervalDays }
