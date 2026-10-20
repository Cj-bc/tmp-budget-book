package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.Day
import jp.cjbc.kakeibo.model.HistoryFilter
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.categoryKey
import jp.cjbc.kakeibo.model.categoryName
import jp.cjbc.kakeibo.model.expenseCategory
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.fundIds
import jp.cjbc.kakeibo.model.groupByDay
import jp.cjbc.kakeibo.model.key
import jp.cjbc.kakeibo.model.signedAmount
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.deleted_message
import jp.cjbc.kakeibo.resources.entry_auto_badge
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.history_all
import jp.cjbc.kakeibo.resources.history_category
import jp.cjbc.kakeibo.resources.history_clear_filters
import jp.cjbc.kakeibo.resources.history_day_today
import jp.cjbc.kakeibo.resources.history_edit
import jp.cjbc.kakeibo.resources.history_empty
import jp.cjbc.kakeibo.resources.history_empty_filtered
import jp.cjbc.kakeibo.resources.history_fund
import jp.cjbc.kakeibo.resources.history_search
import jp.cjbc.kakeibo.resources.history_search_hint
import jp.cjbc.kakeibo.resources.history_title
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.AppChip
import jp.cjbc.kakeibo.ui.component.AppIconButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.SwipeToDelete
import jp.cjbc.kakeibo.ui.component.amountColor
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatLongDate
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Every record, grouped by day: filters by fund, category and income, a search, tap to edit and
 * swipe to delete with undo.
 */
@Composable
internal fun HistoryScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    route: Route.History,
) {
    val data = ledger.data
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var filter by remember(route) { mutableStateOf(HistoryFilter(month = route.month)) }
    var searching by remember { mutableStateOf(false) }
    val days = groupByDay(data.transactions.filter { filter.matches(it, data::categoryName) })

    fun delete(transaction: Transaction) {
        val removed = ledger.deleteTransaction(transaction.id) ?: return
        scope.launch {
            val label = transaction.memo.ifBlank { data.categoryName(transaction.categoryKey) }
            val result = snackbarHostState.showSnackbar(
                getString(Res.string.deleted_message, "$label ${formatAmount(transaction.signedAmount, signed = true)}"),
                actionLabel = getString(Res.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) ledger.addTransaction(removed)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.history_title), navigator::back, stringResource(Res.string.back)) {
            AppIconButton(AppIcons.Search, stringResource(Res.string.history_search)) {
                searching = !searching
                if (!searching) filter = filter.copy(query = "")
            }
        }
        FilterChips(data.activeFunds, data.categories, filter) { filter = it }
        if (searching) {
            SearchBox(filter.query, stringResource(Res.string.history_search_hint)) { filter = filter.copy(query = it) }
        }
        if (days.isEmpty()) {
            val filtered = data.transactions.isNotEmpty()
            EmptyState(
                stringResource(if (filtered) Res.string.history_empty_filtered else Res.string.history_empty),
                icon = AppIcons.History,
                action = if (filtered && filter != HistoryFilter()) stringResource(Res.string.history_clear_filters) else null,
                onAction = {
                    filter = HistoryFilter()
                    searching = false
                },
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = AppSpacing.Screen, end = AppSpacing.Screen, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(days, key = { it.date.toString() }) { day ->
                DayGroup(
                    day = day,
                    onEdit = { navigator.open(Route.Edit(it.id)) },
                    onDelete = ::delete,
                    row = { transaction ->
                        val fund = transaction.fundIds.firstOrNull()?.let(data::fund)
                        val category = data.categoryName(transaction.categoryKey)
                        val movedFrom = (transaction as? Transaction.Expense)?.takeIf {
                            data.expenseCategory(it.categoryId)?.defaultFundId != it.fundId
                        }?.let { fund?.name }
                        TransactionRowContent(
                            memo = transaction.memo.ifBlank { category },
                            category = category,
                            barColor = if (transaction is Transaction.Income) colors.positive else fund?.let { Color(it.color.argb) } ?: colors.outlineStrong,
                            isAuto = (transaction as? Transaction.Expense)?.recurringItemId != null,
                            movedFrom = movedFrom,
                            amount = transaction.signedAmount,
                        )
                    },
                )
            }
        }
    }
}

/** すべて / 資金 ▾ / カテゴリ ▾ / 収入, and the month when the history is limited to one. */
@Composable
private fun FilterChips(
    funds: List<jp.cjbc.kakeibo.model.Fund>,
    categories: List<Category>,
    filter: HistoryFilter,
    onChange: (HistoryFilter) -> Unit,
) {
    var fundMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.month?.let { month ->
            AppChip(formatYearMonth(month), selected = true, onClick = { onChange(filter.copy(month = null)) }, trailingIcon = AppIcons.Close)
        }
        AppChip(stringResource(Res.string.history_all), selected = filter.isEmpty, onClick = { onChange(HistoryFilter(month = filter.month)) })
        Box {
            val fund = filter.fundId?.let { id -> funds.find { it.id == id } }
            AppChip(
                fund?.name ?: stringResource(Res.string.history_fund),
                selected = fund != null,
                onClick = { fundMenu = true },
                trailingIcon = AppIcons.ExpandMore,
            )
            DropdownMenu(fundMenu, { fundMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.history_all)) }, onClick = {
                    onChange(filter.copy(fundId = null))
                    fundMenu = false
                })
                funds.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.name) },
                        leadingIcon = { FundDot(f) },
                        onClick = {
                            onChange(filter.copy(fundId = f.id))
                            fundMenu = false
                        },
                    )
                }
            }
        }
        Box {
            AppChip(
                filter.category?.let { key -> categories.find { it.key == key }?.name ?: key.id.value } ?: stringResource(Res.string.history_category),
                selected = filter.category != null,
                onClick = { categoryMenu = true },
                trailingIcon = AppIcons.ExpandMore,
            )
            DropdownMenu(categoryMenu, { categoryMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.history_all)) }, onClick = {
                    onChange(filter.copy(category = null))
                    categoryMenu = false
                })
                val incomeLabel = stringResource(Res.string.income)
                categories.filterNot { it.archived }.sortedWith(compareBy({ it is Category.Income }, { it.sortOrder })).forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.name + if (c is Category.Income) "（$incomeLabel）" else "") },
                        onClick = {
                            onChange(filter.copy(category = c.key))
                            categoryMenu = false
                        },
                    )
                }
            }
        }
        AppChip(
            stringResource(Res.string.income),
            selected = filter.incomeOnly,
            onClick = { onChange(filter.copy(incomeOnly = !filter.incomeOnly)) },
        )
    }
}

@Composable
private fun SearchBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = AppSpacing.Screen)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface, AppShapes.Pill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceMuted)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AppTheme.type.body, color = colors.onSurfaceMuted)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = AppTheme.type.body, cursorBrush = SolidColor(colors.onSurface), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A day heading with the day's net, over a card of its records. */
@Composable
private fun DayGroup(
    day: Day,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    row: @Composable (Transaction) -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val date = formatLongDate(day.date)
            Text(
                if (day.date == today()) stringResource(Res.string.history_day_today, date) else date,
                style = AppTheme.type.sectionHeader,
                color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(formatAmount(day.net, signed = true), style = AppTheme.type.caption.merge(AppTheme.type.numeric), color = colors.onSurfaceMuted)
        }
        Column(Modifier.fillMaxWidth().clip(AppShapes.DayGroup).background(colors.surface)) {
            day.transactions.forEachIndexed { index, transaction ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                // Keyed, so that a deleted row's swipe state does not pass to the row taking its place.
                key(transaction.id.value) {
                    SwipeToDelete(onDelete = { onDelete(transaction) }) {
                        val editLabel = stringResource(Res.string.history_edit, transaction.memo)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .clickable(role = Role.Button) { onEdit(transaction) }
                                .semantics { contentDescription = editLabel },
                        ) { row(transaction) }
                    }
                }
            }
        }
    }
}

/**
 * A record: a bar of its fund's color, the memo with an "auto" badge for recurring items, the
 * category with "→ from X" when paid from other than the default fund, and the signed amount.
 */
@Composable
private fun TransactionRowContent(
    memo: String,
    category: String,
    barColor: Color,
    isAuto: Boolean,
    movedFrom: String?,
    amount: Long,
) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(34.dp).background(barColor, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(memo, style = AppTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isAuto) {
                    Text(
                        stringResource(Res.string.entry_auto_badge),
                        style = AppTheme.type.badge,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceMuted)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category, style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                movedFrom?.let {
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(colors.surfaceMuted).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(AppIcons.Forward, null, Modifier.size(11.dp), tint = colors.onSurface)
                        Text(
                            stringResource(Res.string.entry_paid_from, it),
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                        )
                    }
                }
            }
        }
        Text(formatAmount(amount, signed = true), style = AppTheme.type.listAmount, color = amountColor(amount, signed = true))
    }
}
