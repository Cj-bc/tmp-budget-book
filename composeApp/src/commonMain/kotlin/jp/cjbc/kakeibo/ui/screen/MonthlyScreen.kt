package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Budget
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.model.categoryColor
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.monthly_budget
import jp.cjbc.kakeibo.resources.monthly_budget_label
import jp.cjbc.kakeibo.resources.monthly_by_category
import jp.cjbc.kakeibo.resources.monthly_difference
import jp.cjbc.kakeibo.resources.monthly_empty
import jp.cjbc.kakeibo.resources.period_1_month
import jp.cjbc.kakeibo.resources.period_1_year
import jp.cjbc.kakeibo.resources.period_3_months
import jp.cjbc.kakeibo.resources.period_6_months
import jp.cjbc.kakeibo.resources.save
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.BORDER_COLOR
import jp.cjbc.kakeibo.ui.theme.CARD_SHAPE
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.SURFACE_COLOR
import jp.cjbc.kakeibo.util.addMonths
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.formatYen
import jp.cjbc.kakeibo.util.generateId
import org.jetbrains.compose.resources.stringResource

/** Selectable aggregation periods: the number of months and its label. */
private val PERIOD_OPTIONS = listOf(
    1 to Res.string.period_1_month,
    3 to Res.string.period_3_months,
    6 to Res.string.period_6_months,
    12 to Res.string.period_1_year,
)

@Composable
internal fun MonthlyScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    val categoryNames = categoryNames()
    var month by remember { mutableStateOf(currentYearMonth()) }
    var period by remember { mutableStateOf(1) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var budgetText by remember { mutableStateOf("") }
    val months = (0 until period).map { addMonths(month, it - period + 1) }
    val transactions = ledger.transactions.filter { transaction ->
        months.any { transaction.date.startsWith(it) }
    }
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    ScreenColumn {
        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton("‹", { month = addMonths(month, -1) }, isPrimary = false)
                Text(formatYearMonth(month), fontWeight = FontWeight.Bold)
                ActionButton("›", { month = addMonths(month, 1) }, isPrimary = false)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PERIOD_OPTIONS.forEach { (monthCount, label) ->
                ActionButton(
                    stringResource(label),
                    { period = monthCount },
                    isPrimary = monthCount == period,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                stringResource(Res.string.income),
                income,
                INCOME_COLOR,
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(Res.string.expense),
                expense,
                EXPENSE_COLOR,
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(Res.string.monthly_difference),
                income - expense,
                if (income >= expense) INCOME_COLOR else EXPENSE_COLOR,
                Modifier.weight(1f),
            )
        }
        SectionHeader(stringResource(Res.string.monthly_by_category))
        SectionCard {
            val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
            // Categories removed in the settings still appear while they have expenses.
            val shownCategories =
                (ledger.settings.expenseCategories + expenseTransactions.map { it.category })
                    .distinct()
                    .filter { category ->
                        expenseTransactions.any { it.category == category } ||
                            ledger.budgets.any { it.category == category }
                    }
            if (shownCategories.isEmpty()) {
                EmptyMessage(stringResource(Res.string.monthly_empty))
            } else {
                shownCategories.forEach { category ->
                    val actual = expenseTransactions
                        .filter { it.category == category }
                        .sumOf { it.amount }
                    val monthlyBudget =
                        ledger.budgets.find { it.category == category }?.monthlyAmount ?: 0.0
                    val budget = monthlyBudget * period
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(categoryNames.display(category), Modifier.weight(1f))
                        AmountText(actual)
                        Text(
                            " ✏",
                            modifier = Modifier.clickable {
                                editingCategory = category
                                budgetText = monthlyBudget.toString()
                            },
                        )
                    }
                    if (budget > 0) {
                        val isOverBudget = actual > budget
                        AmountProgressBar(actual, budget, Color(categoryColor(category)))
                        Text(
                            stringResource(Res.string.monthly_budget, formatYen(budget)) +
                                if (isOverBudget) "  ⚠ +${formatYen(actual - budget)}" else "",
                            color = if (isOverBudget) EXPENSE_COLOR else MUTED_COLOR,
                            fontSize = 11.sp,
                        )
                    }
                    if (editingCategory == category) {
                        LabeledTextField(
                            budgetText,
                            stringResource(Res.string.monthly_budget_label),
                            { budgetText = it },
                        )
                        ActionButton(stringResource(Res.string.save), {
                            val value = budgetText.toDoubleOrNull() ?: 0.0
                            val existing = ledger.budgets.find { it.category == category }
                            ledger.updateBudgets(
                                if (existing == null) {
                                    ledger.budgets + Budget(generateId(), category, value)
                                } else {
                                    ledger.budgets.map {
                                        if (it.id == existing.id) {
                                            it.copy(monthlyAmount = value)
                                        } else {
                                            it
                                        }
                                    }
                                },
                            )
                            editingCategory = null
                            onLedgerChange()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Double, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(SURFACE_COLOR, CARD_SHAPE)
            .border(1.dp, BORDER_COLOR, CARD_SHAPE)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MUTED_COLOR, fontSize = 11.sp)
        Text(formatYen(value), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
