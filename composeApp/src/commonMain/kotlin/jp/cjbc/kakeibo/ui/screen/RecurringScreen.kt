package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.CategoryKey
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.categoryName
import jp.cjbc.kakeibo.model.expenseCategory
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.util.generateId
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.deleted_message
import jp.cjbc.kakeibo.resources.entry_amount_error
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_category_error
import jp.cjbc.kakeibo.resources.entry_delete
import jp.cjbc.kakeibo.resources.entry_fund_default
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.entry_fund_expense
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.entry_save
import jp.cjbc.kakeibo.resources.recurring_active_count
import jp.cjbc.kakeibo.resources.recurring_add_title
import jp.cjbc.kakeibo.resources.recurring_day_error
import jp.cjbc.kakeibo.resources.recurring_day_label
import jp.cjbc.kakeibo.resources.recurring_day_of_month
import jp.cjbc.kakeibo.resources.recurring_edit_title
import jp.cjbc.kakeibo.resources.recurring_empty
import jp.cjbc.kakeibo.resources.recurring_enabled
import jp.cjbc.kakeibo.resources.recurring_monthly_expense
import jp.cjbc.kakeibo.resources.recurring_name_error
import jp.cjbc.kakeibo.resources.recurring_name_label
import jp.cjbc.kakeibo.resources.tab_recurring
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.amountColor
import jp.cjbc.kakeibo.ui.component.AppIconButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.ChoiceChips
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SwipeToDelete
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Deletes [item], with a snackbar that can bring it back. */
private fun deleteRecurringItem(ledger: Ledger, item: RecurringItem, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
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

/**
 * The recurring tab: the monthly total of the enabled expenses, then each expense with a switch.
 * Tapping one edits it, swiping it deletes it.
 */
@Composable
internal fun RecurringScreen(ledger: Ledger, navigator: AppNavigator, snackbarHostState: SnackbarHostState) {
    val data = ledger.data
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val items = data.recurringItems.sortedBy { it.dayOfMonth }
    val enabled = items.filter { it.enabled }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.tab_recurring), onBack = null) {
            AppIconButton(AppIcons.Add, stringResource(Res.string.recurring_add_title)) { navigator.open(Route.RecurringEdit(null)) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    stringResource(Res.string.recurring_empty),
                    icon = AppIcons.Recurring,
                    action = stringResource(Res.string.recurring_add_title),
                    onAction = { navigator.open(Route.RecurringEdit(null)) },
                )
                return@Column
            }
            SectionCard {
                // The label sits over the amount, so that a long English label never runs into it.
                Text(stringResource(Res.string.recurring_monthly_expense), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                AmountText(enabled.sumOf { it.amount }, style = AppTheme.type.balance)
                Text(
                    stringResource(Res.string.recurring_active_count, items.size, enabled.size),
                    style = AppTheme.type.caption,
                    color = colors.onSurfaceMuted,
                )
            }
            Column(Modifier.fillMaxWidth().clip(AppShapes.Card).background(colors.surface)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                    // The key lets the swipe state follow the item when another one is deleted.
                    key(item.id.value) {
                        SwipeToDelete(onDelete = { deleteRecurringItem(ledger, item, snackbarHostState, scope) }) {
                            RecurringRow(ledger, item) { navigator.open(Route.RecurringEdit(item.id)) }
                        }
                    }
                }
            }
        }
    }
}

/** "Rent / Day 27 of every month · Housing · from Housing", the amount and the switch. */
@Composable
private fun RecurringRow(ledger: Ledger, item: RecurringItem, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val fund = ledger.data.fund(item.fundId)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(fund?.let { Color(it.color.argb) } ?: colors.disabled, AppShapes.Pill))
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Text(
                item.name,
                style = AppTheme.type.bodyBold,
                color = if (item.enabled) colors.onSurface else colors.disabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    stringResource(Res.string.recurring_day_of_month, item.dayOfMonth),
                    ledger.data.categoryName(CategoryKey(item.categoryId, isExpense = true)),
                    fund?.let { stringResource(Res.string.entry_paid_from, it.name) },
                ).joinToString(" · "),
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
                maxLines = 2,
            )
        }
        Text(
            formatAmount(-item.amount, signed = true),
            style = AppTheme.type.listAmount,
            color = if (item.enabled) amountColor(-item.amount, signed = true) else colors.disabled,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Switch(item.enabled, { enabled ->
            ledger.update { data ->
                data.copy(recurringItems = data.recurringItems.map { if (it.id == item.id) it.copy(enabled = enabled) else it })
            }
        })
    }
}

/** Adding a recurring expense, or editing [Route.RecurringEdit.id]. The fund is required. */
@Composable
internal fun RecurringEditScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    route: Route.RecurringEdit,
) {
    val data = ledger.data
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val existing = route.id?.let { id -> data.recurringItems.find { it.id == id } }
    var name by remember(route) { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember(route) { mutableStateOf(existing?.amount?.toString().orEmpty()) }
    var day by remember(route) { mutableStateOf((existing?.dayOfMonth ?: MIN_RECURRING_DAY).toString()) }
    var categoryId by remember(route) { mutableStateOf<CategoryId?>(existing?.categoryId) }
    var fundId by remember(route) { mutableStateOf<FundId?>(existing?.fundId) }
    var enabled by remember(route) { mutableStateOf(existing?.enabled ?: true) }
    var showErrors by remember(route) { mutableStateOf(false) }

    val categories = data.activeExpenseCategories
    val defaultFundId = categoryId?.let(data::expenseCategory)?.defaultFundId
    val parsedAmount = amount.toLongOrNull()?.takeIf { it > 0 }
    val parsedDay = day.toIntOrNull()?.takeIf { it in MIN_RECURRING_DAY..MAX_RECURRING_DAY }

    fun save() {
        val category = categoryId
        val fund = fundId
        if (name.isBlank() || parsedAmount == null || parsedDay == null || category == null || fund == null) {
            showErrors = true
            return
        }
        val item = RecurringItem(
            id = existing?.id ?: RecurringItemId(generateId()),
            name = name.trim(),
            amount = parsedAmount,
            categoryId = category,
            fundId = fund,
            dayOfMonth = parsedDay,
            enabled = enabled,
            lastAppliedMonth = existing?.lastAppliedMonth,
        )
        ledger.update { current ->
            current.copy(
                recurringItems = if (existing == null) {
                    current.recurringItems + item
                } else {
                    current.recurringItems.map { if (it.id == existing.id) item else it }
                },
            )
        }
        navigator.back()
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            stringResource(if (existing == null) Res.string.recurring_add_title else Res.string.recurring_edit_title),
            { navigator.back() },
            stringResource(Res.string.back),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                LabeledTextField(
                    name,
                    stringResource(Res.string.recurring_name_label),
                    { name = it },
                    Modifier.fillMaxWidth(),
                    error = stringResource(Res.string.recurring_name_error).takeIf { showErrors && name.isBlank() },
                )
                LabeledTextField(
                    amount,
                    stringResource(Res.string.amount),
                    { amount = it.filter(Char::isDigit).take(9) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    error = stringResource(Res.string.entry_amount_error).takeIf { showErrors && parsedAmount == null },
                )
                LabeledTextField(
                    day,
                    stringResource(Res.string.recurring_day_label),
                    { day = it.filter(Char::isDigit).take(2) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    error = stringResource(Res.string.recurring_day_error).takeIf { showErrors && parsedDay == null },
                )
            }
            SectionCard {
                FieldLabel(stringResource(Res.string.entry_category))
                ChoiceChips(categories.map { it.id }, categoryId, {
                    categoryId = it
                    // Paid from the category's fund unless another one is picked.
                    fundId = data.expenseCategory(it)?.defaultFundId
                }) { Text(data.categoryName(CategoryKey(it, isExpense = true)), style = AppTheme.type.label) }
                if (showErrors && categoryId == null) FieldError(stringResource(Res.string.entry_category_error))
                FieldLabel(stringResource(Res.string.entry_fund_expense), Modifier.padding(top = 12.dp))
                val defaultBadge = stringResource(Res.string.entry_fund_default)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.activeFunds.forEach { fund ->
                        FundChip(fund, fund.id == fundId, { fundId = fund.id }, badge = defaultBadge.takeIf { fund.id == defaultFundId })
                    }
                }
                if (showErrors && fundId == null) FieldError(stringResource(Res.string.entry_fund_error))
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.recurring_enabled), style = AppTheme.type.body, modifier = Modifier.weight(1f))
                    Switch(enabled, { enabled = it })
                }
            }
            ActionButton(stringResource(Res.string.entry_save), ::save, modifier = Modifier.fillMaxWidth().height(52.dp))
            if (existing != null) {
                ActionButton(
                    stringResource(Res.string.entry_delete),
                    {
                        navigator.back()
                        deleteRecurringItem(ledger, existing, snackbarHostState, scope)
                    },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.sectionHeader, color = AppTheme.colors.onSurfaceMuted, modifier = modifier.padding(bottom = 6.dp))
}
