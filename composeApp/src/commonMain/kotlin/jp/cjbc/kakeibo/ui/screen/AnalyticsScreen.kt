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
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.formatYen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val ANALYZED_MONTH_COUNT = 12
private const val MIN_PATTERN_OCCURRENCES = 3
private const val MAX_SHOWN_PATTERNS = 12
private const val IMPORT_SUCCESS_MESSAGE = "インポートが完了しました"

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
    var message by remember { mutableStateOf("") }
    val summaries = summarizeRecentMonths(ledger.transactions)
    val maxValue = max(1.0, summaries.maxOf { max(it.income, it.expense) })
    val patterns = findPurchasePatterns(ledger.transactions)

    ScreenColumn {
        SectionHeader("月次収支（過去12ヶ月）")
        SectionCard {
            Text("収入", color = INCOME_COLOR, fontSize = 11.sp)
            Text("支出", color = EXPENSE_COLOR, fontSize = 11.sp)
            summaries.forEach { (yearMonth, income, expense) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        yearMonth.takeLast(2) + "月",
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
        SectionHeader("残高推移（累積）")
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
        SectionHeader("購入パターン")
        SectionCard {
            if (patterns.isEmpty()) {
                EmptyMessage("同じメモで3回以上記録すると購入パターンが表示されます")
            } else {
                patterns.take(MAX_SHOWN_PATTERNS).forEach { pattern ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(pattern.description, fontWeight = FontWeight.Medium)
                            Text(
                                "約${pattern.intervalDays}日ごと · ${pattern.count}回 · " +
                                    "最終: ${pattern.lastDate}",
                                color = MUTED_COLOR,
                                fontSize = 11.sp,
                            )
                        }
                        AmountText(pattern.averageAmount, pattern.type)
                    }
                }
            }
        }
        SectionHeader("データ管理")
        SectionCard {
            Text("JSON バックアップ", fontWeight = FontWeight.Bold)
            Text(
                "エクスポートした内容をコピーして保存し、同じ欄へ貼り付けてインポートできます。",
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
            Row {
                ActionButton("エクスポート", {
                    backupText = ledger.exportBackup()
                    message = "バックアップをこの欄に出力しました"
                })
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    "インポート",
                    {
                        message = if (ledger.importBackup(backupText)) {
                            onLedgerChange()
                            IMPORT_SUCCESS_MESSAGE
                        } else {
                            "ファイルの形式が正しくありません"
                        }
                    },
                    isPrimary = false,
                )
            }
            LabeledTextField(
                backupText,
                "バックアップ JSON",
                { backupText = it },
                Modifier.fillMaxWidth().height(160.dp),
            )
            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = if (message == IMPORT_SUCCESS_MESSAGE) INCOME_COLOR else EXPENSE_COLOR,
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
