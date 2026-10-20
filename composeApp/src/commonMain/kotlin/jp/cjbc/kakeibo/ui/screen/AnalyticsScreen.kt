package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.PurchasePattern
import jp.cjbc.kakeibo.model.Share
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.available
import jp.cjbc.kakeibo.model.balance
import jp.cjbc.kakeibo.model.categoryName
import jp.cjbc.kakeibo.model.categorySpendingByFund
import jp.cjbc.kakeibo.model.cumulativeBalances
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.model.endBalance
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.fundSpendingByCategory
import jp.cjbc.kakeibo.model.monthTotals
import jp.cjbc.kakeibo.model.monthsEndingWith
import jp.cjbc.kakeibo.model.purchasePatterns
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.analytics_balance_trend
import jp.cjbc.kakeibo.resources.analytics_by_category
import jp.cjbc.kakeibo.resources.analytics_by_fund
import jp.cjbc.kakeibo.resources.analytics_category_months
import jp.cjbc.kakeibo.resources.analytics_empty
import jp.cjbc.kakeibo.resources.analytics_from_funds
import jp.cjbc.kakeibo.resources.analytics_fund_breakdown
import jp.cjbc.kakeibo.resources.analytics_fund_left
import jp.cjbc.kakeibo.resources.analytics_last12
import jp.cjbc.kakeibo.resources.analytics_month_end
import jp.cjbc.kakeibo.resources.analytics_month_end_hint
import jp.cjbc.kakeibo.resources.analytics_monthly
import jp.cjbc.kakeibo.resources.analytics_no_patterns
import jp.cjbc.kakeibo.resources.analytics_no_spending
import jp.cjbc.kakeibo.resources.analytics_overall
import jp.cjbc.kakeibo.resources.analytics_partial
import jp.cjbc.kakeibo.resources.analytics_pattern_avg
import jp.cjbc.kakeibo.resources.analytics_pattern_line
import jp.cjbc.kakeibo.resources.analytics_pattern_line_short
import jp.cjbc.kakeibo.resources.analytics_repeated
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.tab_analytics
import jp.cjbc.kakeibo.ui.component.AmountBar
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.LabeledChart
import jp.cjbc.kakeibo.ui.component.LineArea
import jp.cjbc.kakeibo.ui.component.PairedBars
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.Segmented
import jp.cjbc.kakeibo.ui.component.ShareBar
import jp.cjbc.kakeibo.ui.component.StackedBars
import jp.cjbc.kakeibo.ui.component.ZeroCenteredBars
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class AnalyticsView(val label: StringResource) {
    BY_FUND(Res.string.analytics_by_fund),
    BY_CATEGORY(Res.string.analytics_by_category),
    OVERALL(Res.string.analytics_overall),
}

private const val MAX_SHOWN_PATTERNS = 5

/** The analytics tab: the last 12 months by fund, by category, or overall. */
@Composable
internal fun AnalyticsScreen(ledger: Ledger) {
    val data = ledger.data
    var view by rememberSaveable { mutableStateOf(AnalyticsView.BY_FUND) }
    val months = monthsEndingWith(currentMonth())
    val labels = months.map { it.month.number.toString() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.tab_analytics), onBack = null)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val viewLabels = AnalyticsView.entries.associateWith { stringResource(it.label) }
            Segmented(AnalyticsView.entries, view, { view = it }, Modifier.fillMaxWidth(), equalWidths = true) {
                viewLabels.getValue(it)
            }
            if (data.transactions.isEmpty()) {
                EmptyState(stringResource(Res.string.analytics_empty), icon = AppIcons.Analytics)
            } else {
                when (view) {
                    AnalyticsView.BY_FUND -> ByFund(data, months, labels)
                    AnalyticsView.BY_CATEGORY -> ByCategory(data, months, labels)
                    AnalyticsView.OVERALL -> Overall(data, months, labels)
                }
            }
        }
    }
}

/** A row of chips that scrolls sideways. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** The title line of a card, with an optional muted note at its end. */
@Composable
private fun CardTitle(title: String, note: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AppTheme.type.labelBold, color = AppTheme.colors.onSurface)
        if (note != null) {
            Text(
                note,
                style = AppTheme.type.caption,
                color = AppTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

/** A whole percentage of [amount] in [total]. */
private fun percent(amount: Long, total: Long): String = if (total <= 0) "0%" else "${amount * 100 / total}%"

@Composable
private fun ByFund(data: LedgerData, months: List<YearMonth>, labels: List<String>) {
    val funds = data.activeFunds
    if (funds.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(funds.first().id.value) }
    val fund = funds.find { it.id.value == selectedId } ?: funds.first()
    val colors = AppTheme.colors
    val fundColor = Color(fund.color.argb)
    val month = months.last()
    val transactions = data.transactions

    ChipRow { funds.forEach { FundChip(it, it.id == fund.id, { selectedId = it.id.value }) } }

    val left = balance(fund, transactions, today())
    val available = available(fund, transactions, month)
    SectionCard {
        Text(
            stringResource(Res.string.analytics_fund_left, fund.name, formatMonth(month)),
            style = AppTheme.type.caption,
            color = colors.onSurfaceMuted,
        )
        Text(
            formatAmount(left),
            style = AppTheme.type.balance,
            color = if (left < 0) colors.negative else colors.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        AmountBar(left, available, fundColor, height = 8.dp)
    }
    SectionCard {
        CardTitle(stringResource(Res.string.analytics_month_end), stringResource(Res.string.analytics_month_end_hint))
        Spacer(Modifier.height(12.dp))
        LabeledChart(labels) {
            ZeroCenteredBars(months.map { endBalance(fund, transactions, it) }, fundColor, colors.negative)
        }
    }
    SectionCard {
        CardTitle(stringResource(Res.string.analytics_fund_breakdown, formatMonth(month)))
        Spacer(Modifier.height(8.dp))
        val shares = fundSpendingByCategory(transactions, fund.id, month)
        if (shares.isEmpty()) {
            Text(stringResource(Res.string.analytics_no_spending), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
        }
        val total = shares.sumOf { it.amount }
        shares.forEachIndexed { index, share ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(data.categoryName(share.key), style = AppTheme.type.label, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Text(formatAmount(share.amount), style = AppTheme.type.labelBold.merge(AppTheme.type.numeric), color = colors.onSurface)
                    Text(
                        percent(share.amount, total),
                        style = AppTheme.type.caption.merge(AppTheme.type.numeric),
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(start = 6.dp).width(36.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                AmountBar(share.amount, total, fundColor.copy(alpha = (1f - index * 0.2f).coerceAtLeast(0.3f)))
            }
        }
    }
}

@Composable
private fun ByCategory(data: LedgerData, months: List<YearMonth>, labels: List<String>) {
    val categories = data.activeExpenseCategories
    if (categories.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(categories.first().id.value) }
    val category = categories.find { it.id.value == selectedId } ?: categories.first()
    val colors = AppTheme.colors
    val month = months.last()
    val transactions = data.transactions
    fun fundColor(id: FundId): Color = data.fund(id)?.let { Color(it.color.argb) } ?: colors.disabled

    ChipRow {
        categories.forEach { chip ->
            val selected = chip.id == category.id
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .background(if (selected) colors.onSurface else colors.surface, AppShapes.Pill)
                    .border(if (selected) 0.dp else 1.dp, if (selected) Color.Transparent else colors.outline, AppShapes.Pill)
                    .clickable(role = Role.Button) { selectedId = chip.id.value }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    chip.name,
                    style = if (selected) AppTheme.type.labelBold else AppTheme.type.label,
                    color = if (selected) colors.surface else colors.onSurface,
                )
            }
        }
    }

    val shares = categorySpendingByFund(transactions, category.id, month)
    val total = shares.sumOf { it.amount }
    SectionCard {
        CardTitle(
            "${category.name} · ${formatMonth(month)}",
            if (shares.size > 1) stringResource(Res.string.analytics_from_funds, shares.size) else null,
        )
        Text(formatAmount(total), style = AppTheme.type.balance, color = colors.onSurface)
        if (shares.isEmpty()) {
            Text(stringResource(Res.string.analytics_no_spending), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
        } else {
            Spacer(Modifier.height(8.dp))
            ShareBar(shares.map { fundColor(it.key) to it.amount })
            Spacer(Modifier.height(4.dp))
            shares.forEach { share -> FundShareRow(data.fund(share.key), share, total) }
        }
    }
    SectionCard {
        CardTitle(stringResource(Res.string.analytics_category_months))
        Spacer(Modifier.height(12.dp))
        val inCategory = transactions.filterIsInstance<Transaction.Expense>().filter { it.categoryId == category.id }
        val fundOrder = data.funds.map { it.id }
        LabeledChart(labels) {
            StackedBars(
                months.map { m ->
                    inCategory.filter { it.date.yearMonth == m }
                        .groupBy { it.fundId }
                        .entries.sortedBy { fundOrder.indexOf(it.key) }
                        .map { (fundId, list) -> fundColor(fundId) to list.sumOf { it.amount } }
                },
            )
        }
    }
    Patterns(data, purchasePatterns(transactions.filter { it.categoryId == category.id }), showFund = true)
}

@Composable
private fun FundShareRow(fund: Fund?, share: Share<FundId>, total: Long) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (fund != null) FundDot(fund) else Spacer(Modifier.size(10.dp))
        Text(
            fund?.name ?: share.key.value,
            style = AppTheme.type.label,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(formatAmount(share.amount), style = AppTheme.type.labelBold.merge(AppTheme.type.numeric), color = colors.onSurface)
        Text(
            percent(share.amount, total),
            style = AppTheme.type.caption.merge(AppTheme.type.numeric),
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(start = 6.dp).width(36.dp),
        )
    }
}

@Composable
private fun Overall(data: LedgerData, months: List<YearMonth>, labels: List<String>) {
    val colors = AppTheme.colors
    val transactions = data.transactions
    val totals = monthTotals(transactions, months)
    SectionCard {
        CardTitle(stringResource(Res.string.analytics_monthly), stringResource(Res.string.analytics_last12))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(colors.positive, stringResource(Res.string.income))
            Legend(colors.negative, stringResource(Res.string.expense))
        }
        Spacer(Modifier.height(12.dp))
        LabeledChart(labels) {
            PairedBars(totals.map { it.income }, totals.map { it.expense }, colors.positive, colors.negative)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.analytics_partial, formatMonth(months.last())),
                style = AppTheme.type.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            val net = totals.last().net
            Text(
                formatAmount(net, signed = true),
                style = AppTheme.type.listAmount,
                color = if (net < 0) colors.negative else colors.positive,
            )
        }
    }
    SectionCard {
        val balances = cumulativeBalances(transactions, months)
        CardTitle(stringResource(Res.string.analytics_balance_trend))
        Text(formatAmount(balances.last()), style = AppTheme.type.balance, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        LineArea(balances, colors.onSurface)
        Row(Modifier.fillMaxWidth()) {
            Text(formatYearMonth(months.first()), style = AppTheme.type.badge, color = colors.onSurfaceMuted, modifier = Modifier.weight(1f))
            Text(formatYearMonth(months.last()), style = AppTheme.type.badge, color = colors.onSurfaceMuted)
        }
    }
    Patterns(data, purchasePatterns(transactions), showFund = false)
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The expenses recorded repeatedly, with the fund paying them when [showFund]. */
@Composable
private fun Patterns(data: LedgerData, patterns: List<PurchasePattern>, showFund: Boolean) {
    val colors = AppTheme.colors
    SectionCard {
        CardTitle(stringResource(Res.string.analytics_repeated))
        Spacer(Modifier.height(4.dp))
        if (patterns.isEmpty()) {
            Text(stringResource(Res.string.analytics_no_patterns), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
        }
        patterns.take(MAX_SHOWN_PATTERNS).forEach { pattern ->
            val fund = data.fund(pattern.fundId)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showFund && fund != null) {
                    FundDot(fund)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(pattern.memo, style = AppTheme.type.bodyBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (showFund && fund != null) {
                            stringResource(
                                Res.string.analytics_pattern_line,
                                pattern.intervalDays,
                                pattern.count,
                                stringResource(Res.string.entry_paid_from, fund.name),
                            )
                        } else {
                            stringResource(Res.string.analytics_pattern_line_short, pattern.intervalDays, pattern.count)
                        },
                        style = AppTheme.type.caption,
                        color = colors.onSurfaceMuted,
                    )
                }
                Text(
                    stringResource(Res.string.analytics_pattern_avg, formatAmount(pattern.averageAmount)),
                    style = AppTheme.type.label.merge(AppTheme.type.numeric).copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
            }
        }
    }
}
