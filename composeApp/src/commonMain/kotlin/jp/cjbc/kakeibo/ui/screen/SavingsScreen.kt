package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.currentDate
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Contribution
import jp.cjbc.kakeibo.model.Saving
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.MetricItem
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.TEXT_COLOR
import jp.cjbc.kakeibo.util.addMonths
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.formatYen
import jp.cjbc.kakeibo.util.generateId
import jp.cjbc.kakeibo.util.monthsBetween
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun SavingsScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    var isAdding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var monthlyTarget by remember { mutableStateOf("") }
    var startYearMonth by remember { mutableStateOf(currentYearMonth()) }
    var targetYearMonth by remember { mutableStateOf(addMonths(currentYearMonth(), 12)) }

    ScreenColumn {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("積立目標")
            ActionButton("+ 追加", { isAdding = !isAdding })
        }
        if (isAdding) {
            SectionCard {
                Text("新しい積立目標", fontWeight = FontWeight.Bold)
                LabeledTextField(name, "名前 / 目的", { name = it })
                LabeledTextField(monthlyTarget, "月次目標額", { monthlyTarget = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(
                        startYearMonth,
                        "開始月",
                        { startYearMonth = it },
                        Modifier.weight(1f),
                    )
                    LabeledTextField(
                        targetYearMonth,
                        "目標達成月",
                        { targetYearMonth = it },
                        Modifier.weight(1f),
                    )
                }
                Row {
                    ActionButton("追加する", {
                        monthlyTarget.toDoubleOrNull()
                            ?.takeIf { name.isNotBlank() && it > 0 }
                            ?.let {
                                val saving = Saving(
                                    id = generateId(),
                                    name = name.trim(),
                                    monthlyTarget = it,
                                    startYearMonth = startYearMonth,
                                    targetYearMonth = targetYearMonth,
                                )
                                ledger.updateSavings(ledger.savings + saving)
                                isAdding = false
                                name = ""
                                monthlyTarget = ""
                                onLedgerChange()
                            }
                    })
                    Spacer(Modifier.width(8.dp))
                    ActionButton("キャンセル", { isAdding = false }, isPrimary = false)
                }
            }
        }
        if (ledger.savings.isEmpty() && !isAdding) {
            SectionCard { EmptyMessage("🎯\n積立目標を設定しましょう") }
        }
        SavingList(ledger, onLedgerChange)
    }
}

@Composable
private fun SavingList(ledger: Ledger, onLedgerChange: () -> Unit) {
    var contributingSavingId by remember { mutableStateOf<String?>(null) }
    var contributionAmount by remember { mutableStateOf("") }
    var contributionDate by remember { mutableStateOf(currentDate()) }

    ledger.savings.forEach { saving ->
        val totalMonths =
            max(1, monthsBetween(saving.startYearMonth, saving.targetYearMonth) + 1)
        val elapsedMonths =
            min(totalMonths, max(0, monthsBetween(saving.startYearMonth, currentYearMonth()) + 1))
        val total = totalMonths * saving.monthlyTarget
        val actual = saving.contributions.sumOf { it.amount }
        val expected = elapsedMonths * saving.monthlyTarget
        val difference = actual - expected
        val remainingMonths = max(0, monthsBetween(currentYearMonth(), saving.targetYearMonth))

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(saving.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatYearMonth(saving.startYearMonth)} 〜 " +
                            "${formatYearMonth(saving.targetYearMonth)} · あと${remainingMonths}ヶ月",
                        color = MUTED_COLOR,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    "×",
                    color = MUTED_COLOR,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable {
                        ledger.updateSavings(ledger.savings.filterNot { it.id == saving.id })
                        onLedgerChange()
                    },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("月次目標", formatYen(saving.monthlyTarget), ACCENT_COLOR)
                MetricItem("総目標", formatYen(total), TEXT_COLOR)
                MetricItem("積立済", formatYen(actual), INCOME_COLOR)
                MetricItem(
                    "計画差額",
                    (if (difference >= 0) "+" else "") + formatYen(abs(difference)),
                    if (difference >= 0) INCOME_COLOR else EXPENSE_COLOR,
                )
            }
            AmountProgressBar(actual, total, INCOME_COLOR)
            val achievedPercent = ((actual / total) * 100).coerceAtMost(100.0).roundToInt()
            Text(
                "$achievedPercent% 達成　残 ${formatYen(max(0.0, total - actual))}",
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
            saving.contributions.sortedByDescending { it.date }.forEach { contribution ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(contribution.date, color = MUTED_COLOR, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "+${formatYen(contribution.amount)}",
                        color = INCOME_COLOR,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " ×",
                        color = MUTED_COLOR,
                        modifier = Modifier.clickable {
                            ledger.updateSavings(
                                ledger.savings.map {
                                    if (it.id == saving.id) {
                                        it.copy(
                                            contributions = it.contributions.filterNot { other ->
                                                other.id == contribution.id
                                            },
                                        )
                                    } else {
                                        it
                                    }
                                },
                            )
                            onLedgerChange()
                        },
                    )
                }
            }
            if (contributingSavingId == saving.id) {
                LabeledTextField(contributionAmount, "積立額", { contributionAmount = it })
                LabeledTextField(contributionDate, "日付 (YYYY-MM-DD)", { contributionDate = it })
                Row {
                    ActionButton("記録", {
                        contributionAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let { amount ->
                            val contribution = Contribution(generateId(), contributionDate, amount)
                            ledger.updateSavings(
                                ledger.savings.map {
                                    if (it.id == saving.id) {
                                        it.copy(contributions = it.contributions + contribution)
                                    } else {
                                        it
                                    }
                                },
                            )
                            contributingSavingId = null
                            contributionAmount = ""
                            onLedgerChange()
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                    ActionButton("取消", { contributingSavingId = null }, isPrimary = false)
                }
            } else {
                ActionButton(
                    "+ 積立を記録する",
                    {
                        contributingSavingId = saving.id
                        contributionDate = currentDate()
                    },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
