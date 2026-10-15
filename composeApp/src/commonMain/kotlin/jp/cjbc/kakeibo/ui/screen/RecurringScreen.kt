package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.expenseCategory
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.deleted_message
import jp.cjbc.kakeibo.resources.entry_amount_error
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_delete
import jp.cjbc.kakeibo.resources.entry_fund_default
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.entry_fund_expense
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.last_applied
import jp.cjbc.kakeibo.resources.recurring_add_title
import jp.cjbc.kakeibo.resources.recurring_day_error
import jp.cjbc.kakeibo.resources.recurring_day_label
import jp.cjbc.kakeibo.resources.recurring_day_of_month
import jp.cjbc.kakeibo.resources.recurring_empty
import jp.cjbc.kakeibo.resources.recurring_monthly_expense
import jp.cjbc.kakeibo.resources.recurring_name_error
import jp.cjbc.kakeibo.resources.recurring_name_label
import jp.cjbc.kakeibo.resources.recurring_title
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.ChoiceChips
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundLabel
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RecurringScreen(ledger: Ledger, snackbarHostState: SnackbarHostState) {
    val items = ledger.data.recurringItems
    val scope = rememberCoroutineScope()
    var isAdding by remember { mutableStateOf(false) }
    val monthlyTotal = items.filter { it.enabled }.sumOf { it.amount }

    ScreenColumn {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.recurring_title))
            ActionButton(stringResource(Res.string.add), { isAdding = !isAdding })
        }
        if (monthlyTotal > 0) {
            SectionCard {
                // Label above the amount, so long English labels do not run into it.
                Text(stringResource(Res.string.recurring_monthly_expense), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
                AmountText(-monthlyTotal)
            }
        }
        if (isAdding) RecurringItemForm(ledger, onDone = { isAdding = false })
        if (items.isEmpty() && !isAdding) {
            SectionCard { EmptyMessage(stringResource(Res.string.recurring_empty)) }
        }
        if (items.isNotEmpty()) {
            SectionCard {
                items.forEach { item ->
                    RecurringItemRow(ledger, item) {
                        ledger.update { it.copy(recurringItems = it.recurringItems - item) }
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                getString(Res.string.deleted_message, item.name),
                                actionLabel = getString(Res.string.undo),
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                ledger.update { it.copy(recurringItems = it.recurringItems + item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringItemForm(ledger: Ledger, onDone: () -> Unit) {
    val data = ledger.data
    val categoryNames = categoryNames()
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dayOfMonth by remember { mutableStateOf(MIN_RECURRING_DAY.toString()) }
    var categoryId by remember { mutableStateOf<CategoryId?>(null) }
    var fundId by remember { mutableStateOf<FundId?>(null) }
    var showErrors by remember { mutableStateOf(false) }

    val categories = data.activeExpenseCategories
    val selectedCategoryId = categoryId ?: categories.firstOrNull()?.id
    val defaultFundId = selectedCategoryId?.let { data.expenseCategory(it) }?.defaultFundId
    val selectedFundId = fundId ?: defaultFundId
    val parsedAmount = amount.toLongOrNull()?.takeIf { it > 0 }
    val parsedDay = dayOfMonth.toIntOrNull()?.takeIf { it in MIN_RECURRING_DAY..MAX_RECURRING_DAY }

    SectionCard {
        Text(stringResource(Res.string.recurring_add_title), fontWeight = FontWeight.Bold)
        LabeledTextField(name, stringResource(Res.string.recurring_name_label), { name = it }, Modifier.fillMaxWidth())
        if (showErrors && name.isBlank()) FieldError(stringResource(Res.string.recurring_name_error))
        LabeledTextField(
            amount,
            stringResource(Res.string.amount),
            { amount = it.filter(Char::isDigit) },
            Modifier.fillMaxWidth(),
        )
        if (showErrors && parsedAmount == null) FieldError(stringResource(Res.string.entry_amount_error))
        Text(stringResource(Res.string.entry_category), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        ChoiceChips(categories.map { it.id }, selectedCategoryId, {
            categoryId = it
            fundId = null
        }) { Text(categoryNames.display(it)) }
        Text(stringResource(Res.string.entry_fund_expense), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        val defaultBadge = stringResource(Res.string.entry_fund_default)
        ChoiceChips(data.activeFunds, data.activeFunds.find { it.id == selectedFundId }, { fundId = it.id }) {
            FundLabel(it, if (it.id == defaultFundId) defaultBadge else null)
        }
        if (showErrors && selectedFundId == null) FieldError(stringResource(Res.string.entry_fund_error))
        LabeledTextField(
            dayOfMonth,
            stringResource(Res.string.recurring_day_label),
            { dayOfMonth = it.filter(Char::isDigit) },
            Modifier.fillMaxWidth(),
        )
        if (showErrors && parsedDay == null) FieldError(stringResource(Res.string.recurring_day_error))
        Row {
            ActionButton(stringResource(Res.string.add_confirm), {
                if (name.isBlank() || parsedAmount == null || parsedDay == null || selectedCategoryId == null ||
                    selectedFundId == null
                ) {
                    showErrors = true
                    return@ActionButton
                }
                val item = RecurringItem(
                    id = RecurringItemId(generateId()),
                    name = name.trim(),
                    amount = parsedAmount,
                    categoryId = selectedCategoryId,
                    fundId = selectedFundId,
                    dayOfMonth = parsedDay,
                    enabled = true,
                    lastAppliedMonth = null,
                )
                ledger.update { it.copy(recurringItems = it.recurringItems + item) }
                onDone()
            })
            Spacer(Modifier.width(8.dp))
            ActionButton(stringResource(Res.string.cancel), onDone, isPrimary = false)
        }
    }
}

@Composable
private fun RecurringItemRow(ledger: Ledger, item: RecurringItem, onDelete: () -> Unit) {
    val fund = ledger.data.fund(item.fundId)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    categoryNames().display(item.categoryId),
                    fund?.let { stringResource(Res.string.entry_paid_from, it.name) },
                    stringResource(Res.string.recurring_day_of_month, item.dayOfMonth),
                    item.lastAppliedMonth?.let { stringResource(Res.string.last_applied, it.toString()) },
                ).joinToString(" · "),
                color = AppTheme.colors.onSurfaceMuted,
                fontSize = 11.sp,
            )
            AmountText(-item.amount)
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = { enabled ->
                ledger.update { data ->
                    data.copy(
                        recurringItems = data.recurringItems.map {
                            if (it.id == item.id) it.copy(enabled = enabled) else it
                        },
                    )
                }
            },
        )
        Text(
            stringResource(Res.string.entry_delete),
            color = AppTheme.colors.onSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}
