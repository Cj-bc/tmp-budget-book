package jp.cjbc.kakeibo.ui.screen

import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.resources.rule_line_deleted
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.AllocationRule
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.activeIncomeCategories
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.previewAllocation
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_choose_rest
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.method_ask
import jp.cjbc.kakeibo.resources.method_fixed
import jp.cjbc.kakeibo.resources.method_percent
import jp.cjbc.kakeibo.resources.method_rest
import jp.cjbc.kakeibo.resources.rule_add_line
import jp.cjbc.kakeibo.resources.rule_delete_line
import jp.cjbc.kakeibo.resources.rule_description
import jp.cjbc.kakeibo.resources.rule_example
import jp.cjbc.kakeibo.resources.rule_need_rest
import jp.cjbc.kakeibo.resources.rule_no_categories
import jp.cjbc.kakeibo.resources.rule_percent_error
import jp.cjbc.kakeibo.resources.rule_title
import jp.cjbc.kakeibo.resources.rule_value_error
import jp.cjbc.kakeibo.resources.sheet_move_down
import jp.cjbc.kakeibo.resources.sheet_move_up
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.AppChip
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.DropdownChip
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundPicker
import jp.cjbc.kakeibo.ui.component.ListRow
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Method(val label: StringResource) {
    FIXED(Res.string.method_fixed),
    PERCENT(Res.string.method_percent),
    ASK(Res.string.method_ask),
}

private val RuleLine.method: Method
    get() = when (this) {
        is RuleLine.Fixed -> Method.FIXED
        is RuleLine.Percent -> Method.PERCENT
        is RuleLine.Ask -> Method.ASK
    }

/** [this] line paying [fundId] by [method], keeping its value where it can. */
private fun RuleLine.with(fundId: FundId = this.fundId, method: Method = this.method): RuleLine = when (method) {
    Method.FIXED -> RuleLine.Fixed(fundId, (this as? RuleLine.Fixed)?.amount ?: 0)
    Method.PERCENT -> RuleLine.Percent(fundId, (this as? RuleLine.Percent)?.percent ?: 0)
    Method.ASK -> RuleLine.Ask(fundId)
}

/** Replaces the rule of [categoryId] with what [transform] makes of it. */
private fun LedgerData.updatingRule(categoryId: CategoryId, transform: (AllocationRule) -> AllocationRule): LedgerData =
    copy(allocationRules = allocationRules.map { if (it.incomeCategoryId == categoryId) transform(it) else it })

/** A copy of the list with the items at [i] and [j] swapped. */
private fun <T> List<T>.swapped(i: Int, j: Int): List<T> = toMutableList().also {
    val item = it[i]
    it[i] = it[j]
    it[j] = item
}

/**
 * The allocation rule of each income category: lines in order (fund, method, value), and the fund
 * that receives the rest. Every change is saved as it is made.
 */
@Composable
internal fun AllocationRulesScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    route: Route.AllocationRules,
) {
    val scope = rememberCoroutineScope()
    val data = ledger.data
    val colors = AppTheme.colors
    val categoryNames = categoryNames()
    val categories = data.activeIncomeCategories
    var categoryId by remember(route) { mutableStateOf(route.categoryId ?: categories.firstOrNull()?.id) }
    val rule = data.allocationRules.find { it.incomeCategoryId == categoryId }
    val funds = data.activeFunds

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.rule_title), { navigator.back() }, stringResource(Res.string.back))
        val selected = categoryId
        if (selected == null) {
            EmptyState(stringResource(Res.string.rule_no_categories))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    AppChip(categoryNames.display(category.id), category.id == selected, { categoryId = category.id })
                }
            }
            Text(
                stringResource(Res.string.rule_description, categoryNames.display(selected)),
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
            )
            Column(Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card)) {
                rule?.lines.orEmpty().forEachIndexed { index, line ->
                    RuleLineRow(
                        line = line,
                        first = index == 0,
                        canMoveUp = index > 0,
                        canMoveDown = index < rule!!.lines.lastIndex,
                        funds = funds,
                        fundOf = data::fund,
                        onChange = { new ->
                            ledger.update { it.updatingRule(selected) { r -> r.copy(lines = r.lines.toMutableList().also { l -> l[index] = new }) } }
                        },
                        onMove = { delta ->
                            ledger.update { it.updatingRule(selected) { r -> r.copy(lines = r.lines.swapped(index, index + delta)) } }
                        },
                        onDelete = {
                            val before = rule
                            ledger.update { it.updatingRule(selected) { r -> r.copy(lines = r.lines.filterIndexed { i, _ -> i != index }) } }
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    getString(Res.string.rule_line_deleted),
                                    actionLabel = getString(Res.string.undo),
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    ledger.update { it.updatingRule(selected) { before } }
                                }
                            }
                        },
                    )
                }
                if (rule != null) {
                    ListRow(first = rule.lines.isEmpty(), onClick = {
                        val fund = funds.firstOrNull { it.id != rule.restFundId } ?: funds.first()
                        ledger.update { it.updatingRule(selected) { r -> r.copy(lines = r.lines + RuleLine.Fixed(fund.id, 0)) } }
                    }) {
                        Icon(AppIcons.Add, null, Modifier.size(20.dp), tint = colors.positive)
                        Text(stringResource(Res.string.rule_add_line), style = AppTheme.type.label, color = colors.positive)
                    }
                }
                ListRow(first = rule == null) {
                    Text(stringResource(Res.string.method_rest), style = AppTheme.type.bodyBold, maxLines = 1, softWrap = false)
                    Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = colors.onSurfaceMuted)
                    Spacer(Modifier.weight(1f))
                    FundPicker(
                        funds = funds,
                        selected = rule?.restFundId?.let(data::fund),
                        placeholder = stringResource(Res.string.allocate_choose_rest),
                        onSelect = { fund ->
                            ledger.update { current ->
                                if (rule == null) {
                                    current.copy(allocationRules = current.allocationRules + AllocationRule(selected, emptyList(), fund.id))
                                } else {
                                    current.updatingRule(selected) { it.copy(restFundId = fund.id) }
                                }
                            }
                        },
                    )
                }
            }
            if (rule == null) {
                Text(stringResource(Res.string.rule_need_rest), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
            } else {
                RuleExample(rule, data.transactions, data::fund)
            }
        }
    }
}

/** One line: reorder, fund ▾, method ▾, value, delete. */
@Composable
private fun RuleLineRow(
    line: RuleLine,
    first: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    funds: List<jp.cjbc.kakeibo.model.Fund>,
    fundOf: (FundId) -> jp.cjbc.kakeibo.model.Fund?,
    onChange: (RuleLine) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    // What is typed, kept even while it is not a valid value yet.
    var text by remember(line) {
        mutableStateOf(
            when (line) {
                is RuleLine.Fixed -> line.amount.takeIf { it > 0 }?.toString().orEmpty()
                is RuleLine.Percent -> line.percent.takeIf { it > 0 }?.toString().orEmpty()
                is RuleLine.Ask -> ""
            },
        )
    }
    val error = when (line) {
        is RuleLine.Fixed -> Res.string.rule_value_error.takeIf { line.amount <= 0 }
        is RuleLine.Percent -> Res.string.rule_percent_error.takeIf { line.percent !in 1..100 }
        is RuleLine.Ask -> null
    }
    ListRow(first = first) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FundPicker(funds, fundOf(line.fundId), "", { onChange(line.with(fundId = it.id)) }, Modifier.weight(1f, fill = false))
                val labels = Method.entries.associateWith { stringResource(it.label) }
                DropdownChip(
                    label = { Text(labels.getValue(line.method), style = AppTheme.type.label) },
                    options = Method.entries,
                    onSelect = { onChange(line.with(method = it)) },
                    item = { Text(labels.getValue(it)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line is RuleLine.Ask) {
                    Text("—", style = AppTheme.type.listAmount, color = colors.disabled, modifier = Modifier.width(120.dp))
                } else {
                    AmountField(
                        value = text,
                        label = labels(line),
                        onValueChange = { typed ->
                            text = typed.filter(Char::isDigit).take(if (line is RuleLine.Percent) 3 else 9)
                            val value = text.toLongOrNull() ?: 0
                            onChange(
                                when (line) {
                                    is RuleLine.Fixed -> line.copy(amount = value)
                                    is RuleLine.Percent -> line.copy(percent = value.toInt())
                                    is RuleLine.Ask -> line
                                },
                            )
                        },
                    )
                    if (line is RuleLine.Percent) Text(" %", style = AppTheme.type.body)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(AppIcons.MoveUp, stringResource(Res.string.sheet_move_up), Modifier.size(20.dp), tint = if (canMoveUp) colors.onSurface else colors.disabled)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(AppIcons.MoveDown, stringResource(Res.string.sheet_move_down), Modifier.size(20.dp), tint = if (canMoveDown) colors.onSurface else colors.disabled)
                }
                IconButton(onClick = onDelete) {
                    Icon(AppIcons.Delete, stringResource(Res.string.rule_delete_line), Modifier.size(20.dp), tint = colors.onSurfaceMuted)
                }
            }
            error?.let { FieldError(stringResource(it)) }
        }
    }
}

@Composable
private fun labels(line: RuleLine): String = methodLabel(line)

/**
 * "e.g. for 285,000, 83,100 goes to savings", using the category's latest income and what it put
 * into the asked lines' funds. Nothing shows before the first income.
 */
@Composable
private fun RuleExample(
    rule: AllocationRule,
    transactions: List<Transaction>,
    fundOf: (FundId) -> jp.cjbc.kakeibo.model.Fund?,
) {
    val colors = AppTheme.colors
    val latest = transactions.filterIsInstance<Transaction.Income>()
        .filter { it.categoryId == rule.incomeCategoryId }
        .maxByOrNull { it.date } ?: return
    val asked = rule.lines.withIndex().filter { it.value is RuleLine.Ask }.associate { (index, line) ->
        index to (latest.allocations.find { it.fundId == line.fundId }?.amount ?: 0)
    }
    val preview = previewAllocation(rule, latest.amount, asked)
    Text(
        stringResource(
            Res.string.rule_example,
            formatAmount(latest.amount),
            fundOf(rule.restFundId)?.name.orEmpty(),
            formatAmount(preview.rest),
        ),
        style = AppTheme.type.caption.merge(AppTheme.type.numeric).copy(fontWeight = FontWeight.Bold),
        color = colors.onInfoContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.infoContainer, AppShapes.Banner)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
