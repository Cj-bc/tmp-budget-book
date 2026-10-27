package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
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
import jp.cjbc.kakeibo.data.files.Reference
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
import jp.cjbc.kakeibo.resources.check_allocation_sum
import jp.cjbc.kakeibo.resources.check_choose
import jp.cjbc.kakeibo.resources.check_field_allocation
import jp.cjbc.kakeibo.resources.check_field_due_month
import jp.cjbc.kakeibo.resources.check_field_percent
import jp.cjbc.kakeibo.resources.check_field_start_month
import jp.cjbc.kakeibo.resources.check_jump_move
import jp.cjbc.kakeibo.resources.check_jump_repo
import jp.cjbc.kakeibo.resources.check_jump_values
import jp.cjbc.kakeibo.resources.check_keep
import jp.cjbc.kakeibo.resources.check_later
import jp.cjbc.kakeibo.resources.check_line
import jp.cjbc.kakeibo.resources.check_move_to
import jp.cjbc.kakeibo.resources.check_no_choices
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
import jp.cjbc.kakeibo.resources.check_progress_repo
import jp.cjbc.kakeibo.resources.check_reason_duplicate_id
import jp.cjbc.kakeibo.resources.check_reason_no_id
import jp.cjbc.kakeibo.resources.check_reason_not_json
import jp.cjbc.kakeibo.resources.check_reason_unknown_shape
import jp.cjbc.kakeibo.resources.check_reason_unknown_version
import jp.cjbc.kakeibo.resources.check_referenced_by
import jp.cjbc.kakeibo.resources.check_referenced_more
import jp.cjbc.kakeibo.resources.check_repo_hint
import jp.cjbc.kakeibo.resources.check_resync
import jp.cjbc.kakeibo.resources.check_save
import jp.cjbc.kakeibo.resources.check_save_count
import jp.cjbc.kakeibo.resources.check_section_move
import jp.cjbc.kakeibo.resources.check_section_repo
import jp.cjbc.kakeibo.resources.check_section_values
import jp.cjbc.kakeibo.resources.check_status_done
import jp.cjbc.kakeibo.resources.check_status_kept
import jp.cjbc.kakeibo.resources.check_status_open
import jp.cjbc.kakeibo.resources.check_summary_after
import jp.cjbc.kakeibo.resources.check_summary_all
import jp.cjbc.kakeibo.resources.check_title
import jp.cjbc.kakeibo.resources.check_title_expense_category
import jp.cjbc.kakeibo.resources.check_title_fund
import jp.cjbc.kakeibo.resources.check_title_income_category
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
import jp.cjbc.kakeibo.ui.component.DropdownChip
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How many of the records referring to a missing fund or category are named on its card. */
private const val REFERRERS_NAMED = 3

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

    /** What the user typed for the field at [path] of the record [key], or else its value, as text. */
    fun input(key: RecordKey, path: FieldPath, type: FieldType): String =
        inputs[key]?.get(path) ?: type.input(check.records.getValue(key).valueAt(path))
}

/** Where a missing fund or category, or a record with wrong values, stands. */
private enum class FixStatus { OPEN, DONE, KEPT }

/**
 * The records of [fix], over the whole app as the merge is: a summary of how far they are fixed,
 * then the records of each missing fund or category, to move to another or leave; the records
 * whose values the app does not take, each value to edit by itself; and the ones only the
 * repository can fix, with one button to sync again.
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
    val total = check.missing.size + fix.fields.size + unfixable.size
    val settled = missingStatus.values.count { it != FixStatus.OPEN } + recordStatus.values.count { it == FixStatus.DONE }
    val missingIds = check.missing.keys.map { it.id }.toSet()
    val moves = replacements.filterKeys { it in missingIds }.mapNotNull { (id, to) -> to?.let { id to it } }.toMap()
    val fixedCount = moves.size + recordStatus.values.count { it == FixStatus.DONE }
    val fixed = remember(records, moves) { replaceReferences(records, moves) }
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
                inRepository = unfixable.size,
                jumps = listOfNotNull(
                    (SECTION_MOVE to stringResource(Res.string.check_jump_move, check.missing.size.toString())).takeIf { check.missing.isNotEmpty() },
                    (SECTION_VALUES to stringResource(Res.string.check_jump_values, fix.fields.size.toString())).takeIf { fix.fields.isNotEmpty() },
                    (SECTION_REPOSITORY to stringResource(Res.string.check_jump_repo, unfixable.size.toString())).takeIf { unfixable.isNotEmpty() },
                ),
            ) { section -> sectionTops[section]?.let { scope.launch { scroll.animateScrollTo(it) } } }

            if (check.missing.isNotEmpty()) {
                Section(SECTION_MOVE, stringResource(Res.string.check_section_move), sectionTops) {
                    check.missing.forEach { (reference, keys) ->
                        MissingCard(
                            reference,
                            name = names[reference.id] ?: reference.id,
                            referrers = keys.map { recordView(it, records.getValue(it), names).title },
                            candidates = check.candidates(reference.kind),
                            status = missingStatus.getValue(reference),
                            replacement = replacements[reference.id],
                            folded = missingStatus.getValue(reference) != FixStatus.OPEN && fix.current != reference.id,
                            onOpen = { fix.current = reference.id },
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
            if (unfixable.isNotEmpty()) {
                Section(
                    SECTION_REPOSITORY,
                    stringResource(Res.string.check_section_repo),
                    sectionTops,
                    action = stringResource(Res.string.check_resync) to { scope.launch { runner.pullAgainForFix() } },
                ) {
                    Text(stringResource(Res.string.check_repo_hint), style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
                    unfixable.forEach { (key, reason) -> UnfixableCard(fix, key, fix.check.records.getValue(key), reason) }
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

private const val SECTION_MOVE = "move"
private const val SECTION_VALUES = "values"
private const val SECTION_REPOSITORY = "repository"

/**
 * How far the records are fixed: [settled] of [total], of which [inRepository] can only be fixed
 * in the repository, so that the bar is never all done while they are there; and a chip that
 * [onJump]s to each section of [jumps].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(settled: Int, total: Int, inRepository: Int, jumps: List<Pair<String, String>>, onJump: (String) -> Unit) {
    val colors = AppTheme.colors
    SectionCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(Res.string.check_progress, settled.toString(), total.toString()),
                    style = AppTheme.type.labelBold,
                    modifier = Modifier.weight(1f),
                )
                if (inRepository > 0) {
                    Text(stringResource(Res.string.check_progress_repo, inRepository.toString()), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                }
            }
            // Done in green from the start; what only the repository can fix hatched at the end.
            val hatch = colors.onSurfaceMuted
            Canvas(Modifier.fillMaxWidth().height(8.dp).clip(AppShapes.Pill)) {
                drawRect(colors.track)
                if (total == 0) return@Canvas
                drawRect(colors.success, size = Size(size.width * settled / total, size.height))
                val start = size.width * (total - inRepository) / total
                clipRect(left = start) {
                    drawRect(hatch.copy(alpha = 0.18f), topLeft = Offset(start, 0f))
                    val step = 6.dp.toPx()
                    var x = start - size.height
                    while (x < size.width) {
                        drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                        x += step
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                jumps.forEach { (section, label) -> AppChip(label, selected = false, onClick = { onJump(section) }) }
            }
        }
    }
}

/**
 * A section [name]d [title], with an optional [action] (its label and what it does) beside the
 * title, noting where it starts in [tops].
 */
@Composable
private fun Section(
    name: String,
    title: String,
    tops: MutableMap<String, Int>,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.onGloballyPositioned { tops[name] = it.positionInParent().y.toInt() },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title, Modifier.weight(1f))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
        content()
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
 * A fund or category that is missing, [name]d, with the records that refer to it ([referrers],
 * named by their titles): the user moves them to one of the [candidates], or leaves them.
 * [replacement] is the ID chosen; [folded] shows only the title and [status].
 */
@Composable
private fun MissingCard(
    reference: Reference,
    name: String,
    referrers: List<String>,
    candidates: List<Pair<String, String>>,
    status: FixStatus,
    replacement: String?,
    folded: Boolean,
    onOpen: () -> Unit,
    onDecide: (String?) -> Unit,
) {
    val colors = AppTheme.colors
    val title = stringResource(
        when (reference.kind) {
            ReferenceKind.FUND -> Res.string.check_title_fund
            ReferenceKind.EXPENSE_CATEGORY -> Res.string.check_title_expense_category
            ReferenceKind.INCOME_CATEGORY -> Res.string.check_title_income_category
        },
        name,
    )
    val chosen = candidates.firstOrNull { it.first == replacement }?.second
    if (folded) {
        FoldedCard(if (chosen != null) "$title → $chosen" else title, status, onOpen)
        return
    }
    FixCard(title, status) {
        val named = referrers.take(REFERRERS_NAMED).joinToString("、") +
            if (referrers.size > REFERRERS_NAMED) "、" + stringResource(Res.string.check_referenced_more, (referrers.size - REFERRERS_NAMED).toString()) else ""
        Text(stringResource(Res.string.check_referenced_by, referrers.size.toString(), named), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
        if (candidates.isEmpty()) {
            Text(stringResource(Res.string.check_no_choices), style = AppTheme.type.label, color = colors.onSurfaceMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.check_move_to), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                DropdownChip(
                    label = { Text(chosen ?: stringResource(Res.string.check_choose), style = AppTheme.type.label) },
                    options = candidates,
                    onSelect = { onDecide(it.first) },
                    item = { Text(it.second) },
                )
            }
        }
        if (status != FixStatus.KEPT) {
            TextButton(onClick = { onDecide(null) }) { Text(stringResource(Res.string.check_keep)) }
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

/**
 * A record that only the repository can fix: where it is, why it cannot be read, and the start of
 * its text.
 */
@Composable
private fun UnfixableCard(fix: RecordFix, key: RecordKey, record: SavedRecord, reason: UnfixableReason) {
    val (place, raw) = when (record) {
        is SavedRecord.Master -> "$MASTER_FILE · ${key.list}" to record.element.toString()
        is SavedRecord.TransactionLine -> (
            fix.saved.lineNumber(record)?.let { stringResource(Res.string.check_line, record.file, it.toString()) } ?: record.file
            ) to record.line
    }
    SectionCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(place, style = AppTheme.type.labelBold)
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
            Text(
                raw,
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
            Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
            if (options.isEmpty()) {
                Text(stringResource(Res.string.check_no_choices), style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted)
            } else {
                DropdownChip(
                    label = { Text(options.firstOrNull { it.first == input }?.second ?: stringResource(Res.string.check_choose), style = AppTheme.type.label) },
                    options = options,
                    onSelect = { onInput(it.first) },
                    item = { Text(it.second) },
                )
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
