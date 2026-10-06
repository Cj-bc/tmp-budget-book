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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.analytics_backup_description
import jp.cjbc.kakeibo.resources.analytics_backup_label
import jp.cjbc.kakeibo.resources.analytics_backup_title
import jp.cjbc.kakeibo.resources.analytics_cumulative_title
import jp.cjbc.kakeibo.resources.analytics_data_title
import jp.cjbc.kakeibo.resources.analytics_export
import jp.cjbc.kakeibo.resources.analytics_exported
import jp.cjbc.kakeibo.resources.analytics_import
import jp.cjbc.kakeibo.resources.analytics_import_failure
import jp.cjbc.kakeibo.resources.analytics_import_success
import jp.cjbc.kakeibo.resources.analytics_monthly_title
import jp.cjbc.kakeibo.resources.analytics_pattern_count
import jp.cjbc.kakeibo.resources.analytics_pattern_summary
import jp.cjbc.kakeibo.resources.analytics_patterns_empty
import jp.cjbc.kakeibo.resources.analytics_patterns_title
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.last_applied
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.util.addMonths
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.daysBetween
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.formatYen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val ANALYZED_MONTH_COUNT = 12
private const val MIN_PATTERN_OCCURRENCES = 3
private const val MAX_SHOWN_PATTERNS = 12

private data class MonthlySummary(val yearMonth: String, val income: Double, val expense: Double)

private data class PurchasePattern(
    val description: String,
    val count: Int,
    val intervalDays: Int,
    val averageAmount: Double,
    val type: TransactionType,
    val lastDate: String,
)

@Composable
internal fun AnalyticsScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    var backupText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val summaries = summarizeRecentMonths(ledger.transactions)
    val maxValue = max(1.0, summaries.maxOf { max(it.income, it.expense) })
    val patterns = findPurchasePatterns(ledger.transactions)

    ScreenColumn {
        SectionHeader(stringResource(Res.string.analytics_monthly_title))
        SectionCard {
            Text(stringResource(Res.string.income), color = INCOME_COLOR, fontSize = 11.sp)
            Text(stringResource(Res.string.expense), color = EXPENSE_COLOR, fontSize = 11.sp)
            summaries.forEach { (yearMonth, income, expense) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMonth(yearMonth),
                        color = MUTED_COLOR,
                        fontSize = 10.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        AmountProgressBar(income, maxValue, INCOME_COLOR)
                        Spacer(Modifier.height(2.dp))
                        AmountProgressBar(expense, maxValue, EXPENSE_COLOR)
                    }
                    Text(
                        " ${formatYen(income - expense)}",
                        fontSize = 10.sp,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        SectionHeader(stringResource(Res.string.analytics_cumulative_title))
        SectionCard {
            var cumulative = 0.0
            summaries.forEach { (yearMonth, income, expense) ->
                cumulative += income - expense
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        formatYearMonth(yearMonth),
                        color = MUTED_COLOR,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (cumulative >= 0) "+" else "-") + formatYen(abs(cumulative)),
                        color = if (cumulative >= 0) INCOME_COLOR else EXPENSE_COLOR,
                        fontWeight = FontWeight.Bold,
                    )
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
                            Text(pattern.description, fontWeight = FontWeight.Medium)
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
                                    stringResource(Res.string.last_applied, pattern.lastDate),
                                ).joinToString(" · "),
                                color = MUTED_COLOR,
                                fontSize = 11.sp,
                            )
                        }
                        AmountText(pattern.averageAmount, pattern.type)
                    }
                }
            }
        }
        SectionHeader(stringResource(Res.string.analytics_data_title))
        SectionCard {
            Text(stringResource(Res.string.analytics_backup_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.analytics_backup_description),
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
            Row {
                ActionButton(stringResource(Res.string.analytics_export), {
                    backupText = ledger.exportBackup()
                    message = Res.string.analytics_exported
                })
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    stringResource(Res.string.analytics_import),
                    {
                        message = if (ledger.importBackup(backupText)) {
                            onLedgerChange()
                            Res.string.analytics_import_success
                        } else {
                            Res.string.analytics_import_failure
                        }
                    },
                    isPrimary = false,
                )
            }
            LabeledTextField(
                backupText,
                stringResource(Res.string.analytics_backup_label),
                { backupText = it },
                Modifier.fillMaxWidth().height(160.dp),
            )
            message?.let {
                Text(
                    stringResource(it),
                    color = if (it == Res.string.analytics_import_success) {
                        INCOME_COLOR
                    } else {
                        EXPENSE_COLOR
                    },
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun summarizeRecentMonths(transactions: List<Transaction>): List<MonthlySummary> =
    (0 until ANALYZED_MONTH_COUNT)
        .map { addMonths(currentYearMonth(), it - (ANALYZED_MONTH_COUNT - 1)) }
        .map { yearMonth ->
            val monthTransactions = transactions.filter { it.date.startsWith(yearMonth) }
            MonthlySummary(
                yearMonth = yearMonth,
                income = monthTransactions
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amount },
                expense = monthTransactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount },
            )
        }

/** Groups transactions by description and summarizes those recorded repeatedly. */
private fun findPurchasePatterns(transactions: List<Transaction>): List<PurchasePattern> =
    transactions
        .filter { it.description.isNotBlank() }
        .groupBy { it.description.trim().lowercase() }
        .values
        .filter { it.size >= MIN_PATTERN_OCCURRENCES }
        .map { group ->
            val ordered = group.sortedBy { it.date }
            val gaps = ordered.zipWithNext { a, b -> daysBetween(a.date, b.date) }
            PurchasePattern(
                description = ordered.first().description,
                count = group.size,
                intervalDays = gaps.average().roundToInt(),
                averageAmount = group.map { it.amount }.average(),
                type = ordered.first().type,
                lastDate = ordered.last().date,
            )
        }
        .sortedBy { it.intervalDays }
