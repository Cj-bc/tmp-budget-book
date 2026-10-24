package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.PendingMerge
import jp.cjbc.kakeibo.data.Side
import jp.cjbc.kakeibo.data.files.AllocationRuleRecord
import jp.cjbc.kakeibo.data.files.Conflict
import jp.cjbc.kakeibo.data.files.ExpenseCategoryRecord
import jp.cjbc.kakeibo.data.files.FundRecord
import jp.cjbc.kakeibo.data.files.GoalRecord
import jp.cjbc.kakeibo.data.files.IncomeCategoryRecord
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecurringItemRecord
import jp.cjbc.kakeibo.data.files.RuleLineRecord
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.TransactionRecord
import jp.cjbc.kakeibo.data.files.decodeRecord
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_section
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_memo_chip
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.fund_color
import jp.cjbc.kakeibo.resources.fund_goal
import jp.cjbc.kakeibo.resources.fund_goal_none
import jp.cjbc.kakeibo.resources.fund_name
import jp.cjbc.kakeibo.resources.fund_opening
import jp.cjbc.kakeibo.resources.fund_show_in_entry
import jp.cjbc.kakeibo.resources.funds_goal
import jp.cjbc.kakeibo.resources.history_fund
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.merge_choose_again
import jp.cjbc.kakeibo.resources.merge_confirm
import jp.cjbc.kakeibo.resources.merge_deleted
import jp.cjbc.kakeibo.resources.merge_field_archived
import jp.cjbc.kakeibo.resources.merge_field_budget_month
import jp.cjbc.kakeibo.resources.merge_field_date
import jp.cjbc.kakeibo.resources.merge_field_day
import jp.cjbc.kakeibo.resources.merge_field_last_applied
import jp.cjbc.kakeibo.resources.merge_field_order
import jp.cjbc.kakeibo.resources.merge_hides_confirm
import jp.cjbc.kakeibo.resources.merge_hides_intro
import jp.cjbc.kakeibo.resources.merge_hides_message
import jp.cjbc.kakeibo.resources.merge_hides_title
import jp.cjbc.kakeibo.resources.merge_intro
import jp.cjbc.kakeibo.resources.merge_kind_expense_category
import jp.cjbc.kakeibo.resources.merge_kind_fund
import jp.cjbc.kakeibo.resources.merge_kind_income_category
import jp.cjbc.kakeibo.resources.merge_kind_recurring
import jp.cjbc.kakeibo.resources.merge_kind_rule
import jp.cjbc.kakeibo.resources.merge_no
import jp.cjbc.kakeibo.resources.merge_ours
import jp.cjbc.kakeibo.resources.merge_take_in
import jp.cjbc.kakeibo.resources.merge_theirs
import jp.cjbc.kakeibo.resources.merge_title
import jp.cjbc.kakeibo.resources.merge_unreadable
import jp.cjbc.kakeibo.resources.merge_yes
import jp.cjbc.kakeibo.resources.method_ask
import jp.cjbc.kakeibo.resources.method_fixed
import jp.cjbc.kakeibo.resources.method_percent_of
import jp.cjbc.kakeibo.resources.method_rest
import jp.cjbc.kakeibo.resources.recurring_enabled
import jp.cjbc.kakeibo.resources.recurring_name_label
import jp.cjbc.kakeibo.resources.settings_category_fund
import jp.cjbc.kakeibo.resources.sheet_pinned
import jp.cjbc.kakeibo.ui.PlatformBackHandler
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** A record as the user reads it: what [kind] it is, a [title] naming it, and its [fields]. */
private class RecordView(val kind: String, val title: String, val fields: List<Pair<String, String>>)

/**
 * The merge that [runner] stopped at, over the whole app so that nothing is edited meanwhile:
 * a choice of version for each conflicting record, or the records the merge would hide.
 */
@Composable
internal fun MergeDialog(runner: SyncRunner, merge: PendingMerge) {
    var hidden by remember(merge) { mutableStateOf<List<RecordView>?>(null) }
    val names = remember(merge) { recordNames(merge.ours.entries + merge.theirs.entries) }
    val choices = runner.choices
    val allChosen = merge.conflicts.all { it in choices }
    // Reading all the records again takes a while, so only when the choices change.
    val hiddenRecords = remember(merge, choices) {
        if (allChosen) merge.hiddenBy(choices.mapKeys { it.key.key }) else emptyMap()
    }
    val hiddenViews = hiddenRecords.map { (key, record) -> recordView(key, record, names) }

    PlatformBackHandler(enabled = true, onBack = runner::closeMerge)
    // Drawn over the app in its window rather than in a dialog's own, which Android 15 draws
    // under the system bars, so that the button at the bottom stays clear of them.
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            // Takes the touches that nothing in it handles, so that none reach the app below.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(stringResource(Res.string.merge_title), runner::closeMerge, stringResource(Res.string.back))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (merge.conflicts.isEmpty()) {
                Text(stringResource(Res.string.merge_hides_intro), style = AppTheme.type.body)
                RecordList(hiddenViews)
            } else {
                Text(stringResource(Res.string.merge_intro), style = AppTheme.type.body)
                merge.conflicts.forEach { conflict ->
                    ConflictCard(conflict, names, choices[conflict]) { runner.choose(conflict, it) }
                }
            }
        }
        val confirm = stringResource(if (merge.conflicts.isEmpty()) Res.string.merge_take_in else Res.string.merge_confirm)
        ActionButton(
            confirm,
            onClick = {
                // Records that the merge hides were listed already when nothing conflicts.
                if (merge.conflicts.isNotEmpty() && hiddenViews.isNotEmpty()) {
                    hidden = hiddenViews
                } else {
                    runner.resolveMerge()
                }
            },
            enabled = allChosen,
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.Screen),
        )
    }

    hidden?.let { views ->
        AlertDialog(
            onDismissRequest = { hidden = null },
            title = { Text(stringResource(Res.string.merge_hides_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.merge_hides_message, views.size.toString()), style = AppTheme.type.body)
                    views.forEach { Text("${it.kind}: ${it.title}", style = AppTheme.type.label) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    hidden = null
                    runner.resolveMerge()
                }) { Text(stringResource(Res.string.merge_hides_confirm), color = AppTheme.colors.negative) }
            },
            dismissButton = { TextButton(onClick = { hidden = null }) { Text(stringResource(Res.string.merge_choose_again)) } },
        )
    }
}

/** The records of [views], each with its kind and title. */
@Composable
private fun RecordList(views: List<RecordView>) {
    SectionCard {
        views.forEach { view ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(view.kind, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
                Text(view.title, style = AppTheme.type.body)
            }
        }
    }
}

/** The two versions of a conflicting record, of which the user picks one. */
@Composable
private fun ConflictCard(conflict: Conflict<RecordKey, SavedRecord>, names: Map<String, String>, chosen: Side?, onChoose: (Side) -> Unit) {
    val ours = conflict.ours?.let { recordView(conflict.key, it, names) }
    val theirs = conflict.theirs?.let { recordView(conflict.key, it, names) }
    val any = checkNotNull(ours ?: theirs)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("${any.kind} · ${any.title}")
        SectionCard(padding = 8.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionOption(stringResource(Res.string.merge_ours), ours, theirs, chosen == Side.OURS) { onChoose(Side.OURS) }
                VersionOption(stringResource(Res.string.merge_theirs), theirs, ours, chosen == Side.THEIRS) { onChoose(Side.THEIRS) }
            }
        }
    }
}

/**
 * One side's [version] of a record, `null` if it deleted it. The fields that differ from the
 * [other] version, unless it was deleted, are in bold.
 */
@Composable
private fun VersionOption(label: String, version: RecordView?, other: RecordView?, selected: Boolean, onSelect: () -> Unit) {
    val colors = AppTheme.colors
    val otherFields = other?.fields.orEmpty().toSet()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Tile)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onSurface else colors.outline, AppShapes.Tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, Modifier.padding(12.dp), colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = AppTheme.type.bodyBold)
            if (version == null) {
                Text(stringResource(Res.string.merge_deleted), style = AppTheme.type.label, color = colors.negative)
            } else {
                version.fields.forEach { field ->
                    val differs = other != null && field !in otherFields
                    Text(
                        if (field.first.isEmpty()) field.second else "${field.first}: ${field.second}",
                        style = if (differs) AppTheme.type.labelBold else AppTheme.type.label,
                        color = if (differs || other == null) colors.onSurface else colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

/** The names of the funds, categories and recurring items among [records], by ID. */
private fun recordNames(records: Collection<Map.Entry<RecordKey, SavedRecord>>): Map<String, String> =
    records.mapNotNull { (key, record) ->
        when (val decoded = decodeRecord(key, record)) {
            is FundRecord -> decoded.id to decoded.name
            is ExpenseCategoryRecord -> decoded.id to decoded.name
            is IncomeCategoryRecord -> decoded.id to decoded.name
            is RecurringItemRecord -> decoded.id to decoded.name
            else -> null
        }
    }.toMap()

private fun transactionTitle(date: LocalDate, category: String, amount: Long, memo: String) =
    listOf(date.toString(), category, formatAmount(amount), memo).filter { it.isNotBlank() }.joinToString(" ")

/** [record] as the user reads it, naming the records it refers to by [names]. */
@Composable
private fun recordView(key: RecordKey, record: SavedRecord, names: Map<String, String>): RecordView {
    fun name(id: String) = names[id] ?: id
    val yes = stringResource(Res.string.merge_yes)
    val no = stringResource(Res.string.merge_no)
    fun flag(value: Boolean) = if (value) yes else no
    val order = stringResource(Res.string.merge_field_order)
    val archived = stringResource(Res.string.merge_field_archived)
    val pinned = stringResource(Res.string.sheet_pinned)
    val category = stringResource(Res.string.entry_category)
    val fund = stringResource(Res.string.history_fund)
    val amount = stringResource(Res.string.amount)
    return when (val decoded = decodeRecord(key, record)) {
        is TransactionRecord.Expense -> RecordView(
            stringResource(Res.string.expense),
            transactionTitle(decoded.transactionDate, name(decoded.categoryId), decoded.amount, decoded.memo),
            listOf(
                stringResource(Res.string.merge_field_date) to decoded.transactionDate.toString(),
                amount to formatAmount(decoded.amount),
                category to name(decoded.categoryId),
                fund to name(decoded.fundId),
                stringResource(Res.string.entry_memo_chip) to decoded.memo,
            ),
        )
        is TransactionRecord.Income -> RecordView(
            stringResource(Res.string.income),
            transactionTitle(decoded.transactionDate, name(decoded.categoryId), decoded.amount, decoded.memo),
            listOf(
                stringResource(Res.string.merge_field_date) to decoded.transactionDate.toString(),
                amount to formatAmount(decoded.amount),
                category to name(decoded.categoryId),
                stringResource(Res.string.merge_field_budget_month) to formatYearMonth(decoded.budgetMonth),
                stringResource(Res.string.allocate_section) to
                    decoded.allocations.joinToString { "${name(it.fundId)} ${formatAmount(it.amount)}" },
                stringResource(Res.string.entry_memo_chip) to decoded.memo,
            ),
        )
        is FundRecord -> RecordView(
            stringResource(Res.string.merge_kind_fund),
            decoded.name,
            listOf(
                stringResource(Res.string.fund_name) to decoded.name,
                stringResource(Res.string.fund_color) to "#" + (decoded.color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(),
                stringResource(Res.string.fund_opening) to formatAmount(decoded.openingBalance),
                stringResource(Res.string.fund_goal) to when (val goal = decoded.goal) {
                    GoalRecord.None -> stringResource(Res.string.fund_goal_none)
                    is GoalRecord.Target -> stringResource(Res.string.funds_goal, formatAmount(goal.amount), formatYearMonth(goal.dueMonth)) +
                        " (${formatYearMonth(goal.startMonth)}–)"
                },
                stringResource(Res.string.fund_show_in_entry) to flag(decoded.showInEntry),
                order to decoded.sortOrder.toString(),
                archived to flag(decoded.archived),
            ),
        )
        is ExpenseCategoryRecord -> RecordView(
            stringResource(Res.string.merge_kind_expense_category),
            decoded.name,
            listOf(
                stringResource(Res.string.fund_name) to decoded.name,
                stringResource(Res.string.settings_category_fund) to name(decoded.defaultFundId),
                pinned to flag(decoded.pinned),
                order to decoded.sortOrder.toString(),
                archived to flag(decoded.archived),
            ),
        )
        is IncomeCategoryRecord -> RecordView(
            stringResource(Res.string.merge_kind_income_category),
            decoded.name,
            listOf(
                stringResource(Res.string.fund_name) to decoded.name,
                pinned to flag(decoded.pinned),
                order to decoded.sortOrder.toString(),
                archived to flag(decoded.archived),
            ),
        )
        is AllocationRuleRecord -> RecordView(
            stringResource(Res.string.merge_kind_rule),
            name(decoded.incomeCategoryId),
            listOf(
                category to name(decoded.incomeCategoryId),
                stringResource(Res.string.allocate_section) to decoded.lines.map { line ->
                    when (line) {
                        is RuleLineRecord.Fixed -> "${name(line.fundId)} ${stringResource(Res.string.method_fixed)} ${formatAmount(line.amount)}"
                        is RuleLineRecord.Percent -> "${name(line.fundId)} ${stringResource(Res.string.method_percent_of, "${line.percent}%")}"
                        is RuleLineRecord.Ask -> "${name(line.fundId)} ${stringResource(Res.string.method_ask)}"
                    }
                }.joinToString(),
                stringResource(Res.string.method_rest) to name(decoded.restFundId),
            ),
        )
        is RecurringItemRecord -> RecordView(
            stringResource(Res.string.merge_kind_recurring),
            decoded.name,
            listOf(
                stringResource(Res.string.recurring_name_label) to decoded.name,
                amount to formatAmount(decoded.amount),
                category to name(decoded.categoryId),
                fund to name(decoded.fundId),
                stringResource(Res.string.merge_field_day) to decoded.dayOfMonth.toString(),
                stringResource(Res.string.recurring_enabled) to flag(decoded.enabled),
                stringResource(Res.string.merge_field_last_applied) to (decoded.lastAppliedMonth?.let { formatYearMonth(it) } ?: "–"),
            ),
        )
        else -> {
            val raw = when (record) {
                is SavedRecord.Master -> record.element.toString()
                is SavedRecord.TransactionLine -> record.line
            }
            RecordView(stringResource(Res.string.merge_unreadable), key.id.removePrefix("content:").take(40), listOf("" to raw))
        }
    }
}
