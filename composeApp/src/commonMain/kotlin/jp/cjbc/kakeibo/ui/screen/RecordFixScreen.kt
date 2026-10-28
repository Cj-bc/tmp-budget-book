package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.SavedFiles
import jp.cjbc.kakeibo.data.files.FieldPath
import jp.cjbc.kakeibo.data.files.FieldProblem
import jp.cjbc.kakeibo.data.files.FieldType
import jp.cjbc.kakeibo.data.files.FundRecord
import jp.cjbc.kakeibo.data.files.InvalidField
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.RecordEdit
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecordsCheck
import jp.cjbc.kakeibo.data.files.Reference
import jp.cjbc.kakeibo.data.files.ReferenceKind
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.TransactionRecord
import jp.cjbc.kakeibo.data.files.UnfixableReason
import jp.cjbc.kakeibo.data.files.UnreadablePlace
import jp.cjbc.kakeibo.data.files.decodeRecord
import jp.cjbc.kakeibo.data.files.replaceReferences
import jp.cjbc.kakeibo.data.files.valueAt
import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.allocate_section
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.check_allocation_sum
import jp.cjbc.kakeibo.resources.check_choose
import jp.cjbc.kakeibo.resources.check_count
import jp.cjbc.kakeibo.resources.check_field_allocation
import jp.cjbc.kakeibo.resources.check_field_due_month
import jp.cjbc.kakeibo.resources.check_field_percent
import jp.cjbc.kakeibo.resources.check_field_start_month
import jp.cjbc.kakeibo.resources.check_jump_move
import jp.cjbc.kakeibo.resources.check_jump_values
import jp.cjbc.kakeibo.resources.check_keep
import jp.cjbc.kakeibo.resources.check_later
import jp.cjbc.kakeibo.resources.check_line
import jp.cjbc.kakeibo.resources.check_move_to
import jp.cjbc.kakeibo.resources.check_no_choices
import jp.cjbc.kakeibo.resources.check_period
import jp.cjbc.kakeibo.resources.check_period_one
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
import jp.cjbc.kakeibo.resources.check_progress
import jp.cjbc.kakeibo.resources.check_reason_duplicate_id
import jp.cjbc.kakeibo.resources.check_reason_no_id
import jp.cjbc.kakeibo.resources.check_reason_not_json
import jp.cjbc.kakeibo.resources.check_reason_unknown_shape
import jp.cjbc.kakeibo.resources.check_reason_unknown_version
import jp.cjbc.kakeibo.resources.check_referenced_by
import jp.cjbc.kakeibo.resources.check_resync
import jp.cjbc.kakeibo.resources.check_save
import jp.cjbc.kakeibo.resources.check_save_count
import jp.cjbc.kakeibo.resources.check_section_move
import jp.cjbc.kakeibo.resources.check_section_values
import jp.cjbc.kakeibo.resources.check_show_list
import jp.cjbc.kakeibo.resources.check_status_done
import jp.cjbc.kakeibo.resources.check_status_kept
import jp.cjbc.kakeibo.resources.check_status_open
import jp.cjbc.kakeibo.resources.check_summary_after
import jp.cjbc.kakeibo.resources.check_summary_all
import jp.cjbc.kakeibo.resources.check_title
import jp.cjbc.kakeibo.resources.check_title_expense_category
import jp.cjbc.kakeibo.resources.check_title_fund
import jp.cjbc.kakeibo.resources.check_title_income_category
import jp.cjbc.kakeibo.resources.check_title_unknown_expense_category
import jp.cjbc.kakeibo.resources.check_title_unknown_fund
import jp.cjbc.kakeibo.resources.check_title_unknown_income_category
import jp.cjbc.kakeibo.resources.check_unreadable_first
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
import jp.cjbc.kakeibo.ui.PlatformBackHandler
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppChip
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatLongDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The hidden records, as [check] found them in [saved], while the user fixes them: the values
 * typed for their fields, and what to do with the records of each missing fund or category. The
 * [earlier] records name the funds and categories that are no longer there. [afterSync] tells
 * whether the changes a sync took in hid them, or the user asked for every hidden record.
 */
@Stable
internal class RecordFix(
    val saved: SavedFiles,
    val check: RecordsCheck,
    val earlier: Map<RecordKey, SavedRecord>,
    val afterSync: Boolean,
    /** The names that missing funds and categories last had in the history, by ID. */
    val historyNames: Map<String, String> = emptyMap(),
) {
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

    /**
     * What the user last worked on: a [RecordKey], or the ID of a missing fund or category. It stays
     * open when done; the others fold once done.
     */
    var current by mutableStateOf<Any?>(null)

    fun type(key: RecordKey, path: FieldPath, input: String) {
        inputs = inputs + (key to inputs[key].orEmpty() + (path to input))
        current = key
    }

    fun replace(id: String, replacement: String?) {
        replacements = replacements + (id to replacement)
        current = id
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

    /** Where the record [key], which cannot be read because of [reason], is. */
    fun place(key: RecordKey, reason: UnfixableReason): UnreadablePlace = when (val record = check.records.getValue(key)) {
        is SavedRecord.Master -> UnreadablePlace(MASTER_FILE, null, record.element.toString(), reason)
        is SavedRecord.TransactionLine -> UnreadablePlace(record.file, saved.lineNumber(record), record.line, reason)
    }

    /** What the user typed for the field at [path] of the record [key], or else its value, as text. */
    fun input(key: RecordKey, path: FieldPath, type: FieldType): String =
        inputs[key]?.get(path) ?: type.input(check.records.getValue(key).valueAt(path))
}

/** Where a missing fund or category, or a record with wrong values, stands. */
private enum class FixStatus { OPEN, DONE, KEPT }

/**
 * The records of [fix], over the whole app as the merge is: a summary of how far they are fixed,
 * then the records of each missing fund or category, to move to another or leave; and the records
 * whose values the app does not take, each value to edit by itself. While some records cannot be
 * read at all, only those show, with a button to sync again once the repository is fixed.
 */
@Composable
internal fun RecordFixDialog(runner: SyncRunner, fix: RecordFix) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    // Where each section starts in the scrolled column, to jump to it from the summary.
    val sectionTops = remember { mutableStateMapOf<String, Int>() }
    val names = remember(fix) { recordNames(fix.earlier.entries + fix.check.records.entries) }
    val edits = remember(fix.inputs, fix.fields) { fix.edits() }
    LaunchedEffect(edits) { fix.showProblems(edits) }
    val records = remember(edits) { fix.check.records + edits.mapValues { it.value.record } }
    // Reading all the records again takes a while, so only when the values taken change.
    val check = remember(records) { RecordsCheck(records) { key, _ -> key in fix.check.hidden } }
    val replacements = fix.replacements
    val unfixable = fix.check.unfixable

    val missingStatus = check.missing.keys.associateWith { reference ->
        when {
            reference.id !in replacements -> FixStatus.OPEN
            replacements[reference.id] == null -> FixStatus.KEPT
            else -> FixStatus.DONE
        }
    }
    val recordStatus = fix.fields.keys.associateWith { if (edits.getValue(it).problems.isEmpty()) FixStatus.DONE else FixStatus.OPEN }
    val total = check.missing.size + fix.fields.size
    val settled = missingStatus.values.count { it != FixStatus.OPEN } + recordStatus.values.count { it == FixStatus.DONE }
    val missingIds = check.missing.keys.map { it.id }.toSet()
    val moves = replacements.filterKeys { it in missingIds }.mapNotNull { (id, to) -> to?.let { id to it } }.toMap()
    val fixedCount = moves.size + recordStatus.values.count { it == FixStatus.DONE }
    val fixed = remember(records, moves) { replaceReferences(records, moves) }
    val fixable = fix.fields.isNotEmpty() || check.missing.isNotEmpty()
    // The missing fund or category whose records are listed in place of the rest, or null.
    var listing by remember { mutableStateOf<Reference?>(null) }
    listing?.let { reference ->
        ReferrerList(
            title = missingTitle(reference, names[reference.id] ?: fix.historyNames[reference.id]),
            keys = check.missing[reference].orEmpty(),
            records = records,
            names = names,
        ) { listing = null }
        return
    }
    // The other values can only be checked once every record can be read.
    if (unfixable.isNotEmpty()) {
        UnreadableFirst(
            unfixable.map { (key, reason) -> fix.place(key, reason) },
            onClose = runner::closeFix,
        ) { scope.launch { runner.pullAgainForFix() } }
        return
    }

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
                .verticalScroll(scroll)
                .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                stringResource(
                    if (fix.afterSync) Res.string.check_summary_after else Res.string.check_summary_all,
                    fix.check.hidden.size.toString(),
                ),
                style = AppTheme.type.label,
                color = AppTheme.colors.onSurfaceMuted,
            )
            Summary(
                settled = settled,
                total = total,
                jumps = listOfNotNull(
                    (SECTION_MOVE to stringResource(Res.string.check_jump_move, check.missing.size.toString())).takeIf { check.missing.isNotEmpty() },
                    (SECTION_VALUES to stringResource(Res.string.check_jump_values, fix.fields.size.toString())).takeIf { fix.fields.isNotEmpty() },
                ),
            ) { section -> sectionTops[section]?.let { scope.launch { scroll.animateScrollTo(it) } } }

            if (check.missing.isNotEmpty()) {
                Section(SECTION_MOVE, stringResource(Res.string.check_section_move), sectionTops) {
                    check.missing.forEach { (reference, keys) ->
                        MissingCard(
                            title = missingTitle(reference, names[reference.id] ?: fix.historyNames[reference.id]),
                            count = keys.size,
                            summary = referrerSummary(keys, records),
                            candidates = check.candidates(reference.kind),
                            status = missingStatus.getValue(reference),
                            replacement = replacements[reference.id],
                            folded = missingStatus.getValue(reference) != FixStatus.OPEN && fix.current != reference.id,
                            onOpen = { fix.current = reference.id },
                            onShowList = { listing = reference },
                        ) { fix.replace(reference.id, it) }
                    }
                }
            }
            if (fix.fields.isNotEmpty()) {
                Section(SECTION_VALUES, stringResource(Res.string.check_section_values), sectionTops) {
                    fix.fields.forEach { (key, types) ->
                        val view = recordView(key, records.getValue(key), names)
                        val status = recordStatus.getValue(key)
                        if (status == FixStatus.DONE && fix.current != key) {
                            FoldedCard("${view.kind} · ${view.title}", status) { fix.current = key }
                        } else {
                            FixCard("${view.kind} · ${view.title}", status) {
                                AllocationSum(fix, key, types)
                                types.forEach { (path, type) ->
                                    FieldEditor(
                                        label = fieldLabel(path, records.getValue(key), names),
                                        type = type,
                                        input = fix.input(key, path, type),
                                        // The sum of the allocations is shown once, above.
                                        problem = edits.getValue(key).problems[path]?.takeIf { it.problem != FieldProblem.ALLOCATIONS_DO_NOT_ADD_UP },
                                        candidates = { kind -> check.candidates(kind) },
                                    ) { fix.type(key, path, it) }
                                }
                            }
                        }
                    }
                }
            }
            if (check.others.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(Res.string.merge_other_hidden))
                    RecordList(check.others.map { recordView(it, records.getValue(it), names) })
                }
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
                    if (fixedCount > 0) stringResource(Res.string.check_save_count, fixedCount.toString()) else stringResource(Res.string.check_save),
                    onClick = { runner.saveFix(fixed) },
                    enabled = fixed != fix.check.records,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The records [keys] among [records], under [title], in place of the screen until [onBack]: the
 * transactions as the history shows them, by day, latest first, then the other records.
 */
@Composable
private fun ReferrerList(
    title: String,
    keys: List<RecordKey>,
    records: Map<RecordKey, SavedRecord>,
    names: Map<String, String>,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val transactions = keys.mapNotNull { key -> (decodeRecord(key, records.getValue(key)) as? TransactionRecord)?.let { key to it } }
    val days = transactions
        .sortedByDescending { (_, record) -> record.date }
        .groupBy { (_, record) -> record.date }
        .toList()
    val others = keys - transactions.map { it.first }.toSet()
    val fundColors = remember(records) {
        records.entries.mapNotNull { (key, record) -> (decodeRecord(key, record) as? FundRecord)?.let { it.id to Color(it.color) } }.toMap()
    }
    PlatformBackHandler(enabled = true, onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(title, onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = AppSpacing.Screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(days, key = { it.first.toString() }) { (date, day) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(formatLongDate(date))
                    Column(Modifier.fillMaxWidth().clip(AppShapes.DayGroup).background(colors.surface)) {
                        day.forEachIndexed { index, (_, record) ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                            val category = names[record.categoryId] ?: record.categoryId
                            val fund = when (record) {
                                is TransactionRecord.Expense -> record.fundId
                                is TransactionRecord.Income -> record.allocations.firstOrNull()?.fundId
                            }
                            TransactionRowContent(
                                memo = record.memo.ifBlank { category },
                                category = category,
                                barColor = fund?.let(fundColors::get) ?: colors.outlineStrong,
                                isAuto = record is TransactionRecord.Expense && record.recurringItemId != null,
                                movedFrom = null,
                                amount = if (record is TransactionRecord.Expense) -record.amount else record.amount,
                            )
                        }
                    }
                }
            }
            if (others.isNotEmpty()) {
                item {
                    RecordList(others.map { recordView(it, records.getValue(it), names) })
                }
            }
        }
    }
}

private val TransactionRecord.date get() = when (this) {
    is TransactionRecord.Expense -> transactionDate
    is TransactionRecord.Income -> transactionDate
}

private val TransactionRecord.categoryId get() = when (this) {
    is TransactionRecord.Expense -> categoryId
    is TransactionRecord.Income -> categoryId
}

private val TransactionRecord.amount get() = when (this) {
    is TransactionRecord.Expense -> amount
    is TransactionRecord.Income -> amount
}

private val TransactionRecord.memo get() = when (this) {
    is TransactionRecord.Expense -> memo
    is TransactionRecord.Income -> memo
}

private const val SECTION_MOVE = "move"
private const val SECTION_VALUES = "values"

/** How far the records are fixed, [settled] of [total], and a chip that [onJump]s to each section of [jumps]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(settled: Int, total: Int, jumps: List<Pair<String, String>>, onJump: (String) -> Unit) {
    val colors = AppTheme.colors
    SectionCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.check_progress, settled.toString(), total.toString()), style = AppTheme.type.labelBold)
            Canvas(Modifier.fillMaxWidth().height(8.dp).clip(AppShapes.Pill)) {
                drawRect(colors.track)
                if (total > 0) drawRect(colors.success, size = Size(size.width * settled / total, size.height))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                jumps.forEach { (section, label) -> AppChip(label, selected = false, onClick = { onJump(section) }) }
            }
        }
    }
}

/** A section [name]d [title], noting where it starts in [tops]. */
@Composable
private fun Section(name: String, title: String, tops: MutableMap<String, Int>, content: @Composable () -> Unit) {
    Column(
        Modifier.onGloballyPositioned { tops[name] = it.positionInParent().y.toInt() },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(title)
        content()
    }
}

/**
 * The records at [places] that cannot be read, in place of the rest, which can only be checked once
 * they are fixed in the repository and synced again with [onSync].
 */
@Composable
private fun UnreadableFirst(places: List<UnreadablePlace>, onClose: () -> Unit, onSync: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
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
            Text(stringResource(Res.string.check_unreadable_first), style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted)
            places.forEach { UnreadablePlaceCard(it) }
        }
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(Res.string.sync_close)) }
            ActionButton(stringResource(Res.string.check_resync), onClick = onSync, modifier = Modifier.weight(1f))
        }
    }
}

/** The pill telling where a card stands. */
@Composable
private fun StatusPill(status: FixStatus) {
    val colors = AppTheme.colors
    val (label, background, color) = when (status) {
        FixStatus.OPEN -> Triple(Res.string.check_status_open, colors.warningContainer, colors.onWarningContainer)
        FixStatus.DONE -> Triple(Res.string.check_status_done, colors.success.copy(alpha = 0.16f), colors.onSurface)
        FixStatus.KEPT -> Triple(Res.string.check_status_kept, colors.surfaceMuted, colors.onSurfaceMuted)
    }
    Text(
        stringResource(label),
        style = AppTheme.type.badge,
        color = color,
        modifier = Modifier.clip(AppShapes.Pill).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A card with its [title] and [status] above its [content]. */
@Composable
private fun FixCard(title: String, status: FixStatus, content: @Composable () -> Unit) {
    SectionCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = AppTheme.type.bodyBold, modifier = Modifier.weight(1f))
                StatusPill(status)
            }
            content()
        }
    }
}

/** A card that is settled, folded to its [title] and [status]; it opens when tapped. */
@Composable
private fun FoldedCard(title: String, status: FixStatus, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppTheme.colors.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StatusPill(status)
    }
}

/**
 * The title of the card of a missing fund or category, by its [name]; without one, by the end of
 * its ID, which tells it apart from others well enough.
 */
@Composable
private fun missingTitle(reference: Reference, name: String?): String = if (name != null) {
    stringResource(
        when (reference.kind) {
            ReferenceKind.FUND -> Res.string.check_title_fund
            ReferenceKind.EXPENSE_CATEGORY -> Res.string.check_title_expense_category
            ReferenceKind.INCOME_CATEGORY -> Res.string.check_title_income_category
        },
        name,
    )
} else {
    stringResource(
        when (reference.kind) {
            ReferenceKind.FUND -> Res.string.check_title_unknown_fund
            ReferenceKind.EXPENSE_CATEGORY -> Res.string.check_title_unknown_expense_category
            ReferenceKind.INCOME_CATEGORY -> Res.string.check_title_unknown_income_category
        },
        reference.id.takeLast(ID_SHOWN),
    )
}

/** How many characters from the end of an ID name a fund or category whose name is not known. */
private const val ID_SHOWN = 6

/**
 * The records [keys] among [records] in a few words, however many: how many of each kind, and for
 * transactions the months they span, e.g. "Expense: 120 (2025-04–2026-10) · Recurring expense: 2".
 */
@Composable
private fun referrerSummary(keys: List<RecordKey>, records: Map<RecordKey, SavedRecord>): String {
    val byKind = keys.groupBy { key -> recordKind(key, records.getValue(key)) }
    return byKind.map { (kind, group) ->
        val months = group.mapNotNull { (records.getValue(it).valueAt(DATE) as? JsonPrimitive)?.content?.take(7) }.sorted()
        val count = stringResource(Res.string.check_count, stringResource(kind), group.size.toString())
        when {
            months.isEmpty() -> count
            months.first() == months.last() -> count + stringResource(Res.string.check_period_one, months.first())
            else -> count + stringResource(Res.string.check_period, months.first(), months.last())
        }
    }.joinToString(" · ")
}

private val DATE = FieldPath(listOf("transaction_date"))

/**
 * A fund or category that is missing, under [title], with a [summary] of the [count] records that
 * refer to it, which [onShowList] lists: the user moves them to one of the [candidates], or leaves
 * them. [replacement] is the ID chosen; [folded] shows only the title and [status].
 */
@Composable
private fun MissingCard(
    title: String,
    count: Int,
    summary: String,
    candidates: List<Pair<String, String>>,
    status: FixStatus,
    replacement: String?,
    folded: Boolean,
    onOpen: () -> Unit,
    onShowList: () -> Unit,
    onDecide: (String?) -> Unit,
) {
    val colors = AppTheme.colors
    val chosen = candidates.firstOrNull { it.first == replacement }?.second
    if (folded) {
        FoldedCard(if (chosen != null) "$title → $chosen" else title, status, onOpen)
        return
    }
    FixCard(title, status) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.check_referenced_by, count.toString(), summary),
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowList) { Text(stringResource(Res.string.check_show_list)) }
        }
        if (candidates.isEmpty()) {
            Text(stringResource(Res.string.check_no_choices), style = AppTheme.type.label, color = colors.onSurfaceMuted)
        } else {
            SelectBox(stringResource(Res.string.check_move_to), chosen, candidates) { onDecide(it) }
        }
        if (status != FixStatus.KEPT) {
            TextButton(onClick = { onDecide(null) }) { Text(stringResource(Res.string.check_keep)) }
        }
    }
}

/**
 * A box as wide as the card, named [label], showing [chosen] or a prompt to choose, that opens a
 * menu of [options] (an ID and its name) and passes the ID picked to [onSelect].
 */
@Composable
private fun SelectBox(label: String, chosen: String?, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = AppTheme.type.caption, color = colors.onSurfaceMuted)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(AppShapes.Tile)
                    .border(1.dp, colors.outlineStrong, AppShapes.Tile)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chosen ?: stringResource(Res.string.check_choose),
                    style = AppTheme.type.body,
                    color = if (chosen == null) colors.onSurfaceMuted else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.ExpandMore, null, Modifier.size(20.dp), tint = colors.onSurfaceMuted)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        onSelect(id)
                        open = false
                    })
                }
            }
        }
    }
}

/**
 * For an income whose allocations are shown, what they add up to against its amount, as the user
 * types: the difference is signed, positive when they allocate more than the amount.
 */
@Composable
private fun AllocationSum(fix: RecordFix, key: RecordKey, types: Map<FieldPath, FieldType>) {
    if (types.keys.none { it.segments.firstOrNull() == "allocations" && it.segments.lastOrNull() == "amount" }) return
    val record = fix.check.records.getValue(key)
    val count = (record.valueAt(FieldPath(listOf("allocations"))) as? JsonArray)?.size ?: return
    fun number(path: FieldPath): Long? = fix.input(key, path, FieldType.NUMBER).trim().toLongOrNull()
    val amount = number(FieldPath(listOf("amount")))
    val allocations = (0 until count).map { number(FieldPath(listOf("allocations", it.toString(), "amount"))) }
    val allocated = if (allocations.any { it == null }) null else allocations.sumOf { it!! }
    val difference = if (amount != null && allocated != null) allocated - amount else null
    val colors = AppTheme.colors
    Text(
        stringResource(
            Res.string.check_allocation_sum,
            allocated?.let(::formatAmount) ?: "–",
            amount?.let(::formatAmount) ?: "–",
            when {
                difference == null -> "–"
                difference > 0 -> "+" + formatAmount(difference)
                difference < 0 -> "−" + formatAmount(-difference)
                else -> "0"
            },
        ),
        style = AppTheme.type.caption,
        color = if (difference == 0L) colors.onSurfaceMuted else colors.onWarningContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Tile)
            .background(if (difference == 0L) colors.surfaceMuted else colors.warningContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** The words for why a record cannot be read at all. */
@Composable
internal fun unreadableReason(reason: UnfixableReason): String = stringResource(
    when (reason) {
        UnfixableReason.NOT_JSON -> Res.string.check_reason_not_json
        UnfixableReason.UNKNOWN_VERSION -> Res.string.check_reason_unknown_version
        UnfixableReason.UNKNOWN_SHAPE -> Res.string.check_reason_unknown_shape
        UnfixableReason.NO_ID -> Res.string.check_reason_no_id
        UnfixableReason.DUPLICATE_ID -> Res.string.check_reason_duplicate_id
    },
)

/** Where [place] is, as the user reads it: the file, and the line in it if there is one. */
@Composable
internal fun placeLabel(place: UnreadablePlace): String =
    place.line?.let { stringResource(Res.string.check_line, place.file, it.toString()) } ?: place.file

/** A record that only the repository can fix: where it is, why it cannot be read, and the start of its text. */
@Composable
internal fun UnreadablePlaceCard(place: UnreadablePlace) {
    SectionCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(placeLabel(place), style = AppTheme.type.labelBold)
            Text(unreadableReason(place.reason), style = AppTheme.type.caption, color = AppTheme.colors.negative)
            Text(
                place.text,
                style = AppTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                color = AppTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The [input] of a field, named [label], of [type], with its [problem] if it has one. A fund or
 * category is picked among the [candidates] of its kind, a flag between yes and no; a number is
 * typed with the number keyboard.
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
    if (kind != null) {
        val options = candidates(kind)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (options.isEmpty()) {
                Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
                Text(stringResource(Res.string.check_no_choices), style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted)
            } else {
                SelectBox(label, options.firstOrNull { it.first == input }?.second, options, onInput)
            }
            error?.let { FieldError(it) }
        }
        return
    }
    if (type == FieldType.BOOLEAN) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppChip(stringResource(Res.string.merge_yes), selected = input == "true", onClick = { onInput("true") })
                AppChip(stringResource(Res.string.merge_no), selected = input == "false", onClick = { onInput("false") })
            }
            error?.let { FieldError(it) }
        }
        return
    }
    val numeric = type == FieldType.NUMBER || type == FieldType.INTEGER
    LabeledTextField(
        input,
        label,
        onInput,
        error = error,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
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
    // Shown once for the record, by AllocationSum.
    FieldProblem.ALLOCATIONS_DO_NOT_ADD_UP -> ""
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

/**
 * The field at [path] of [record] as the user reads it: the amount of an allocation is named by its
 * fund, "Allocation (Food)", by [names]; other fields of a list by their place in it.
 */
@Composable
private fun fieldLabel(path: FieldPath, record: SavedRecord, names: Map<String, String>): String {
    val segments = path.segments
    if (segments.size == 3 && segments[0] == "allocations" && segments[2] == "amount") {
        val fund = (record.valueAt(FieldPath(segments.take(2) + "fund_id")) as? JsonPrimitive)?.content
        val name = fund?.let { names[it] ?: it } ?: (segments[1].toInt() + 1).toString()
        return stringResource(Res.string.check_field_allocation, name)
    }
    return segments.map { segment ->
        segment.toIntOrNull()?.let { (it + 1).toString() } ?: FIELD_LABELS[segment]?.let { stringResource(it) } ?: segment
    }.joinToString(" › ")
}
