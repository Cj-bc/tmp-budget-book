package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.defaultBudgetMonth
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.previewAllocation
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_amount_for
import jp.cjbc.kakeibo.resources.allocate_balanced
import jp.cjbc.kakeibo.resources.allocate_choose_rest
import jp.cjbc.kakeibo.resources.allocate_create_rule
import jp.cjbc.kakeibo.resources.allocate_edit_rule
import jp.cjbc.kakeibo.resources.allocate_over
import jp.cjbc.kakeibo.resources.allocate_section
import jp.cjbc.kakeibo.resources.allocate_shortage
import jp.cjbc.kakeibo.resources.allocate_title
import jp.cjbc.kakeibo.resources.allocate_total
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.entry_budget_month
import jp.cjbc.kakeibo.resources.entry_record
import jp.cjbc.kakeibo.resources.entry_recorded
import jp.cjbc.kakeibo.resources.entry_saved
import jp.cjbc.kakeibo.resources.method_ask
import jp.cjbc.kakeibo.resources.method_fixed
import jp.cjbc.kakeibo.resources.method_percent_of
import jp.cjbc.kakeibo.resources.method_rest
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.IncomeDraft
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.DropdownChip
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.FundPicker
import jp.cjbc.kakeibo.ui.component.ListRow
import jp.cjbc.kakeibo.ui.component.WarningBanner
import jp.cjbc.kakeibo.ui.component.amountColor
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatShortDate
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** How a rule line takes its amount, e.g. "Fixed" or "14% of total". */
@Composable
internal fun methodLabel(line: RuleLine): String = when (line) {
    is RuleLine.Fixed -> stringResource(Res.string.method_fixed)
    is RuleLine.Percent -> stringResource(Res.string.method_percent_of, "${line.percent}%")
    is RuleLine.Ask -> stringResource(Res.string.method_ask)
}

/**
 * Splits the income in [draft] into funds by its category's rule, then records it. Without a
 * rule, it starts from the "everything left" line alone, whose fund the user picks.
 */
@Composable
internal fun AllocateScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    draft: IncomeDraft,
    newForm: MutableState<EntryForm>,
) {
    val data = ledger.data
    val colors = AppTheme.colors
    val categoryNames = categoryNames()
    val scope = rememberCoroutineScope()
    val original = draft.editing?.let { id -> data.transactions.find { it.id == id } } as? Transaction.Income
    val rule = data.allocationRules.find { it.incomeCategoryId == draft.categoryId }
    var budgetMonth by remember(draft) { mutableStateOf(original?.budgetMonth ?: defaultBudgetMonth(draft.date)) }
    // Amounts typed for the rule's "ask" lines, by line index; editing starts from what was stored.
    var asked by remember(draft, rule) {
        mutableStateOf(
            rule?.lines.orEmpty().withIndex()
                .filter { it.value is RuleLine.Ask }
                .associate { (index, line) ->
                    index to (original?.allocations?.find { it.fundId == line.fundId }?.amount?.toString() ?: "")
                },
        )
    }
    var restFundId by remember(draft) { mutableStateOf<FundId?>(null) }
    val preview = previewAllocation(
        rule,
        draft.amount,
        asked.mapValues { it.value.toLongOrNull() ?: 0 },
        restFundId,
    )
    val categoryName = categoryNames.display(draft.categoryId)

    fun record() {
        if (!preview.isRecordable) return
        val income = Transaction.Income(
            id = draft.editing ?: TransactionId(generateId()),
            date = draft.date,
            amount = draft.amount,
            categoryId = draft.categoryId,
            memo = draft.memo,
            budgetMonth = budgetMonth,
            allocations = preview.allocations(),
        )
        if (draft.editing != null) {
            ledger.update { current ->
                current.copy(transactions = current.transactions.map { if (it.id == income.id) income else it })
            }
            // This screen and the edit screen under it.
            navigator.back(2)
            scope.launch { snackbarHostState.showSnackbar(getString(Res.string.entry_saved)) }
        } else {
            ledger.addTransaction(income)
            newForm.value = newForm.value.copy(digits = "", memo = "")
            navigator.back()
            val label = income.memo.ifBlank { categoryName }
            scope.launch {
                snackbarHostState.showSnackbar(
                    getString(Res.string.entry_recorded, "$label ${formatAmount(income.amount, signed = true)}"),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.allocate_title), { navigator.back() }, stringResource(Res.string.back))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card).padding(AppSpacing.Card),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$categoryName · ${formatShortDate(draft.date)}",
                        style = AppTheme.type.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                    val months = (-1..2).map { draft.date.yearMonth.plus(it, DateTimeUnit.MONTH) }
                    DropdownChip(
                        label = {
                            Text(
                                stringResource(Res.string.entry_budget_month, formatMonth(budgetMonth)),
                                style = AppTheme.type.label,
                            )
                        },
                        options = months,
                        onSelect = { budgetMonth = it },
                        item = { Text(stringResource(Res.string.entry_budget_month, formatYearMonth(it))) },
                    )
                }
                Text(
                    formatAmount(draft.amount, signed = true),
                    style = AppTheme.type.balance,
                    color = colors.positive,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (preview.shortage > 0) {
                WarningBanner {
                    Text(
                        stringResource(Res.string.allocate_shortage, formatAmount(preview.shortage)),
                        style = AppTheme.type.caption,
                        color = colors.onWarningContainer,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.allocate_section), style = AppTheme.type.sectionHeader, color = colors.onSurfaceMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = { navigator.open(Route.AllocationRules(draft.categoryId)) }) {
                    Text(
                        stringResource(
                            if (rule != null) Res.string.allocate_edit_rule else Res.string.allocate_create_rule,
                            categoryName,
                        ),
                        style = AppTheme.type.label,
                        color = colors.positive,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card)) {
                preview.lines.forEachIndexed { index, previewLine ->
                    val fund = data.fund(previewLine.line.fundId)
                    ListRow(first = index == 0) {
                        fund?.let { FundDot(it) }
                        Column(Modifier.weight(1f)) {
                            Text(fund?.name.orEmpty(), style = AppTheme.type.body)
                            Text(methodLabel(previewLine.line), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                        }
                        if (previewLine.line is RuleLine.Ask) {
                            AmountField(
                                value = asked[index].orEmpty(),
                                label = stringResource(Res.string.allocate_amount_for, fund?.name.orEmpty()),
                                onValueChange = { text -> asked = asked + (index to text.filter(Char::isDigit).take(9)) },
                            )
                        } else {
                            Text(formatAmount(previewLine.amount), style = AppTheme.type.listAmount)
                        }
                    }
                }
                ListRow(first = preview.lines.isEmpty()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.method_rest), style = AppTheme.type.body)
                        FundPicker(
                            funds = data.activeFunds,
                            selected = preview.restFundId?.let(data::fund),
                            placeholder = stringResource(Res.string.allocate_choose_rest),
                            onSelect = { restFundId = it.id },
                        )
                    }
                    Text(
                        formatAmount(preview.rest),
                        style = AppTheme.type.listAmount,
                        color = amountColor(preview.rest, signed = false),
                    )
                }
            }
            Box(Modifier.height(8.dp))
        }
        // The footer stays in view: the total against the income, and recording.
        Column(
            Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = AppSpacing.Card, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val total = preview.allocations().sumOf { it.amount }
                Text(
                    stringResource(Res.string.allocate_total, formatAmount(total), formatAmount(draft.amount)),
                    style = AppTheme.type.caption.merge(AppTheme.type.numeric),
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                when {
                    preview.shortage > 0 -> StatusText(stringResource(Res.string.allocate_over, formatAmount(preview.shortage)), colors.negative, warning = true)
                    preview.isRecordable -> StatusText(stringResource(Res.string.allocate_balanced), colors.success, warning = false)
                    else -> StatusText(stringResource(Res.string.allocate_choose_rest), colors.negative, warning = true)
                }
            }
            ActionButton(
                stringResource(Res.string.entry_record),
                ::record,
                enabled = preview.isRecordable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
}

/** "Balanced" with a check, or an over/missing note with a warning sign. */
@Composable
private fun StatusText(text: String, color: Color, warning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (warning) AppIcons.Warning else AppIcons.Check, null, Modifier.size(16.dp), tint = color)
        Text(text, style = AppTheme.type.caption.merge(AppTheme.type.numeric), color = color)
    }
}

/** A right-aligned box for typing a yen amount. */
@Composable
internal fun AmountField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .width(120.dp)
            .height(44.dp)
            .background(colors.background, AppShapes.Tile)
            .border(1.dp, colors.outlineStrong, AppShapes.Tile)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterEnd,
    ) {
        BasicTextField(
            value,
            onValueChange,
            singleLine = true,
            textStyle = AppTheme.type.listAmount.copy(textAlign = TextAlign.End, color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
