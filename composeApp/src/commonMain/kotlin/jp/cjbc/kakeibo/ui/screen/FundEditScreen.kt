package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.FUND_PALETTE
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundColor
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.model.defaultShowInEntry
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.nextPaletteColor
import jp.cjbc.kakeibo.model.nextSortOrder
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.entry_save
import jp.cjbc.kakeibo.resources.fund_archive
import jp.cjbc.kakeibo.resources.fund_archived
import jp.cjbc.kakeibo.resources.fund_color
import jp.cjbc.kakeibo.resources.fund_due_error
import jp.cjbc.kakeibo.resources.fund_edit
import jp.cjbc.kakeibo.resources.fund_goal
import jp.cjbc.kakeibo.resources.fund_goal_amount
import jp.cjbc.kakeibo.resources.fund_goal_amount_error
import jp.cjbc.kakeibo.resources.fund_goal_due
import jp.cjbc.kakeibo.resources.fund_goal_none
import jp.cjbc.kakeibo.resources.fund_goal_start
import jp.cjbc.kakeibo.resources.fund_goal_target
import jp.cjbc.kakeibo.resources.fund_month_error
import jp.cjbc.kakeibo.resources.fund_name
import jp.cjbc.kakeibo.resources.fund_name_error
import jp.cjbc.kakeibo.resources.fund_new_title
import jp.cjbc.kakeibo.resources.fund_opening
import jp.cjbc.kakeibo.resources.fund_opening_negative
import jp.cjbc.kakeibo.resources.fund_reassign
import jp.cjbc.kakeibo.resources.fund_reassign_to
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.Segmented
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Adds a fund (route without an ID) or edits one: name, color, opening balance and goal, and
 * archiving, which first moves the categories using it as their default to another fund.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FundEditScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    route: Route.FundEdit,
) {
    val data = ledger.data
    val colors = AppTheme.colors
    val categoryNames = categoryNames()
    val scope = rememberCoroutineScope()
    val existing = route.id?.let(data::fund)
    val target = existing?.goal as? FundGoal.Target
    var name by remember(route) { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember(route) { mutableStateOf(existing?.color ?: nextPaletteColor(data.funds)) }
    var opening by remember(route) { mutableStateOf(existing?.openingBalance?.takeIf { it != 0L }?.let { kotlin.math.abs(it).toString() }.orEmpty()) }
    var negative by remember(route) { mutableStateOf((existing?.openingBalance ?: 0) < 0) }
    var hasGoal by remember(route) { mutableStateOf(target != null) }
    var goalAmount by remember(route) { mutableStateOf(target?.amount?.toString().orEmpty()) }
    var start by remember(route) { mutableStateOf((target?.startMonth ?: currentMonth()).toString()) }
    var due by remember(route) { mutableStateOf((target?.dueMonth ?: currentMonth().plus(11, DateTimeUnit.MONTH)).toString()) }
    var reassignTo by remember(route) { mutableStateOf<FundId?>(null) }
    var showErrors by remember(route) { mutableStateOf(false) }

    val startMonth = YearMonth.Formats.ISO.parseOrNull(start.trim())
    val dueMonth = YearMonth.Formats.ISO.parseOrNull(due.trim())
    val amount = goalAmount.toLongOrNull()?.takeIf { it > 0 }
    val goalErrors = hasGoal && (amount == null || startMonth == null || dueMonth == null || dueMonth < startMonth)

    fun save() {
        if (name.isBlank() || goalErrors) {
            showErrors = true
            return
        }
        val goal = if (hasGoal) FundGoal.Target(amount!!, startMonth!!, dueMonth!!) else FundGoal.None
        val balance = (opening.toLongOrNull() ?: 0).let { if (negative) -it else it }
        ledger.update { current ->
            if (existing == null) {
                current.copy(
                    funds = current.funds + Fund(
                        id = FundId(generateId()),
                        name = name.trim(),
                        color = color,
                        openingBalance = balance,
                        goal = goal,
                        showInEntry = defaultShowInEntry(goal),
                        sortOrder = nextSortOrder(current.funds.map { it.sortOrder }),
                        archived = false,
                    ),
                )
            } else {
                current.copy(
                    funds = current.funds.map {
                        if (it.id == existing.id) it.copy(name = name.trim(), color = color, openingBalance = balance, goal = goal) else it
                    },
                )
            }
        }
        navigator.back()
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            stringResource(if (existing == null) Res.string.fund_new_title else Res.string.fund_edit),
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
                    stringResource(Res.string.fund_name),
                    { name = it },
                    Modifier.fillMaxWidth(),
                    error = stringResource(Res.string.fund_name_error).takeIf { showErrors && name.isBlank() },
                )
                Text(stringResource(Res.string.fund_color), style = AppTheme.type.sectionHeader, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FUND_PALETTE.forEach { option -> ColorSwatch(option, option == color) { color = option } }
                }
                LabeledTextField(
                    opening,
                    stringResource(Res.string.fund_opening),
                    { opening = it.filter(Char::isDigit).take(9) },
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.fund_opening_negative), style = AppTheme.type.body, modifier = Modifier.weight(1f))
                    Switch(negative, { negative = it })
                }
            }
            SectionHeader(stringResource(Res.string.fund_goal))
            SectionCard {
                val labels = mapOf(false to stringResource(Res.string.fund_goal_none), true to stringResource(Res.string.fund_goal_target))
                Segmented(listOf(false, true), hasGoal, { hasGoal = it }) { labels.getValue(it) }
                if (hasGoal) {
                    LabeledTextField(
                        goalAmount,
                        stringResource(Res.string.fund_goal_amount),
                        { goalAmount = it.filter(Char::isDigit).take(9) },
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        error = stringResource(Res.string.fund_goal_amount_error).takeIf { showErrors && amount == null },
                    )
                    LabeledTextField(
                        start,
                        stringResource(Res.string.fund_goal_start),
                        { start = it },
                        Modifier.fillMaxWidth(),
                        error = stringResource(Res.string.fund_month_error).takeIf { showErrors && startMonth == null },
                    )
                    LabeledTextField(
                        due,
                        stringResource(Res.string.fund_goal_due),
                        { due = it },
                        Modifier.fillMaxWidth(),
                        error = when {
                            !showErrors -> null
                            dueMonth == null -> stringResource(Res.string.fund_month_error)
                            startMonth != null && dueMonth < startMonth -> stringResource(Res.string.fund_due_error)
                            else -> null
                        },
                    )
                }
            }
            ActionButton(stringResource(Res.string.entry_save), ::save, modifier = Modifier.fillMaxWidth().height(52.dp))
            if (existing != null && !existing.archived) {
                ArchiveSection(
                    fund = existing,
                    dependents = data.categories.filterIsInstance<Category.Expense>()
                        .filter { it.defaultFundId == existing.id && !it.archived },
                    others = data.activeFunds.filter { it.id != existing.id },
                    reassignTo = reassignTo,
                    onReassign = { reassignTo = it },
                    categoryName = { categoryNames.display(it.id) },
                    onArchive = { dependents ->
                        ledger.update { current ->
                            current.copy(
                                funds = current.funds.map { if (it.id == existing.id) it.copy(archived = true) else it },
                                categories = current.categories.map { category ->
                                    if (category is Category.Expense && category in dependents) {
                                        category.copy(defaultFundId = requireNotNull(reassignTo))
                                    } else {
                                        category
                                    }
                                },
                            )
                        }
                        // The edit screen and the archived fund's details.
                        navigator.back(2)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                getString(Res.string.fund_archived, existing.name),
                                actionLabel = getString(Res.string.undo),
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                ledger.update { current ->
                                    current.copy(funds = current.funds.map { if (it.id == existing.id) it.copy(archived = false) else it })
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Archiving; when categories use the fund as their default, they must move to another first. */
@Composable
private fun ArchiveSection(
    fund: Fund,
    dependents: List<Category.Expense>,
    others: List<Fund>,
    reassignTo: FundId?,
    onReassign: (FundId) -> Unit,
    categoryName: (Category) -> String,
    onArchive: (List<Category.Expense>) -> Unit,
) {
    val colors = AppTheme.colors
    SectionCard {
        if (dependents.isNotEmpty()) {
            Text(
                stringResource(Res.string.fund_reassign, dependents.joinToString("、") { categoryName(it) }),
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
            )
            Text(stringResource(Res.string.fund_reassign_to), style = AppTheme.type.sectionHeader, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                others.forEach { FundChip(it, it.id == reassignTo, { onReassign(it.id) }) }
            }
        }
        ActionButton(
            stringResource(Res.string.fund_archive),
            { onArchive(dependents) },
            isPrimary = false,
            enabled = dependents.isEmpty() || reassignTo != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** A round swatch of a palette color, ringed when selected. */
@Composable
private fun ColorSwatch(color: FundColor, selected: Boolean, onClick: () -> Unit) {
    val ring = AppTheme.colors.onSurface
    val description = "#" + (color.argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
    Box(
        Modifier
            .size(44.dp)
            .border(if (selected) 2.dp else 0.dp, if (selected) ring else Color.Transparent, CircleShape)
            .padding(5.dp)
            .background(Color(color.argb), CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    )
}
