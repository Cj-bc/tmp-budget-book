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
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.date_label
import jp.cjbc.kakeibo.resources.savings_cancel
import jp.cjbc.kakeibo.resources.savings_contribution_label
import jp.cjbc.kakeibo.resources.savings_empty
import jp.cjbc.kakeibo.resources.savings_goals
import jp.cjbc.kakeibo.resources.savings_monthly_target
import jp.cjbc.kakeibo.resources.savings_monthly_target_label
import jp.cjbc.kakeibo.resources.savings_months_left
import jp.cjbc.kakeibo.resources.savings_name_label
import jp.cjbc.kakeibo.resources.savings_new_goal
import jp.cjbc.kakeibo.resources.savings_period
import jp.cjbc.kakeibo.resources.savings_progress
import jp.cjbc.kakeibo.resources.savings_record
import jp.cjbc.kakeibo.resources.savings_record_contribution
import jp.cjbc.kakeibo.resources.savings_saved
import jp.cjbc.kakeibo.resources.savings_start_month_label
import jp.cjbc.kakeibo.resources.savings_target_month_label
import jp.cjbc.kakeibo.resources.savings_total_target
import jp.cjbc.kakeibo.resources.savings_vs_plan
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
            SectionHeader(stringResource(Res.string.savings_goals))
            ActionButton(stringResource(Res.string.add), { isAdding = !isAdding })
        }
        if (isAdding) {
            SectionCard {
                Text(stringResource(Res.string.savings_new_goal), fontWeight = FontWeight.Bold)
                LabeledTextField(
                    name,
                    stringResource(Res.string.savings_name_label),
                    { name = it },
                )
                LabeledTextField(
                    monthlyTarget,
                    stringResource(Res.string.savings_monthly_target_label),
                    { monthlyTarget = it },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(
                        startYearMonth,
                        stringResource(Res.string.savings_start_month_label),
                        { startYearMonth = it },
                        Modifier.weight(1f),
                    )
                    LabeledTextField(
                        targetYearMonth,
                        stringResource(Res.string.savings_target_month_label),
                        { targetYearMonth = it },
                        Modifier.weight(1f),
                    )
                }
                Row {
                    ActionButton(stringResource(Res.string.add_confirm), {
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
                    ActionButton(
                        stringResource(Res.string.cancel),
                        { isAdding = false },
                        isPrimary = false,
                    )
                }
            }
        }
        if (ledger.savings.isEmpty() && !isAdding) {
            SectionCard { EmptyMessage(stringResource(Res.string.savings_empty)) }
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
                        stringResource(
                            Res.string.savings_period,
                            formatYearMonth(saving.startYearMonth),
                            formatYearMonth(saving.targetYearMonth),
                            pluralStringResource(
                                Res.plurals.savings_months_left,
                                remainingMonths,
                                remainingMonths,
                            ),
                        ),
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
                MetricItem(
                    stringResource(Res.string.savings_monthly_target),
                    formatYen(saving.monthlyTarget),
                    ACCENT_COLOR,
                )
                MetricItem(
                    stringResource(Res.string.savings_total_target),
                    formatYen(total),
                    TEXT_COLOR,
                )
                MetricItem(
                    stringResource(Res.string.savings_saved),
                    formatYen(actual),
                    INCOME_COLOR,
                )
                MetricItem(
                    stringResource(Res.string.savings_vs_plan),
                    (if (difference >= 0) "+" else "") + formatYen(abs(difference)),
                    if (difference >= 0) INCOME_COLOR else EXPENSE_COLOR,
                )
            }
            AmountProgressBar(actual, total, INCOME_COLOR)
            val achievedPercent = ((actual / total) * 100).coerceAtMost(100.0).roundToInt()
            Text(
                stringResource(
                    Res.string.savings_progress,
                    "$achievedPercent%",
                    formatYen(max(0.0, total - actual)),
                ),
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
                LabeledTextField(
                    contributionAmount,
                    stringResource(Res.string.savings_contribution_label),
                    { contributionAmount = it },
                )
                LabeledTextField(
                    contributionDate,
                    stringResource(Res.string.date_label),
                    { contributionDate = it },
                )
                Row {
                    ActionButton(stringResource(Res.string.savings_record), {
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
                    ActionButton(
                        stringResource(Res.string.savings_cancel),
                        { contributingSavingId = null },
                        isPrimary = false,
                    )
                }
            } else {
                ActionButton(
                    stringResource(Res.string.savings_record_contribution),
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
