package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.FundWarning
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.activeIncomeCategories
import jp.cjbc.kakeibo.model.balance
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.model.defaultBudgetMonth
import jp.cjbc.kakeibo.model.expenseCategory
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.fundWarning
import jp.cjbc.kakeibo.model.monthNet
import jp.cjbc.kakeibo.model.pressBackspace
import jp.cjbc.kakeibo.model.pressKey
import jp.cjbc.kakeibo.model.signedAmount
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.entry_all_categories
import jp.cjbc.kakeibo.resources.entry_amount_error
import jp.cjbc.kakeibo.resources.entry_backspace
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_category_error
import jp.cjbc.kakeibo.resources.entry_edit_title
import jp.cjbc.kakeibo.resources.entry_fund_default
import jp.cjbc.kakeibo.resources.entry_fund_section
import jp.cjbc.kakeibo.resources.entry_memo_chip
import jp.cjbc.kakeibo.resources.entry_month_chip
import jp.cjbc.kakeibo.resources.entry_pays_from
import jp.cjbc.kakeibo.resources.entry_puts_into
import jp.cjbc.kakeibo.resources.entry_record_key
import jp.cjbc.kakeibo.resources.entry_recorded
import jp.cjbc.kakeibo.resources.entry_save
import jp.cjbc.kakeibo.resources.entry_saved
import jp.cjbc.kakeibo.resources.entry_today
import jp.cjbc.kakeibo.resources.entry_yesterday
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.history_title
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.ok
import jp.cjbc.kakeibo.resources.settings_title
import jp.cjbc.kakeibo.resources.warning_already_over
import jp.cjbc.kakeibo.resources.warning_will_be_over
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.AppIconButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.Segmented
import jp.cjbc.kakeibo.ui.component.WarningBanner
import jp.cjbc.kakeibo.ui.component.amountColor
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatShortDate
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The number of category tiles besides "all": the pinned categories shown on the grid. */
const val GRID_CATEGORY_COUNT = 7

private const val MILLIS_PER_DAY = 86_400_000L

/** Marks where the fund's name goes in "paid from %s", so that it can be colored. */
private const val FUND_NAME_MARK = "\u0001"

internal enum class EntryKind(val label: StringResource) {
    EXPENSE(Res.string.expense),
    INCOME(Res.string.income),
}

/** What the entry screen is filling in. */
private data class EntryForm(
    val kind: EntryKind,
    val digits: String,
    val date: LocalDate,
    val categoryId: CategoryId?,
    /** The fund the user picked; `null` follows the category's default. */
    val fundId: FundId?,
    val memo: String,
) {
    val amount: Long get() = digits.toLongOrNull() ?: 0

    companion object {
        fun empty() = EntryForm(EntryKind.EXPENSE, "", today(), null, null, "")

        fun of(transaction: Transaction) = when (transaction) {
            is Transaction.Expense -> EntryForm(
                EntryKind.EXPENSE, transaction.amount.toString(), transaction.date, transaction.categoryId,
                transaction.fundId, transaction.memo,
            )
            is Transaction.Income -> EntryForm(
                EntryKind.INCOME, transaction.amount.toString(), transaction.date, transaction.categoryId,
                transaction.allocations.singleOrNull()?.fundId, transaction.memo,
            )
        }
    }
}

/**
 * The record screen, the app's start screen: a keypad entry of an expense paid from a fund or an
 * income put into a fund. With [editing], it edits that transaction instead and goes back when
 * saved.
 */
@Composable
internal fun EntryScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    editing: TransactionId?,
) {
    val data = ledger.data
    val categoryNames = categoryNames()
    val scope = rememberCoroutineScope()
    val original = editing?.let { id -> data.transactions.find { it.id == id } }
    var form by remember(editing) { mutableStateOf(original?.let(EntryForm::of) ?: EntryForm.empty()) }
    var showErrors by remember(editing) { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }

    val categories: List<Category> = when (form.kind) {
        EntryKind.EXPENSE -> data.activeExpenseCategories
        EntryKind.INCOME -> data.activeIncomeCategories
    }
    val gridCategories = when (form.kind) {
        EntryKind.EXPENSE -> categories.filter { it.pinned }.take(GRID_CATEGORY_COUNT)
        EntryKind.INCOME -> categories.take(GRID_CATEGORY_COUNT)
    }
    val categoryId = form.categoryId?.takeIf { id -> categories.any { it.id == id } }
        ?: gridCategories.firstOrNull()?.id
        ?: categories.firstOrNull()?.id
    val defaultFundId = categoryId?.let { data.expenseCategory(it) }?.defaultFundId
        ?.takeIf { form.kind == EntryKind.EXPENSE }
    val fundId = form.fundId ?: defaultFundId ?: data.activeFunds.firstOrNull()?.id
    val fund = fundId?.let(data::fund)
    // The chips list the funds shown on the entry screen, plus the ones in use here, with the
    // category's default first so that it is in view.
    val chipFunds = data.activeFunds
        .filter { it.showInEntry || it.id == fundId || it.id == defaultFundId }
        .sortedByDescending { it.id == defaultFundId }
    val otherTransactions = data.transactions.filter { it.id != editing }

    fun record() {
        if (form.amount <= 0 || categoryId == null || fundId == null) {
            showErrors = true
            return
        }
        val id = editing ?: TransactionId(generateId())
        val transaction = when (form.kind) {
            EntryKind.EXPENSE -> Transaction.Expense(
                id = id,
                date = form.date,
                amount = form.amount,
                categoryId = categoryId,
                memo = form.memo.trim(),
                fundId = fundId,
                recurringItemId = (original as? Transaction.Expense)?.recurringItemId,
            )
            // Until the allocation screen exists, an income goes wholly into one fund.
            EntryKind.INCOME -> Transaction.Income(
                id = id,
                date = form.date,
                amount = form.amount,
                categoryId = categoryId,
                memo = form.memo.trim(),
                budgetMonth = (original as? Transaction.Income)?.budgetMonth ?: defaultBudgetMonth(form.date),
                allocations = listOf(Allocation(fundId, form.amount)),
            )
        }
        if (editing != null) {
            ledger.update { current ->
                current.copy(transactions = current.transactions.map { if (it.id == editing) transaction else it })
            }
            navigator.back()
            scope.launch { snackbarHostState.showSnackbar(getString(Res.string.entry_saved)) }
        } else {
            ledger.addTransaction(transaction)
            val label = transaction.memo.ifBlank { categoryNames.display(transaction.categoryId) }
            val amount = formatAmount(transaction.signedAmount, signed = true)
            form = form.copy(digits = "", memo = "")
            showErrors = false
            scope.launch { snackbarHostState.showSnackbar(getString(Res.string.entry_recorded, "$label $amount")) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (editing != null) {
            AppTopBar(stringResource(Res.string.entry_edit_title), navigator::back, stringResource(Res.string.back))
        } else {
            EntryTopBar(
                net = monthNet(data.transactions, currentMonth()),
                onMonthClick = { navigator.open(Route.History(currentMonth())) },
                onHistoryClick = { navigator.open(Route.History()) },
                onSettingsClick = { navigator.open(Route.Settings) },
            )
        }
        Column(
            Modifier.weight(1f).padding(horizontal = AppSpacing.Screen).padding(top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val kindLabels = EntryKind.entries.associateWith { stringResource(it.label) }
                Segmented(
                    EntryKind.entries,
                    form.kind,
                    { form = form.copy(kind = it, categoryId = null, fundId = null) },
                ) { kindLabels.getValue(it) }
                Spacer(Modifier.weight(1f))
                DateChip(form.date) { showDatePicker = true }
            }
            AmountCard(
                form = form,
                categoryName = categoryId?.let(categoryNames::display).orEmpty(),
                fund = fund,
                onMemoClick = { showMemoDialog = true },
            )
            if (showErrors && form.amount <= 0) FieldError(stringResource(Res.string.entry_amount_error))
            Section(stringResource(Res.string.entry_category)) {
                CategoryGrid(
                    tiles = gridCategories.map { it.id },
                    selected = categoryId,
                    total = categories.size,
                    name = categoryNames::display,
                    onSelect = { form = form.copy(categoryId = it, fundId = null) },
                    onAll = { showCategorySheet = true },
                )
                if (showErrors && categoryId == null) FieldError(stringResource(Res.string.entry_category_error))
            }
            Section(stringResource(Res.string.entry_fund_section)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val defaultBadge = stringResource(Res.string.entry_fund_default)
                    chipFunds.forEach {
                        FundChip(
                            it,
                            selected = it.id == fundId,
                            onClick = { form = form.copy(fundId = it.id) },
                            badge = if (it.id == defaultFundId) defaultBadge else null,
                        )
                    }
                }
                if (form.kind == EntryKind.EXPENSE && fund != null) {
                    fundWarning(balance(fund, otherTransactions, today()), form.amount)?.let { warning ->
                        FundWarningBanner(fund, warning)
                    }
                }
            }
            Keypad(
                recordLabel = stringResource(if (editing != null) Res.string.entry_save else Res.string.entry_record_key),
                onKey = { form = form.copy(digits = pressKey(form.digits, it)) },
                onBackspace = { form = form.copy(digits = pressBackspace(form.digits)) },
                onRecord = ::record,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showCategorySheet) {
        CategorySheet(
            ledger = ledger,
            isExpense = form.kind == EntryKind.EXPENSE,
            selected = categoryId,
            snackbarHostState = snackbarHostState,
            onSelect = {
                form = form.copy(categoryId = it, fundId = null)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
        )
    }
    if (showDatePicker) {
        EntryDatePicker(form.date, onPick = { form = form.copy(date = it) }, onDismiss = { showDatePicker = false })
    }
    if (showMemoDialog) {
        MemoDialog(
            form.memo,
            onDone = {
                form = form.copy(memo = it)
                showMemoDialog = false
            },
            onDismiss = { showMemoDialog = false },
        )
    }
}

/** The top bar: this month's net as a chip, history and settings. */
@Composable
private fun EntryTopBar(net: Long, onMonthClick: () -> Unit, onHistoryClick: () -> Unit, onSettingsClick: () -> Unit) {
    val colors = AppTheme.colors
    val chipLabel = stringResource(Res.string.entry_month_chip)
    Row(
        Modifier.fillMaxWidth().height(AppSpacing.TopBar).padding(start = AppSpacing.Screen, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(44.dp)
                .clip(AppShapes.Pill)
                .background(colors.surface)
                .border(1.dp, colors.outline, AppShapes.Pill)
                .clickable(role = Role.Button, onClick = onMonthClick)
                .semantics { contentDescription = chipLabel }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(formatMonth(currentMonth()), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
            Text(
                formatAmount(net, signed = true),
                style = AppTheme.type.listAmount.merge(AppTheme.type.body.copy(fontWeight = FontWeight.Bold)),
                color = amountColor(net, signed = true),
            )
        }
        Spacer(Modifier.weight(1f))
        AppIconButton(AppIcons.History, stringResource(Res.string.history_title), onHistoryClick)
        AppIconButton(AppIcons.Settings, stringResource(Res.string.settings_title), onSettingsClick)
    }
}

/** The date chip: "Today 10/9 (Fri)", opening a date picker. */
@Composable
private fun DateChip(date: LocalDate, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val today = today()
    val relative = when (date) {
        today -> stringResource(Res.string.entry_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.entry_yesterday)
        else -> null
    }
    Row(
        Modifier
            .height(44.dp)
            .clip(AppShapes.Pill)
            .background(colors.surface)
            .border(1.dp, colors.outline, AppShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.Calendar, null, Modifier.size(18.dp), tint = colors.onSurface)
        relative?.let { Text(it, style = AppTheme.type.label) }
        Text(
            formatShortDate(date),
            style = AppTheme.type.label.merge(AppTheme.type.numeric),
            color = if (relative != null) colors.onSurfaceMuted else colors.onSurface,
        )
    }
}

/** The amount, right-aligned and large, under "category · paid from fund" and the memo chip. */
@Composable
private fun AmountCard(form: EntryForm, categoryName: String, fund: Fund?, onMemoClick: () -> Unit) {
    val colors = AppTheme.colors
    val signed = when (form.kind) {
        EntryKind.EXPENSE -> -form.amount
        EntryKind.INCOME -> form.amount
    }
    val fundTemplate = stringResource(
        if (form.kind == EntryKind.EXPENSE) Res.string.entry_pays_from else Res.string.entry_puts_into,
        FUND_NAME_MARK,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, AppShapes.Card)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(categoryName)
                    if (fund != null) {
                        append(" · ")
                        val parts = fundTemplate.split(FUND_NAME_MARK, limit = 2)
                        append(parts[0])
                        withStyle(SpanStyle(color = Color(fund.color.argb), fontWeight = FontWeight.Bold)) {
                            append(fund.name)
                        }
                        append(parts.getOrElse(1) { "" })
                    }
                },
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .clip(AppShapes.Pill)
                    .background(colors.background)
                    .clickable(role = Role.Button, onClick = onMemoClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AppIcons.Edit, null, Modifier.size(14.dp), tint = colors.onSurface)
                Text(
                    form.memo.ifBlank { stringResource(Res.string.entry_memo_chip) },
                    style = AppTheme.type.caption,
                    color = if (form.memo.isBlank()) colors.onSurfaceMuted else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        Text(
            formatAmount(signed, signed = true),
            style = AppTheme.type.entryAmount,
            color = if (signed == 0L) colors.onSurfaceMuted else amountColor(signed, signed = true),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = AppTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        content()
    }
}

/**
 * The four-column category grid: up to [GRID_CATEGORY_COUNT] tiles and "All (n)", always eight
 * slots, so the layout does not move as categories are added.
 */
@Composable
private fun CategoryGrid(
    tiles: List<CategoryId>,
    selected: CategoryId?,
    total: Int,
    name: (CategoryId) -> String,
    onSelect: (CategoryId) -> Unit,
    onAll: () -> Unit,
) {
    val colors = AppTheme.colors
    val slots: List<CategoryId?> = tiles + List(GRID_CATEGORY_COUNT - tiles.size) { null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (slots + listOf(null)).chunked(4).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { column, id ->
                    val modifier = Modifier.weight(1f).height(48.dp)
                    when {
                        row == 1 && column == 3 -> AllCategoriesTile(total, onAll, modifier)
                        id == null -> Spacer(modifier)
                        else -> {
                            val isSelected = id == selected
                            Box(
                                modifier
                                    .clip(AppShapes.Tile)
                                    .background(if (isSelected) colors.onSurface else colors.surface)
                                    .clickable(role = Role.Button) { onSelect(id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    name(id),
                                    style = if (isSelected) AppTheme.type.labelBold else AppTheme.type.label,
                                    color = if (isSelected) colors.onFill else colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The "All (n)" tile with a dashed border, opening the category sheet. */
@Composable
private fun AllCategoriesTile(total: Int, onClick: () -> Unit, modifier: Modifier) {
    val dashed = AppTheme.colors.dashed
    Box(modifier.clip(AppShapes.Tile).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = dashed,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                ),
            )
        }
        Text(stringResource(Res.string.entry_all_categories, total), style = AppTheme.type.caption, maxLines = 1)
    }
}

@Composable
private fun FundWarningBanner(fund: Fund, warning: FundWarning) {
    WarningBanner {
        Text(
            when (warning) {
                is FundWarning.AlreadyOver -> stringResource(
                    Res.string.warning_already_over, fund.name, formatAmount(warning.over), formatAmount(warning.overAfter),
                )
                is FundWarning.WillBeOver -> stringResource(
                    Res.string.warning_will_be_over, fund.name, formatAmount(warning.left), formatAmount(warning.overAfter),
                )
            },
            style = AppTheme.type.caption.merge(AppTheme.type.numeric),
            color = AppTheme.colors.onWarningContainer,
        )
    }
}

/** 1–9, 00, 0, 000, backspace and a three-row "record" key. */
@Composable
private fun Keypad(
    recordLabel: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "000"))
    Row(modifier.fillMaxWidth().heightIn(min = 180.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { keys ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(AppShapes.Tile)
                                .background(colors.surface)
                                .clickable(role = Role.Button) { onKey(key) },
                            contentAlignment = Alignment.Center,
                        ) { Text(key, style = AppTheme.type.keypad) }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val backspaceLabel = stringResource(Res.string.entry_backspace)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AppShapes.Tile)
                    .background(colors.surfaceMuted)
                    .clickable(role = Role.Button, onClick = onBackspace)
                    .semantics { contentDescription = backspaceLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Backspace, null, Modifier.size(24.dp), tint = colors.onSurface) }
            Column(
                Modifier
                    .weight(3f)
                    .fillMaxWidth()
                    .clip(AppShapes.Tile)
                    .background(colors.onSurface)
                    .clickable(role = Role.Button, onClick = onRecord),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(AppIcons.Check, null, Modifier.size(26.dp), tint = colors.onFill)
                Text(recordLabel, style = AppTheme.type.bodyBold, color = colors.onFill)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePicker(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDays() * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.fromEpochDays(it / MILLIS_PER_DAY)) }
                onDismiss()
            }) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) { DatePicker(state) }
}

@Composable
private fun MemoDialog(memo: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(memo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.entry_memo_chip)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, textStyle = AppTheme.type.body) },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(stringResource(Res.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
