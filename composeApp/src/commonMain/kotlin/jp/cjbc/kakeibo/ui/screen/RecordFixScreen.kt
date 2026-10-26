package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.SavedFiles
import jp.cjbc.kakeibo.data.files.FieldPath
import jp.cjbc.kakeibo.data.files.FieldProblem
import jp.cjbc.kakeibo.data.files.FieldType
import jp.cjbc.kakeibo.data.files.InvalidField
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.RecordEdit
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecordsCheck
import jp.cjbc.kakeibo.data.files.ReferenceKind
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.UnfixableReason
import jp.cjbc.kakeibo.data.files.replaceReferences
import jp.cjbc.kakeibo.data.files.valueAt
import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_section
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.check_field_due_month
import jp.cjbc.kakeibo.resources.check_field_percent
import jp.cjbc.kakeibo.resources.check_field_start_month
import jp.cjbc.kakeibo.resources.check_intro
import jp.cjbc.kakeibo.resources.check_invalid_intro
import jp.cjbc.kakeibo.resources.check_invalid_title
import jp.cjbc.kakeibo.resources.check_keep_hidden
import jp.cjbc.kakeibo.resources.check_later
import jp.cjbc.kakeibo.resources.check_missing_expense_category
import jp.cjbc.kakeibo.resources.check_missing_fund
import jp.cjbc.kakeibo.resources.check_missing_income_category
import jp.cjbc.kakeibo.resources.check_missing_intro
import jp.cjbc.kakeibo.resources.check_missing_records
import jp.cjbc.kakeibo.resources.check_no_choices
import jp.cjbc.kakeibo.resources.check_problem_allocations
import jp.cjbc.kakeibo.resources.check_problem_choose
import jp.cjbc.kakeibo.resources.check_problem_date
import jp.cjbc.kakeibo.resources.check_problem_missing
import jp.cjbc.kakeibo.resources.check_problem_month
import jp.cjbc.kakeibo.resources.check_problem_negative
import jp.cjbc.kakeibo.resources.check_problem_not_positive
import jp.cjbc.kakeibo.resources.check_problem_number
import jp.cjbc.kakeibo.resources.check_problem_optional_month
import jp.cjbc.kakeibo.resources.check_problem_out_of_range
import jp.cjbc.kakeibo.resources.check_problem_text
import jp.cjbc.kakeibo.resources.check_reason_duplicate_id
import jp.cjbc.kakeibo.resources.check_reason_no_id
import jp.cjbc.kakeibo.resources.check_reason_not_json
import jp.cjbc.kakeibo.resources.check_reason_unknown_shape
import jp.cjbc.kakeibo.resources.check_reason_unknown_version
import jp.cjbc.kakeibo.resources.check_save
import jp.cjbc.kakeibo.resources.check_title
import jp.cjbc.kakeibo.resources.check_unfixable_intro
import jp.cjbc.kakeibo.resources.check_unfixable_title
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_memo_chip
import jp.cjbc.kakeibo.resources.fund_color
import jp.cjbc.kakeibo.resources.fund_goal
import jp.cjbc.kakeibo.resources.fund_name
import jp.cjbc.kakeibo.resources.fund_opening
import jp.cjbc.kakeibo.resources.fund_show_in_entry
import jp.cjbc.kakeibo.resources.history_fund
import jp.cjbc.kakeibo.resources.merge_field_archived
import jp.cjbc.kakeibo.resources.merge_field_budget_month
import jp.cjbc.kakeibo.resources.merge_field_date
import jp.cjbc.kakeibo.resources.merge_field_day
import jp.cjbc.kakeibo.resources.merge_field_last_applied
import jp.cjbc.kakeibo.resources.merge_field_order
import jp.cjbc.kakeibo.resources.merge_kind_recurring
import jp.cjbc.kakeibo.resources.merge_no
import jp.cjbc.kakeibo.resources.merge_other_hidden
import jp.cjbc.kakeibo.resources.merge_yes
import jp.cjbc.kakeibo.resources.method_rest
import jp.cjbc.kakeibo.resources.recurring_enabled
import jp.cjbc.kakeibo.resources.settings_category_fund
import jp.cjbc.kakeibo.resources.sheet_pinned
import jp.cjbc.kakeibo.resources.sync_close
import jp.cjbc.kakeibo.resources.sync_pull_again
import jp.cjbc.kakeibo.ui.PlatformBackHandler
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppChip
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The records that a sync left hidden, as [check] found them in [saved], while the user fixes them:
 * the values typed for their fields, and what to do with the records of each missing fund or
 * category. The [earlier] records name the funds and categories that are no longer there.
 */
@Stable
internal class RecordFix(val saved: SavedFiles, val check: RecordsCheck, val earlier: Map<RecordKey, SavedRecord>) {
    /** What the user typed for the fields, by record and path. */
    var inputs by mutableStateOf(emptyMap<RecordKey, Map<FieldPath, String>>())
        private set

    /**
     * The fields shown of each record, with their types: those found wrong at first, and those that
     * an edit finds wrong since. A field stays once shown, so that it does not go while typed in.
     */
    var fields by mutableStateOf(check.invalid.mapValues { (_, fields) -> fields.associate { it.path to it.type } })
        private set

    /**
     * What to do with the records of each missing fund or category, by its ID: the ID of the one to
     * move them to, or `null` to leave them as they are.
     */
    var replacements by mutableStateOf(emptyMap<String, String?>())
        private set

    fun type(key: RecordKey, path: FieldPath, input: String) {
        inputs = inputs + (key to inputs[key].orEmpty() + (path to input))
    }

    fun replace(id: String, replacement: String?) {
        replacements = replacements + (id to replacement)
    }

    /** Each record with fields shown, with what the user typed so far. */
    fun edits(): Map<RecordKey, RecordEdit> = fields.mapValues { (key, types) ->
        RecordEdit(key, check.records.getValue(key), inputs[key].orEmpty().mapValues { (path, input) -> types.getValue(path) to input })
    }

    /** Shows the fields that [edits] find wrong, besides the ones shown. */
    fun showProblems(edits: Map<RecordKey, RecordEdit>) {
        val more = edits.mapValues { (key, edit) -> edit.problems.values.filter { it.path !in fields.getValue(key) } }
        if (more.values.any { it.isNotEmpty() }) {
            fields = fields.mapValues { (key, types) -> types + more[key].orEmpty().associate { it.path to it.type } }
        }
    }
}

/**
 * The records of [fix], over the whole app as the merge is: the ones only the repository can fix,
 * with a button to take it in again; the records of each missing fund or category, to move to
 * another or leave; and the fields whose values the app does not take, each to edit by itself.
 */
@Composable
internal fun RecordFixDialog(runner: SyncRunner, fix: RecordFix) {
    val scope = rememberCoroutineScope()
    val names = remember(fix) { recordNames(fix.earlier.entries + fix.check.records.entries) }
    val edits = remember(fix.inputs, fix.fields) { fix.edits() }
    LaunchedEffect(edits) { fix.showProblems(edits) }
    val records = remember(edits) { fix.check.records + edits.mapValues { it.value.record } }
    // Reading all the records again takes a while, so only when the values taken change.
    val check = remember(records) { RecordsCheck(records) { key, _ -> key in fix.check.hidden } }
    val replacements = fix.replacements
    val fixable = fix.fields.isNotEmpty() || check.missing.isNotEmpty()

    PlatformBackHandler(enabled = true, onBack = runner::closeFix)
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            // Takes the touches that nothing in it handles, so that none reach the app below.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(stringResource(Res.string.check_title), onBack = null)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.check_intro), style = AppTheme.type.body)
            if (fix.check.unfixable.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.check_unfixable_title))
                Text(stringResource(Res.string.check_unfixable_intro), style = AppTheme.type.body)
                SectionCard(padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        fix.check.unfixable.forEach { (key, reason) -> UnfixableRow(key, fix.check.records.getValue(key), reason) }
                    }
                }
                ActionButton(
                    stringResource(Res.string.sync_pull_again),
                    onClick = { scope.launch { runner.pullAgainForFix() } },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (check.missing.isNotEmpty()) {
                Text(stringResource(Res.string.check_missing_intro), style = AppTheme.type.body)
                check.missing.forEach { (reference, keys) ->
                    val title = when (reference.kind) {
                        ReferenceKind.FUND -> Res.string.check_missing_fund
                        ReferenceKind.EXPENSE_CATEGORY -> Res.string.check_missing_expense_category
                        ReferenceKind.INCOME_CATEGORY -> Res.string.check_missing_income_category
                    }
                    MissingCard(
                        reference,
                        title = stringResource(title, names[reference.id] ?: reference.id),
                        count = stringResource(Res.string.check_missing_records, keys.size.toString()),
                        keepLabel = stringResource(Res.string.check_keep_hidden),
                        hidden = keys.map { recordView(it, records.getValue(it), names) },
                        candidates = check.candidates(reference.kind),
                        decided = reference.id in replacements,
                        replacement = replacements[reference.id],
                    ) { fix.replace(reference.id, it) }
                }
            }
            if (fix.fields.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.check_invalid_title))
                Text(stringResource(Res.string.check_invalid_intro), style = AppTheme.type.body)
                fix.fields.forEach { (key, types) ->
                    val original = fix.check.records.getValue(key)
                    val problems = edits.getValue(key).problems
                    val view = recordView(key, records.getValue(key), names)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeader("${view.kind} · ${view.title}")
                        SectionCard(padding = 12.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                types.forEach { (path, type) ->
                                    FieldEditor(
                                        label = fieldLabel(path),
                                        type = type,
                                        input = fix.inputs[key]?.get(path) ?: type.input(original.valueAt(path)),
                                        problem = problems[path],
                                        candidates = { kind -> check.candidates(kind) },
                                    ) { fix.type(key, path, it) }
                                }
                            }
                        }
                    }
                }
            }
            if (check.others.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.merge_other_hidden))
                RecordList(check.others.map { recordView(it, records.getValue(it), names) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = runner::closeFix) {
                Text(stringResource(if (fixable) Res.string.check_later else Res.string.sync_close))
            }
            if (fixable) {
                ActionButton(
                    stringResource(Res.string.check_save),
                    onClick = {
                        val missing = check.missing.keys.map { it.id }.toSet()
                        val moves = replacements.filterKeys { it in missing }.mapNotNull { (id, to) -> to?.let { id to it } }.toMap()
                        val fixed = replaceReferences(records, moves)
                        if (fixed == fix.check.records) runner.closeFix() else runner.saveFix(fixed)
                    },
                    enabled = check.missing.keys.all { it.id in replacements },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A record that only the repository can fix: where it is, what it says, and why it cannot be read. */
@Composable
private fun UnfixableRow(key: RecordKey, record: SavedRecord, reason: UnfixableReason) {
    val (place, raw) = when (record) {
        is SavedRecord.Master -> "$MASTER_FILE · ${key.list}" to record.element.toString()
        is SavedRecord.TransactionLine -> record.file to record.line
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(place, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        Text(raw, style = AppTheme.type.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(
                when (reason) {
                    UnfixableReason.NOT_JSON -> Res.string.check_reason_not_json
                    UnfixableReason.UNKNOWN_VERSION -> Res.string.check_reason_unknown_version
                    UnfixableReason.UNKNOWN_SHAPE -> Res.string.check_reason_unknown_shape
                    UnfixableReason.NO_ID -> Res.string.check_reason_no_id
                    UnfixableReason.DUPLICATE_ID -> Res.string.check_reason_duplicate_id
                },
            ),
            style = AppTheme.type.caption,
            color = AppTheme.colors.negative,
        )
    }
}

/**
 * The [input] of a field, named [label], of [type], with its [problem] if it has one. A fund or
 * category is picked among the [candidates] of its kind, a flag between yes and no.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldEditor(
    label: String,
    type: FieldType,
    input: String,
    problem: InvalidField?,
    candidates: (ReferenceKind) -> List<Pair<String, String>>,
    onInput: (String) -> Unit,
) {
    val error = problem?.let { problemText(it) }
    val kind = when (type) {
        FieldType.FUND -> ReferenceKind.FUND
        FieldType.EXPENSE_CATEGORY -> ReferenceKind.EXPENSE_CATEGORY
        FieldType.INCOME_CATEGORY -> ReferenceKind.INCOME_CATEGORY
        else -> null
    }
    val options = when {
        kind != null -> candidates(kind)
        type == FieldType.BOOLEAN -> listOf("true" to stringResource(Res.string.merge_yes), "false" to stringResource(Res.string.merge_no))
        else -> null
    }
    if (options == null) {
        LabeledTextField(input, label, onInput, error = error)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        if (options.isEmpty()) {
            Text(stringResource(Res.string.check_no_choices), style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (value, name) -> AppChip(name, selected = value == input, onClick = { onInput(value) }) }
            }
        }
        error?.let { FieldError(it) }
    }
}

/** What is wrong with [field], and how to make it right. */
@Composable
private fun problemText(field: InvalidField): String = when (field.problem) {
    FieldProblem.MISSING -> stringResource(Res.string.check_problem_missing)
    FieldProblem.WRONG_FORM -> stringResource(
        when (field.type) {
            FieldType.TEXT, FieldType.OPTIONAL_TEXT -> Res.string.check_problem_text
            FieldType.NUMBER, FieldType.INTEGER -> Res.string.check_problem_number
            FieldType.DATE -> Res.string.check_problem_date
            FieldType.MONTH -> Res.string.check_problem_month
            FieldType.OPTIONAL_MONTH -> Res.string.check_problem_optional_month
            FieldType.BOOLEAN, FieldType.FUND, FieldType.EXPENSE_CATEGORY, FieldType.INCOME_CATEGORY -> Res.string.check_problem_choose
        },
    )
    FieldProblem.NOT_POSITIVE -> stringResource(Res.string.check_problem_not_positive)
    FieldProblem.NEGATIVE -> stringResource(Res.string.check_problem_negative)
    FieldProblem.OUT_OF_RANGE ->
        stringResource(Res.string.check_problem_out_of_range, MIN_RECURRING_DAY.toString(), MAX_RECURRING_DAY.toString())
    FieldProblem.ALLOCATIONS_DO_NOT_ADD_UP -> stringResource(Res.string.check_problem_allocations)
}

/** The names of the fields as the user reads them, by their names in the files. */
private val FIELD_LABELS: Map<String, StringResource> = mapOf(
    "transaction_date" to Res.string.merge_field_date,
    "amount" to Res.string.amount,
    "category_id" to Res.string.entry_category,
    "income_category_id" to Res.string.entry_category,
    "memo" to Res.string.entry_memo_chip,
    "fund_id" to Res.string.history_fund,
    "budget_month" to Res.string.merge_field_budget_month,
    "allocations" to Res.string.allocate_section,
    "lines" to Res.string.allocate_section,
    "recurring_item_id" to Res.string.merge_kind_recurring,
    "name" to Res.string.fund_name,
    "color" to Res.string.fund_color,
    "opening_balance" to Res.string.fund_opening,
    "goal" to Res.string.fund_goal,
    "start_month" to Res.string.check_field_start_month,
    "due_month" to Res.string.check_field_due_month,
    "percent" to Res.string.check_field_percent,
    "show_in_entry" to Res.string.fund_show_in_entry,
    "sort_order" to Res.string.merge_field_order,
    "archived" to Res.string.merge_field_archived,
    "pinned" to Res.string.sheet_pinned,
    "default_fund_id" to Res.string.settings_category_fund,
    "rest_fund_id" to Res.string.method_rest,
    "day_of_month" to Res.string.merge_field_day,
    "enabled" to Res.string.recurring_enabled,
    "last_applied_month" to Res.string.merge_field_last_applied,
)

/** The field at [path] as the user reads it, e.g. "Allocation › 2 › Amount". */
@Composable
private fun fieldLabel(path: FieldPath): String = path.segments.map { segment ->
    segment.toIntOrNull()?.let { (it + 1).toString() } ?: FIELD_LABELS[segment]?.let { stringResource(it) } ?: segment
}.joinToString(" › ")
