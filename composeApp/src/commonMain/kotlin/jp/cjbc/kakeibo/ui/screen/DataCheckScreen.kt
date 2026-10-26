package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.DataCheck
import jp.cjbc.kakeibo.data.files.FieldIssue
import jp.cjbc.kakeibo.data.files.FieldKind
import jp.cjbc.kakeibo.data.files.FieldProblem
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecordTrouble
import jp.cjbc.kakeibo.data.files.ReferenceKind
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.TRANSACTIONS_LIST
import jp.cjbc.kakeibo.data.files.fieldText
import jp.cjbc.kakeibo.data.files.parse
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.check_allocation_amount
import jp.cjbc.kakeibo.resources.check_close
import jp.cjbc.kakeibo.resources.check_dependent
import jp.cjbc.kakeibo.resources.check_duplicate_id
import jp.cjbc.kakeibo.resources.check_fields_intro
import jp.cjbc.kakeibo.resources.check_intro
import jp.cjbc.kakeibo.resources.check_issue_allocations
import jp.cjbc.kakeibo.resources.check_issue_day
import jp.cjbc.kakeibo.resources.check_issue_missing
import jp.cjbc.kakeibo.resources.check_issue_negative
import jp.cjbc.kakeibo.resources.check_issue_not_positive
import jp.cjbc.kakeibo.resources.check_issue_wrong_date
import jp.cjbc.kakeibo.resources.check_issue_wrong_month
import jp.cjbc.kakeibo.resources.check_issue_wrong_number
import jp.cjbc.kakeibo.resources.check_issue_wrong_optional_month
import jp.cjbc.kakeibo.resources.check_issue_wrong_text
import jp.cjbc.kakeibo.resources.check_issue_wrong_yes_no
import jp.cjbc.kakeibo.resources.check_malformed
import jp.cjbc.kakeibo.resources.check_missing_intro
import jp.cjbc.kakeibo.resources.check_pull_again
import jp.cjbc.kakeibo.resources.check_rule_line_amount
import jp.cjbc.kakeibo.resources.check_rule_line_percent
import jp.cjbc.kakeibo.resources.check_save
import jp.cjbc.kakeibo.resources.check_still_hidden
import jp.cjbc.kakeibo.resources.check_title
import jp.cjbc.kakeibo.resources.check_unfixable_intro
import jp.cjbc.kakeibo.resources.check_unreadable
import jp.cjbc.kakeibo.resources.entry_memo_chip
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.fund_color
import jp.cjbc.kakeibo.resources.fund_goal_amount
import jp.cjbc.kakeibo.resources.fund_goal_due
import jp.cjbc.kakeibo.resources.fund_goal_start
import jp.cjbc.kakeibo.resources.fund_name
import jp.cjbc.kakeibo.resources.fund_opening
import jp.cjbc.kakeibo.resources.fund_show_in_entry
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.merge_field_archived
import jp.cjbc.kakeibo.resources.merge_field_budget_month
import jp.cjbc.kakeibo.resources.merge_field_date
import jp.cjbc.kakeibo.resources.merge_field_day
import jp.cjbc.kakeibo.resources.merge_field_last_applied
import jp.cjbc.kakeibo.resources.merge_field_order
import jp.cjbc.kakeibo.resources.merge_kind_expense_category
import jp.cjbc.kakeibo.resources.merge_kind_fund
import jp.cjbc.kakeibo.resources.merge_kind_income_category
import jp.cjbc.kakeibo.resources.merge_kind_recurring
import jp.cjbc.kakeibo.resources.merge_kind_rule
import jp.cjbc.kakeibo.resources.merge_no
import jp.cjbc.kakeibo.resources.merge_unreadable
import jp.cjbc.kakeibo.resources.merge_yes
import jp.cjbc.kakeibo.resources.recurring_enabled
import jp.cjbc.kakeibo.resources.sheet_pinned
import jp.cjbc.kakeibo.resources.unreadable_ledger_message
import jp.cjbc.kakeibo.resources.unreadable_ledger_title
import jp.cjbc.kakeibo.ui.PlatformBackHandler
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppChip
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import org.jetbrains.compose.resources.stringResource

/**
 * In place of the ledger, whose master.json pulled from the repository cannot be read for
 * [reason]: the user fixes it there, and pulls again with [onPullAgain] (`null` without syncing).
 */
@Composable
internal fun UnreadableLedgerScreen(reason: String, onPullAgain: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.Screen, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Warning, null, Modifier.size(40.dp), tint = AppTheme.colors.negative)
        Text(stringResource(Res.string.unreadable_ledger_title), style = AppTheme.type.screenTitle)
        Text(stringResource(Res.string.unreadable_ledger_message), style = AppTheme.type.body)
        Text(reason, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        onPullAgain?.let { ActionButton(stringResource(Res.string.check_pull_again), it) }
    }
}

/**
 * The records of [check] that cannot be shown, over the whole app so that nothing is edited
 * meanwhile: the user moves the records of the missing funds and categories to others of [data],
 * edits the wrong values, and for what the app cannot fix, is told to fix it in the repository and
 * pull again with [onPullAgain] (`null` without syncing). [names] names the funds and categories,
 * including those that are gone. [onSave] gets the fixed records; [onClose] leaves them as they are.
 */
@Composable
internal fun DataCheckScreen(
    check: DataCheck,
    data: LedgerData,
    names: Map<String, String>,
    onSave: (Map<RecordKey, SavedRecord>) -> Unit,
    onPullAgain: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val allNames = remember(check, names) { names + recordNames(check.records.entries) }
    // What to do with the records of each missing fund or category: the one to move them to, or
    // `null` to leave them hidden. And the texts of the fields being edited, by record and path.
    var replacements by remember(check) { mutableStateOf(emptyMap<String, String?>()) }
    var edits by remember(check) { mutableStateOf(emptyMap<RecordKey, Map<String, String>>()) }
    // The records still hidden after the last try to save them.
    var stillHidden by remember(check) { mutableStateOf(emptySet<RecordKey>()) }
    val fieldTroubles = check.troubles.filterValues { it is RecordTrouble.Fields }.mapValues { it.value as RecordTrouble.Fields }
    val dependents = check.troubles.filterValues { it is RecordTrouble.Dependent }
    val unfixable = check.troubles.filterValues {
        it == RecordTrouble.Unreadable || it == RecordTrouble.DuplicateId || it == RecordTrouble.Malformed
    }
    // Once any field of a record is edited, all its fields to edit are saved, those left as they
    // were too: a missing memo, say, is fixed by saving it empty.
    val parsed = edits.mapValues { (key, texts) ->
        val record = check.records.getValue(key)
        fieldTroubles.getValue(key).editable.associate { (path, kind) -> path to kind.parse(texts[path] ?: fieldText(record, path)) }
    }
    val moves = replacements.mapNotNull { (id, to) -> to?.let { id to it } }.toMap()
    val canSave = (moves.isNotEmpty() || parsed.values.any { it.isNotEmpty() }) && parsed.values.all { fields -> fields.values.all { it != null } }

    PlatformBackHandler(enabled = true, onBack = onClose)
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
            Text(stringResource(Res.string.check_intro, check.hidden.size.toString()), style = AppTheme.type.body)
            if (check.missing.isNotEmpty()) {
                Text(stringResource(Res.string.check_missing_intro), style = AppTheme.type.body)
                check.missing.forEach { (reference, keys) ->
                    MissingCard(
                        reference,
                        name = allNames[reference.id],
                        hidden = keys.map { brokenRecordView(it, check.records.getValue(it), allNames) },
                        candidates = candidates(data, reference.kind),
                        decided = reference.id in replacements,
                        replacement = replacements[reference.id],
                    ) { replacements = replacements + (reference.id to it) }
                }
            }
            if (fieldTroubles.isNotEmpty()) {
                Text(stringResource(Res.string.check_fields_intro), style = AppTheme.type.body)
                fieldTroubles.forEach { (key, trouble) ->
                    val record = check.records.getValue(key)
                    FieldsCard(
                        view = brokenRecordView(key, record, allNames),
                        trouble = trouble,
                        texts = edits[key].orEmpty(),
                        current = { path -> fieldText(record, path) },
                        label = { path -> fieldLabel(path, record, allNames) },
                        stillHidden = key in stillHidden,
                    ) { path, text -> edits = edits + (key to (edits[key].orEmpty() + (path to text))) }
                }
            }
            if (dependents.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.check_dependent))
                RecordList(dependents.keys.map { brokenRecordView(it, check.records.getValue(it), allNames) })
            }
            if (unfixable.isNotEmpty()) {
                Text(stringResource(Res.string.check_unfixable_intro), style = AppTheme.type.body)
                unfixable.forEach { (key, trouble) -> UnfixableCard(key, check.records.getValue(key), trouble, allNames) }
                onPullAgain?.let { TextButton(onClick = it) { Text(stringResource(Res.string.check_pull_again)) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(Res.string.check_close)) }
            ActionButton(
                stringResource(Res.string.check_save),
                onClick = {
                    val values = parsed.mapValues { (_, fields) -> fields.mapValues { it.value!! } }
                    val records = check.fixed(moves, values)
                    // Save only once the edited records can be shown; moved ones always can.
                    val hiddenAfter = DataCheck(records).hidden.keys
                    stillHidden = values.filterValues { it.isNotEmpty() }.keys.filter { it in hiddenAfter }.toSet()
                    if (stillHidden.isEmpty()) onSave(records)
                },
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The funds or categories of [kind] in [data] that records can be moved to, by ID, with names. */
private fun candidates(data: LedgerData, kind: ReferenceKind): List<Pair<String, String>> = when (kind) {
    ReferenceKind.FUND -> data.funds.filterNot { it.archived }.sortedBy { it.sortOrder }.map { it.id.value to it.name }
    ReferenceKind.EXPENSE_CATEGORY -> data.categories.filterIsInstance<Category.Expense>()
        .filterNot { it.archived }.sortedBy { it.sortOrder }.map { it.id.value to it.name }
    ReferenceKind.INCOME_CATEGORY -> data.categories.filterIsInstance<Category.Income>()
        .filterNot { it.archived }.sortedBy { it.sortOrder }.map { it.id.value to it.name }
}

/** A record whose [problems][RecordTrouble.Fields.problems] the user fixes by editing fields. */
@Composable
private fun FieldsCard(
    view: RecordView,
    trouble: RecordTrouble.Fields,
    texts: Map<String, String>,
    current: (String) -> String,
    label: @Composable (String) -> String,
    stillHidden: Boolean,
    onEdit: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("${view.kind} · ${view.title}")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                trouble.problems.forEach { problem ->
                    Text("${label(problem.path)}: ${issueText(problem)}", style = AppTheme.type.label, color = AppTheme.colors.negative)
                }
                trouble.editable.forEach { (path, kind) ->
                    val text = texts[path] ?: current(path)
                    val invalid = texts.isNotEmpty() && kind.parse(text) == null
                    if (kind == FieldKind.YES_NO) {
                        Text(label(path), style = AppTheme.type.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppChip(stringResource(Res.string.merge_yes), selected = text == "true", onClick = { onEdit(path, "true") })
                            AppChip(stringResource(Res.string.merge_no), selected = text == "false", onClick = { onEdit(path, "false") })
                        }
                    } else {
                        LabeledTextField(
                            text,
                            label(path),
                            { onEdit(path, it) },
                            error = if (invalid) wrongTypeText(kind) else null,
                        )
                    }
                }
                if (stillHidden) FieldError(stringResource(Res.string.check_still_hidden))
            }
        }
    }
}

/** A record the app cannot fix, with why and the line of the file it is in. */
@Composable
private fun UnfixableCard(key: RecordKey, record: SavedRecord, trouble: RecordTrouble, names: Map<String, String>) {
    val (file, raw) = when (record) {
        is SavedRecord.Master -> MASTER_FILE to record.element.toString()
        is SavedRecord.TransactionLine -> record.file to record.line
    }
    val reason = when (trouble) {
        RecordTrouble.Unreadable -> stringResource(Res.string.check_unreadable, file)
        RecordTrouble.DuplicateId -> stringResource(Res.string.check_duplicate_id, file)
        else -> stringResource(Res.string.check_malformed, file)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val view = brokenRecordView(key, record, names)
        SectionHeader("${view.kind} · ${view.title}")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reason, style = AppTheme.type.label, color = AppTheme.colors.negative)
                Text(
                    raw,
                    style = AppTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                    color = AppTheme.colors.onSurfaceMuted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A record that may not be readable, named from what can be read of it. */
@Composable
private fun brokenRecordView(key: RecordKey, record: SavedRecord, names: Map<String, String>): RecordView {
    val readable = recordView(key, record, names)
    if (readable.kind != stringResource(Res.string.merge_unreadable)) return readable
    fun field(path: String) = fieldText(record, path)
    val kind = when (key.list) {
        "funds" -> stringResource(Res.string.merge_kind_fund)
        "expense_categories" -> stringResource(Res.string.merge_kind_expense_category)
        "income_categories" -> stringResource(Res.string.merge_kind_income_category)
        "allocation_rules" -> stringResource(Res.string.merge_kind_rule)
        "recurring_items" -> stringResource(Res.string.merge_kind_recurring)
        TRANSACTIONS_LIST -> when (field("type")) {
            "expense" -> stringResource(Res.string.expense)
            "income" -> stringResource(Res.string.income)
            else -> readable.kind
        }
        else -> readable.kind
    }
    val title = if (key.list == TRANSACTIONS_LIST) {
        listOf(field("transaction_date"), names[field("category_id")].orEmpty(), field("amount").toLongOrNull()?.let(::formatAmount) ?: field("amount"), field("memo"))
    } else {
        listOf(field("name").ifEmpty { names[field("income_category_id")].orEmpty() })
    }.filter { it.isNotBlank() }.joinToString(" ").ifEmpty { readable.title }
    return RecordView(kind, title, readable.fields)
}

/** What the field at [path] of [record] is called. */
@Composable
private fun fieldLabel(path: String, record: SavedRecord, names: Map<String, String>): String {
    val steps = path.split('.')
    return when {
        steps.size == 3 && steps[0] == "allocations" ->
            stringResource(Res.string.check_allocation_amount, names[fieldText(record, "allocations.${steps[1]}.fund_id")] ?: "${steps[1].toInt() + 1}")
        steps.size == 3 && steps[0] == "lines" -> stringResource(
            if (steps[2] == "percent") Res.string.check_rule_line_percent else Res.string.check_rule_line_amount,
            names[fieldText(record, "lines.${steps[1]}.fund_id")] ?: "${steps[1].toInt() + 1}",
        )
        else -> when (path) {
            "amount" -> stringResource(Res.string.amount)
            "transaction_date" -> stringResource(Res.string.merge_field_date)
            "memo" -> stringResource(Res.string.entry_memo_chip)
            "budget_month" -> stringResource(Res.string.merge_field_budget_month)
            "name" -> stringResource(Res.string.fund_name)
            "color" -> stringResource(Res.string.fund_color)
            "opening_balance" -> stringResource(Res.string.fund_opening)
            "show_in_entry" -> stringResource(Res.string.fund_show_in_entry)
            "sort_order" -> stringResource(Res.string.merge_field_order)
            "archived" -> stringResource(Res.string.merge_field_archived)
            "pinned" -> stringResource(Res.string.sheet_pinned)
            "day_of_month" -> stringResource(Res.string.merge_field_day)
            "enabled" -> stringResource(Res.string.recurring_enabled)
            "last_applied_month" -> stringResource(Res.string.merge_field_last_applied)
            "goal.amount" -> stringResource(Res.string.fund_goal_amount)
            "goal.start_month" -> stringResource(Res.string.fund_goal_start)
            "goal.due_month" -> stringResource(Res.string.fund_goal_due)
            else -> path
        }
    }
}

@Composable
private fun issueText(problem: FieldProblem): String = when (problem.issue) {
    FieldIssue.MISSING -> stringResource(Res.string.check_issue_missing)
    FieldIssue.WRONG_TYPE -> wrongTypeText(problem.kind)
    FieldIssue.NOT_POSITIVE -> stringResource(Res.string.check_issue_not_positive)
    FieldIssue.NEGATIVE -> stringResource(Res.string.check_issue_negative)
    FieldIssue.DAY_OUT_OF_RANGE -> stringResource(Res.string.check_issue_day)
    FieldIssue.ALLOCATIONS_DO_NOT_ADD_UP -> stringResource(Res.string.check_issue_allocations)
}

@Composable
private fun wrongTypeText(kind: FieldKind): String = stringResource(
    when (kind) {
        FieldKind.WHOLE_NUMBER -> Res.string.check_issue_wrong_number
        FieldKind.DATE -> Res.string.check_issue_wrong_date
        FieldKind.MONTH -> Res.string.check_issue_wrong_month
        FieldKind.OPTIONAL_MONTH -> Res.string.check_issue_wrong_optional_month
        FieldKind.YES_NO -> Res.string.check_issue_wrong_yes_no
        FieldKind.TEXT, FieldKind.ID, FieldKind.OPTIONAL_ID -> Res.string.check_issue_wrong_text
    },
)
