package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.activeIncomeCategories
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.nextSortOrder
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.settings_archive
import jp.cjbc.kakeibo.resources.settings_category_fund
import jp.cjbc.kakeibo.resources.sheet_archived
import jp.cjbc.kakeibo.resources.sheet_create
import jp.cjbc.kakeibo.resources.sheet_done
import jp.cjbc.kakeibo.resources.sheet_edit
import jp.cjbc.kakeibo.resources.sheet_group
import jp.cjbc.kakeibo.resources.sheet_move_down
import jp.cjbc.kakeibo.resources.sheet_move_up
import jp.cjbc.kakeibo.resources.sheet_pin
import jp.cjbc.kakeibo.resources.sheet_pin_limit
import jp.cjbc.kakeibo.resources.sheet_pinned
import jp.cjbc.kakeibo.resources.sheet_search
import jp.cjbc.kakeibo.resources.sheet_title
import jp.cjbc.kakeibo.resources.sheet_unpin
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Replaces [old] with [new] among the categories. */
private fun LedgerData.replacing(old: Category, new: Category): LedgerData =
    copy(categories = categories.map { if (it == old) new else it })

private fun Category.withPinned(pinned: Boolean): Category = when (this) {
    is Category.Expense -> copy(pinned = pinned)
    is Category.Income -> copy(pinned = pinned)
}

private fun Category.withArchived(archived: Boolean): Category = when (this) {
    is Category.Expense -> copy(archived = archived)
    is Category.Income -> copy(archived = archived)
}

private fun Category.withSortOrder(sortOrder: Int): Category = when (this) {
    is Category.Expense -> copy(sortOrder = sortOrder)
    is Category.Income -> copy(sortOrder = sortOrder)
}

/** Swaps the order of [a] and [b]. */
private fun LedgerData.swapping(a: Category, b: Category): LedgerData =
    copy(
        categories = categories.map {
            when (it) {
                a -> a.withSortOrder(b.sortOrder)
                b -> b.withSortOrder(a.sortOrder)
                else -> it
            }
        },
    )

/**
 * All categories of one kind in a bottom sheet: search, pick, create (an expense category with
 * its default fund), and in edit mode pin, reorder, change the default fund and archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySheet(
    ledger: Ledger,
    isExpense: Boolean,
    selected: CategoryId?,
    snackbarHostState: SnackbarHostState,
    onSelect: (CategoryId) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var editing by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.Sheet,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.sheet_title), style = AppTheme.type.month, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = !editing }) {
                    Text(
                        stringResource(if (editing) Res.string.sheet_done else Res.string.sheet_edit),
                        style = AppTheme.type.label,
                        color = colors.positive,
                    )
                }
            }
            if (editing) {
                CategoryEditList(ledger, isExpense)
            } else {
                CategoryPicker(ledger, isExpense, selected, onSelect)
            }
        }
    }
}

@Composable
private fun CategoryPicker(ledger: Ledger, isExpense: Boolean, selected: CategoryId?, onSelect: (CategoryId) -> Unit) {
    val data = ledger.data
    val colors = AppTheme.colors
    val categoryNames = categoryNames()
    var query by remember { mutableStateOf("") }
    var newFundId by remember { mutableStateOf<FundId?>(null) }
    var showFundError by remember { mutableStateOf(false) }
    val categories: List<Category> = if (isExpense) data.activeExpenseCategories else data.activeIncomeCategories
    val matches = categories.filter { categoryNames.display(it.id).contains(query.trim(), ignoreCase = true) }
    val typedId = categoryNames.parse(query)
    val exactMatch = categories.any { it.id == typedId }

    SearchField(query, stringResource(Res.string.sheet_search)) {
        query = it
        showFundError = false
    }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (query.isNotBlank() && !exactMatch) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isExpense) {
                        Text(stringResource(Res.string.settings_category_fund), style = AppTheme.type.sectionHeader, color = colors.onSurfaceMuted)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.activeFunds.forEach { fund ->
                                FundChip(fund, fund.id == newFundId, { newFundId = fund.id })
                            }
                        }
                        if (showFundError) FieldError(stringResource(Res.string.entry_fund_error))
                    }
                    Tile(stringResource(Res.string.sheet_create, query.trim()), selected = false, pinned = false) {
                        if (isExpense && newFundId == null) {
                            showFundError = true
                            return@Tile
                        }
                        createCategory(ledger, isExpense, typedId, newFundId)
                        onSelect(typedId)
                    }
                }
            }
        }
        val groups: List<Pair<Fund?, List<Category>>> = if (isExpense) {
            matches.filterIsInstance<Category.Expense>()
                .groupBy { it.defaultFundId }
                .toList()
                .sortedBy { (fundId, _) -> data.fund(fundId)?.sortOrder ?: Int.MAX_VALUE }
                .map { (fundId, list) -> data.fund(fundId) to list }
        } else {
            listOf(null to matches)
        }
        items(groups, key = { it.first?.id?.value ?: "" }) { (fund, list) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (fund != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FundDot(fund, 8.dp)
                        Text(
                            stringResource(Res.string.sheet_group, fund.name),
                            style = AppTheme.type.caption.copy(fontWeight = AppTheme.type.sectionHeader.fontWeight),
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
                list.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { category ->
                            Tile(
                                categoryNames.display(category.id),
                                selected = category.id == selected,
                                pinned = isExpense && category.pinned,
                                modifier = Modifier.weight(1f),
                            ) { onSelect(category.id) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Adds the category [id], or brings it back if it was archived. */
internal fun createCategory(ledger: Ledger, isExpense: Boolean, id: CategoryId, fundId: FundId?) {
    ledger.update { current ->
        val sameKind = current.categories.filter { (it is Category.Expense) == isExpense }
        val archived = sameKind.find { it.id == id }
        when {
            archived != null -> current.replacing(archived, archived.withArchived(false))
            isExpense -> current.copy(
                categories = current.categories + Category.Expense(
                    id, pinned = false, sortOrder = nextSortOrder(sameKind.map { it.sortOrder }), archived = false,
                    defaultFundId = requireNotNull(fundId),
                ),
            )
            else -> current.copy(
                categories = current.categories + Category.Income(
                    id, pinned = false, sortOrder = nextSortOrder(sameKind.map { it.sortOrder }), archived = false,
                ),
            )
        }
    }
}

@Composable
internal fun CategoryEditList(ledger: Ledger, isExpense: Boolean, modifier: Modifier = Modifier) {
    val data = ledger.data
    val colors = AppTheme.colors
    val categoryNames = categoryNames()
    var expanded by remember { mutableStateOf<CategoryId?>(null) }
    var lastArchived by remember { mutableStateOf<Category?>(null) }
    var pinLimitHit by remember { mutableStateOf(false) }
    val categories: List<Category> = if (isExpense) data.activeExpenseCategories else data.activeIncomeCategories

    lastArchived?.let { archived ->
        Row(
            Modifier.fillMaxWidth().background(colors.snackbar, AppShapes.Banner).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.sheet_archived, categoryNames.display(archived.id)),
                style = AppTheme.type.label,
                color = colors.onSnackbar,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                ledger.update { it.replacing(archived.withArchived(true), archived) }
                lastArchived = null
            }) { Text(stringResource(Res.string.undo), style = AppTheme.type.labelBold, color = colors.snackbarAction) }
        }
    }
    if (pinLimitHit) FieldError(stringResource(Res.string.sheet_pin_limit, GRID_CATEGORY_COUNT))
    LazyColumn(modifier.fillMaxWidth()) {
        items(categories, key = { it.id.value }) { category ->
            val index = categories.indexOf(category)
            Column {
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(enabled = category is Category.Expense) {
                            expanded = if (expanded == category.id) null else category.id
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.DragIndicator, null, Modifier.size(20.dp), tint = colors.onSurfaceMuted)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(categoryNames.display(category.id), style = AppTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (category is Category.Expense) {
                            data.fund(category.defaultFundId)?.let {
                                Text(it.name, style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                            }
                        }
                    }
                    IconButton(onClick = { categories.getOrNull(index - 1)?.let { prev -> ledger.update { it.swapping(category, prev) } } }, enabled = index > 0) {
                        Icon(AppIcons.MoveUp, stringResource(Res.string.sheet_move_up), Modifier.size(20.dp), tint = if (index > 0) colors.onSurface else colors.disabled)
                    }
                    IconButton(onClick = { categories.getOrNull(index + 1)?.let { next -> ledger.update { it.swapping(category, next) } } }, enabled = index < categories.lastIndex) {
                        Icon(AppIcons.MoveDown, stringResource(Res.string.sheet_move_down), Modifier.size(20.dp), tint = if (index < categories.lastIndex) colors.onSurface else colors.disabled)
                    }
                    if (isExpense) {
                        IconButton(onClick = {
                            val pinnedCount = categories.count { it.pinned }
                            if (!category.pinned && pinnedCount >= GRID_CATEGORY_COUNT) {
                                pinLimitHit = true
                            } else {
                                pinLimitHit = false
                                ledger.update { it.replacing(category, category.withPinned(!category.pinned)) }
                            }
                        }) {
                            Icon(
                                AppIcons.Pin,
                                stringResource(if (category.pinned) Res.string.sheet_unpin else Res.string.sheet_pin),
                                Modifier.size(18.dp),
                                tint = if (category.pinned) colors.onSurface else colors.dashed,
                            )
                        }
                    }
                    TextButton(onClick = {
                        ledger.update { it.replacing(category, category.withArchived(true)) }
                        lastArchived = category
                    }) { Text(stringResource(Res.string.settings_archive), style = AppTheme.type.caption, color = colors.onSurfaceMuted) }
                }
                if (expanded == category.id && category is Category.Expense) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        data.activeFunds.forEach { fund ->
                            FundChip(fund, fund.id == category.defaultFundId, {
                                ledger.update { it.replacing(category, category.copy(defaultFundId = fund.id)) }
                            })
                        }
                    }
                }
            }
        }
    }
}

/** The pill search field of the sheet. */
@Composable
private fun SearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(colors.background, AppShapes.Pill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceMuted)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AppTheme.type.body, color = colors.onSurfaceMuted)
            BasicTextField(
                value,
                onValueChange,
                singleLine = true,
                textStyle = AppTheme.type.body,
                cursorBrush = SolidColor(colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A category tile of the sheet; [pinned] ones carry a pin. */
@Composable
private fun Tile(
    label: String,
    selected: Boolean,
    pinned: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .height(48.dp)
            .clip(AppShapes.Tile)
            .background(if (selected) colors.onSurface else colors.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (pinned) {
            Icon(
                AppIcons.Pin,
                stringResource(Res.string.sheet_pinned),
                Modifier.size(12.dp),
                tint = if (selected) colors.onFill else colors.onSurface,
            )
        }
        Text(
            label,
            style = if (selected) AppTheme.type.labelBold else AppTheme.type.label,
            color = if (selected) colors.onFill else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
