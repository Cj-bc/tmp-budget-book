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
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.model.categoriesFor
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.last_applied
import jp.cjbc.kakeibo.resources.recurring_add_title
import jp.cjbc.kakeibo.resources.recurring_category_label
import jp.cjbc.kakeibo.resources.recurring_day_label
import jp.cjbc.kakeibo.resources.recurring_day_of_month
import jp.cjbc.kakeibo.resources.recurring_empty
import jp.cjbc.kakeibo.resources.recurring_expenses
import jp.cjbc.kakeibo.resources.recurring_incomes
import jp.cjbc.kakeibo.resources.recurring_monthly_expense
import jp.cjbc.kakeibo.resources.recurring_monthly_income
import jp.cjbc.kakeibo.resources.recurring_name_label
import jp.cjbc.kakeibo.resources.recurring_net
import jp.cjbc.kakeibo.resources.recurring_off
import jp.cjbc.kakeibo.resources.recurring_on
import jp.cjbc.kakeibo.resources.recurring_title
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.MetricItem
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.util.formatYen
import jp.cjbc.kakeibo.util.generateId
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

private const val MIN_DAY_OF_MONTH = 1
private const val MAX_DAY_OF_MONTH = 28

@Composable
internal fun RecurringScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    val categoryNames = categoryNames()
    var isAdding by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val defaultCategory = categoryNames.display(categoriesFor(type).first())
    var category by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var dayOfMonth by remember { mutableStateOf("1") }
    val activeIncome = ledger.recurringItems
        .filter { it.active && it.type == TransactionType.INCOME }
        .sumOf { it.amount }
    val activeExpense = ledger.recurringItems
        .filter { it.active && it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    ScreenColumn {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.recurring_title))
            ActionButton(stringResource(Res.string.add), { isAdding = !isAdding })
        }
        if (activeIncome > 0 || activeExpense > 0) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem(
                        stringResource(Res.string.recurring_monthly_income),
                        "+${formatYen(activeIncome)}",
                        INCOME_COLOR,
                    )
                    MetricItem(
                        stringResource(Res.string.recurring_monthly_expense),
                        "-${formatYen(activeExpense)}",
                        EXPENSE_COLOR,
                    )
                    MetricItem(
                        stringResource(Res.string.recurring_net),
                        formatYen(abs(activeIncome - activeExpense)),
                        if (activeIncome >= activeExpense) INCOME_COLOR else EXPENSE_COLOR,
                    )
                }
            }
        }
        if (isAdding) {
            SectionCard {
                Text(stringResource(Res.string.recurring_add_title), fontWeight = FontWeight.Bold)
                Row {
                    TransactionType.entries.forEach { entry ->
                        ActionButton(
                            label = stringResource(
                                if (entry == TransactionType.EXPENSE) {
                                    Res.string.expense
                                } else {
                                    Res.string.income
                                },
                            ),
                            onClick = { type = entry },
                            isPrimary = type == entry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                LabeledTextField(
                    description,
                    stringResource(Res.string.recurring_name_label),
                    { description = it },
                )
                LabeledTextField(amount, stringResource(Res.string.amount), { amount = it })
                LabeledTextField(
                    category,
                    stringResource(Res.string.recurring_category_label),
                    { category = it },
                )
                LabeledTextField(
                    dayOfMonth,
                    stringResource(Res.string.recurring_day_label),
                    { dayOfMonth = it },
                )
                Row {
                    ActionButton(stringResource(Res.string.add_confirm), {
                        amount.toDoubleOrNull()
                            ?.takeIf { description.isNotBlank() && it > 0 }
                            ?.let {
                                val item = RecurringItem(
                                    id = generateId(),
                                    description = description.trim(),
                                    amount = it,
                                    type = type,
                                    category = categoryNames.parse(category),
                                    dayOfMonth = (dayOfMonth.toIntOrNull() ?: MIN_DAY_OF_MONTH)
                                        .coerceIn(MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH),
                                )
                                ledger.updateRecurringItems(ledger.recurringItems + item)
                                isAdding = false
                                description = ""
                                amount = ""
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
        if (ledger.recurringItems.isEmpty() && !isAdding) {
            SectionCard { EmptyMessage(stringResource(Res.string.recurring_empty)) }
        }
        TransactionType.entries.reversed().forEach { entry ->
            val items = ledger.recurringItems.filter { it.type == entry }
            if (items.isNotEmpty()) {
                SectionHeader(
                    stringResource(
                        if (entry == TransactionType.EXPENSE) {
                            Res.string.recurring_expenses
                        } else {
                            Res.string.recurring_incomes
                        },
                    ),
                )
                SectionCard {
                    items.forEach { item -> RecurringItemRow(ledger, item, onLedgerChange) }
                }
            }
        }
    }
}

@Composable
private fun RecurringItemRow(ledger: Ledger, item: RecurringItem, onLedgerChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.description, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    categoryNames().display(item.category),
                    stringResource(Res.string.recurring_day_of_month, item.dayOfMonth),
                    item.lastApplied?.let { stringResource(Res.string.last_applied, it) },
                ).joinToString(" · "),
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
        }
        AmountText(item.amount, item.type)
        ActionButton(
            stringResource(if (item.active) Res.string.recurring_on else Res.string.recurring_off),
            {
                ledger.updateRecurringItems(
                    ledger.recurringItems.map {
                        if (it.id == item.id) it.copy(active = !it.active) else it
                    },
                )
                onLedgerChange()
            },
            isPrimary = item.active,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            "×",
            color = MUTED_COLOR,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 8.dp).clickable {
                ledger.updateRecurringItems(ledger.recurringItems.filterNot { it.id == item.id })
                onLedgerChange()
            },
        )
    }
}
