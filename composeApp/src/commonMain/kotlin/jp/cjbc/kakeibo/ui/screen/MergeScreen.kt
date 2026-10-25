package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import jp.cjbc.kakeibo.data.files.Reference
import jp.cjbc.kakeibo.data.files.ReferenceKind
import jp.cjbc.kakeibo.data.files.RecurringItemRecord
import jp.cjbc.kakeibo.data.files.RuleLineRecord
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.TransactionRecord
import jp.cjbc.kakeibo.data.files.decodeRecord
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_section
import jp.cjbc.kakeibo.resources.amount
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
import jp.cjbc.kakeibo.resources.merge_cancel
import jp.cjbc.kakeibo.resources.merge_confirm
import jp.cjbc.kakeibo.resources.merge_deleted
import jp.cjbc.kakeibo.resources.merge_field_archived
import jp.cjbc.kakeibo.resources.merge_field_budget_month
import jp.cjbc.kakeibo.resources.merge_field_date
import jp.cjbc.kakeibo.resources.merge_field_day
import jp.cjbc.kakeibo.resources.merge_field_last_applied
import jp.cjbc.kakeibo.resources.merge_field_order
import jp.cjbc.kakeibo.resources.merge_intro
import jp.cjbc.kakeibo.resources.merge_kind_expense_category
import jp.cjbc.kakeibo.resources.merge_kind_fund
import jp.cjbc.kakeibo.resources.merge_kind_income_category
import jp.cjbc.kakeibo.resources.merge_kind_recurring
import jp.cjbc.kakeibo.resources.merge_kind_rule
import jp.cjbc.kakeibo.resources.merge_keep_hidden
import jp.cjbc.kakeibo.resources.merge_missing_expense_category
import jp.cjbc.kakeibo.resources.merge_missing_fund
import jp.cjbc.kakeibo.resources.merge_missing_income_category
import jp.cjbc.kakeibo.resources.merge_missing_intro
import jp.cjbc.kakeibo.resources.merge_missing_records
import jp.cjbc.kakeibo.resources.merge_move_category
import jp.cjbc.kakeibo.resources.merge_move_fund
import jp.cjbc.kakeibo.resources.merge_no_candidates
import jp.cjbc.kakeibo.resources.merge_other_hidden
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
import jp.cjbc.kakeibo.ui.component.AppChip
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
internal class RecordView(val kind: String, val title: String, val fields: List<Pair<String, String>>)

/**
 * The merge that [runner] stopped at, over the whole app so that nothing is edited meanwhile:
 * a choice of version for each conflicting record, then, for each fund or category that one side
 * deleted and the other's records refer to, whether to move those records to another.
 */
@Composable
internal fun MergeDialog(runner: SyncRunner, merge: PendingMerge) {
    val names = remember(merge) { recordNames(merge.ours.entries + merge.theirs.entries) }
    val choices = runner.choices
    val allChosen = merge.conflicts.all { it in choices }
    // Reading all the records again takes a while, so only when the choices change.
    val check = remember(merge, choices) { if (allChosen) MergeCheck(merge, choices.mapKeys { it.key.key }) else null }
    val replacements = runner.replacements
    val allDecided = check != null && check.missing.keys.all { it.id in replacements }

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
        // No back arrow: "Stop merging" says what leaving does. The system back does the same.
        AppTopBar(stringResource(Res.string.merge_title), onBack = null)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (merge.conflicts.isNotEmpty()) {
                Text(stringResource(Res.string.merge_intro), style = AppTheme.type.body)
                merge.conflicts.forEach { conflict ->
                    ConflictCard(conflict, names, choices[conflict]) { runner.choose(conflict, it) }
                }
            }
            if (check != null && check.missing.isNotEmpty()) {
                Text(stringResource(Res.string.merge_missing_intro), style = AppTheme.type.body)
                check.missing.forEach { (reference, keys) ->
                    MissingCard(
                        reference,
                        name = names[reference.id] ?: reference.id,
                        hidden = keys.map { recordView(it, check.resolved.getValue(it), names) },
                        candidates = check.candidates(reference.kind),
                        decided = reference.id in replacements,
                        replacement = replacements[reference.id],
                    ) { runner.replace(reference.id, it) }
                }
            }
            if (check != null && check.others.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.merge_other_hidden))
                RecordList(check.others.map { (key, record) -> recordView(key, record, names) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = runner::closeMerge) { Text(stringResource(Res.string.merge_cancel)) }
            ActionButton(
                stringResource(if (merge.conflicts.isEmpty()) Res.string.merge_take_in else Res.string.merge_confirm),
                onClick = runner::resolveMerge,
                enabled = allDecided,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * What resolving [merge] by [choices] leaves to settle: the [missing] funds and categories with
 * the records they hide, and the [others] that it hides for other reasons.
 */
private class MergeCheck(merge: PendingMerge, choices: Map<RecordKey, Side>) {
    val resolved = merge.resolve(choices)
    val missing: Map<Reference, List<RecordKey>> = merge.missingBy(choices)
    val others: Map<RecordKey, SavedRecord> = merge.hiddenBy(choices) - missing.values.flatten().toSet()

    /** The funds or categories of [kind] that the records can be moved to, by ID, with their names. */
    fun candidates(kind: ReferenceKind): List<Pair<String, String>> = resolved
        .filterKeys { it.list == kind.list && it !in others && missing.values.none { keys -> it in keys } }
        .mapNotNull { (key, record) ->
            when (val decoded = decodeRecord(key, record)) {
                is FundRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
                is ExpenseCategoryRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
                is IncomeCategoryRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
                else -> null
            }
        }
        .sortedBy { it.first }
        .map { it.second to it.third }
}

/** The records of [views], each with its kind and title. */
@Composable
internal fun RecordList(views: List<RecordView>) {
    SectionCard {
        views.forEach { view ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(view.kind, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
                Text(view.title, style = AppTheme.type.body)
            }
        }
    }
}

/**
 * A fund or category, named [name], that one side deleted, with the records that it [hidden]: the
 * user moves them to one of the [candidates], or goes on without. [replacement] is the ID moved
 * to, `null` when going on without or not [decided] yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissingCard(
    reference: Reference,
    name: String,
    hidden: List<RecordView>,
    candidates: List<Pair<String, String>>,
    decided: Boolean,
    replacement: String?,
    onDecide: (String?) -> Unit,
) {
    val colors = AppTheme.colors
    val title = when (reference.kind) {
        ReferenceKind.FUND -> Res.string.merge_missing_fund
        ReferenceKind.EXPENSE_CATEGORY -> Res.string.merge_missing_expense_category
        ReferenceKind.INCOME_CATEGORY -> Res.string.merge_missing_income_category
    }
    val moveLabel = if (reference.kind == ReferenceKind.FUND) Res.string.merge_move_fund else Res.string.merge_move_category
    val moving = decided && replacement != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(title, name))
        SectionCard(padding = 8.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.merge_missing_records, hidden.size.toString()), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                    hidden.forEach { Text("${it.kind}: ${it.title}", style = AppTheme.type.label) }
                }
                if (candidates.isEmpty()) {
                    Text(stringResource(Res.string.merge_no_candidates), style = AppTheme.type.label, color = colors.onSurfaceMuted, modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                    // Moving them picks the first candidate, until the user picks another.
                    OptionRow(stringResource(moveLabel), selected = moving) { onDecide(replacement ?: candidates.first().first) }
                    if (moving) {
                        FlowRow(
                            Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            candidates.forEach { (id, label) -> AppChip(label, selected = id == replacement, onClick = { onDecide(id) }) }
                        }
                    }
                }
                OptionRow(stringResource(Res.string.merge_keep_hidden), selected = decided && replacement == null) { onDecide(null) }
            }
        }
    }
}

/** One of the options of a [MissingCard]. */
@Composable
internal fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Tile)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onSurface else colors.outline, AppShapes.Tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, Modifier.padding(12.dp), colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface))
        Text(label, style = AppTheme.type.body)
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
                        when {
                            field.first.isEmpty() -> field.second
                            // A list goes on lines of its own, under its label.
                            field.second.startsWith(LIST_ITEM) -> "${field.first}:\n${field.second.prependIndent("  ")}"
                            else -> "${field.first}: ${field.second}"
                        },
                        style = if (differs) AppTheme.type.labelBold else AppTheme.type.label,
                        color = if (differs || other == null) colors.onSurface else colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

/** The names of the funds, categories and recurring items among [records], by ID. */
internal fun recordNames(records: Collection<Map.Entry<RecordKey, SavedRecord>>): Map<String, String> =
    records.mapNotNull { (key, record) ->
        when (val decoded = decodeRecord(key, record)) {
            is FundRecord -> decoded.id to decoded.name
            is ExpenseCategoryRecord -> decoded.id to decoded.name
            is IncomeCategoryRecord -> decoded.id to decoded.name
            is RecurringItemRecord -> decoded.id to decoded.name
            else -> null
        }
    }.toMap()

private const val LIST_ITEM = "- "

/** [items] as the value of a field, one under another, since a list joined by commas mixes with the commas of amounts. */
private fun listItems(items: List<String>) = items.joinToString("\n") { LIST_ITEM + it }

private fun transactionTitle(date: LocalDate, category: String, amount: Long, memo: String) =
    listOf(date.toString(), category, formatAmount(amount), memo).filter { it.isNotBlank() }.joinToString(" ")

/** [record] as the user reads it, naming the records it refers to by [names]. */
@Composable
internal fun recordView(key: RecordKey, record: SavedRecord, names: Map<String, String>): RecordView {
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
                    listItems(decoded.allocations.map { "${name(it.fundId)} ${formatAmount(it.amount)}" }),
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
                stringResource(Res.string.allocate_section) to listItems(decoded.lines.map { line ->
                    when (line) {
                        is RuleLineRecord.Fixed -> "${name(line.fundId)} ${stringResource(Res.string.method_fixed)} ${formatAmount(line.amount)}"
                        is RuleLineRecord.Percent -> "${name(line.fundId)} ${stringResource(Res.string.method_percent_of, "${line.percent}%")}"
                        is RuleLineRecord.Ask -> "${name(line.fundId)} ${stringResource(Res.string.method_ask)}"
                    }
                }),
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
