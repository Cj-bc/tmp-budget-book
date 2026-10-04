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
import jp.cjbc.kakeibo.model.CATEGORY_COLORS
import jp.cjbc.kakeibo.model.EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
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

/** Selectable aggregation periods: the number of months and its label. */
private val PERIOD_OPTIONS = listOf(1 to "1ヶ月", 3 to "3ヶ月", 6 to "6ヶ月", 12 to "1年")

@Composable
internal fun MonthlyScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
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
                    label,
                    { period = monthCount },
                    isPrimary = monthCount == period,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("収入", income, INCOME_COLOR, Modifier.weight(1f))
            StatTile("支出", expense, EXPENSE_COLOR, Modifier.weight(1f))
            StatTile(
                "差額",
                income - expense,
                if (income >= expense) INCOME_COLOR else EXPENSE_COLOR,
                Modifier.weight(1f),
            )
        }
        SectionHeader("カテゴリ別支出")
        SectionCard {
            val shownCategories = EXPENSE_CATEGORIES.filter { category ->
                val hasExpense = transactions.any {
                    it.type == TransactionType.EXPENSE && it.category == category
                }
                hasExpense || ledger.budgets.any { it.category == category }
            }
            if (shownCategories.isEmpty()) {
                EmptyMessage("この期間の記録がありません")
            } else {
                shownCategories.forEach { category ->
                    val actual = transactions
                        .filter { it.type == TransactionType.EXPENSE && it.category == category }
                        .sumOf { it.amount }
                    val monthlyBudget =
                        ledger.budgets.find { it.category == category }?.monthlyAmount ?: 0.0
                    val budget = monthlyBudget * period
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(category, Modifier.weight(1f))
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
                        AmountProgressBar(actual, budget, Color(CATEGORY_COLORS.getValue(category)))
                        Text(
                            "予算 ${formatYen(budget)}" +
                                if (isOverBudget) "  ⚠ +${formatYen(actual - budget)}" else "",
                            color = if (isOverBudget) EXPENSE_COLOR else MUTED_COLOR,
                            fontSize = 11.sp,
                        )
                    }
                    if (editingCategory == category) {
                        LabeledTextField(budgetText, "月次予算", { budgetText = it })
                        ActionButton("保存", {
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
