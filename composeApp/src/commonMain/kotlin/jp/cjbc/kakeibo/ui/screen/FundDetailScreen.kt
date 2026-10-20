package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundIncomeSource
import jp.cjbc.kakeibo.model.MonthlyRequirement
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.balance
import jp.cjbc.kakeibo.model.categoryName
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.fundIncomeSources
import jp.cjbc.kakeibo.model.fundMovements
import jp.cjbc.kakeibo.model.incomesFor
import jp.cjbc.kakeibo.model.monthlyRequirement
import jp.cjbc.kakeibo.model.planDifference
import jp.cjbc.kakeibo.model.planned
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.fund_balance
import jp.cjbc.kakeibo.resources.fund_behind_detail
import jp.cjbc.kakeibo.resources.fund_edit
import jp.cjbc.kakeibo.resources.fund_goal_label
import jp.cjbc.kakeibo.resources.fund_incoming
import jp.cjbc.kakeibo.resources.fund_incoming_none
import jp.cjbc.kakeibo.resources.fund_movements
import jp.cjbc.kakeibo.resources.fund_movements_empty
import jp.cjbc.kakeibo.resources.fund_overdue
import jp.cjbc.kakeibo.resources.fund_plan_now
import jp.cjbc.kakeibo.resources.fund_reached
import jp.cjbc.kakeibo.resources.fund_show_in_entry
import jp.cjbc.kakeibo.resources.fund_show_in_entry_hint
import jp.cjbc.kakeibo.resources.fund_source_ask
import jp.cjbc.kakeibo.resources.fund_source_fixed
import jp.cjbc.kakeibo.resources.fund_source_percent
import jp.cjbc.kakeibo.resources.fund_source_rest
import jp.cjbc.kakeibo.resources.funds_incoming
import jp.cjbc.kakeibo.resources.funds_incoming_empty
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.AppIconButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.ListRow
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.WarningBanner
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatShortDate
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlin.math.absoluteValue
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource

/**
 * One fund: its balance (with the plan for a goal), where its money comes from, whether the entry
 * screen offers it, and its money in and out.
 */
@Composable
internal fun FundDetailScreen(ledger: Ledger, navigator: AppNavigator, route: Route.FundDetail) {
    val data = ledger.data
    val colors = AppTheme.colors
    val fund = data.fund(route.id) ?: return
    val today = today()
    val month = currentMonth()
    val balance = balance(fund, data.transactions, today)
    val sources = fundIncomeSources(data.allocationRules, fund.id)
    val movements = fundMovements(data.transactions, fund.id)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(fund.name, { navigator.back() }, stringResource(Res.string.back)) {
            AppIconButton(AppIcons.Edit, stringResource(Res.string.fund_edit)) { navigator.open(Route.FundEdit(fund.id)) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card).padding(AppSpacing.Card),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FundDot(fund, 12.dp)
                    Text(stringResource(Res.string.fund_balance), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                }
                AmountText(balance, style = AppTheme.type.balance)
                (fund.goal as? FundGoal.Target)?.let { goal -> GoalProgress(fund, goal, balance, month) }
            }
            Column(Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card)) {
                ListRow(first = true, onClick = {
                    navigator.open(Route.AllocationRules(sources.firstOrNull()?.incomeCategoryId))
                }) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.fund_incoming), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                        if (sources.isEmpty()) {
                            Text(stringResource(Res.string.fund_incoming_none), style = AppTheme.type.body)
                        }
                        sources.forEach { source ->
                            val category = data.categoryName(source.incomeCategoryId)
                            Text(
                                when (source) {
                                    is FundIncomeSource.Rest -> stringResource(Res.string.fund_source_rest, category)
                                    is FundIncomeSource.Line -> when (val line = source.line) {
                                        is RuleLine.Fixed -> stringResource(Res.string.fund_source_fixed, category, formatAmount(line.amount))
                                        is RuleLine.Percent -> stringResource(Res.string.fund_source_percent, category, "${line.percent}%")
                                        is RuleLine.Ask -> stringResource(Res.string.fund_source_ask, category)
                                    }
                                },
                                style = AppTheme.type.body.merge(AppTheme.type.numeric),
                            )
                        }
                    }
                    Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceMuted)
                }
                ListRow(first = false) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.fund_show_in_entry), style = AppTheme.type.body)
                        Text(stringResource(Res.string.fund_show_in_entry_hint), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
                    }
                    Switch(fund.showInEntry, { show ->
                        ledger.update { current ->
                            current.copy(funds = current.funds.map { if (it.id == fund.id) it.copy(showInEntry = show) else it })
                        }
                    })
                }
            }
            SectionHeader(stringResource(Res.string.fund_movements))
            Column(Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card)) {
                if (movements.isEmpty()) EmptyState(stringResource(Res.string.fund_movements_empty))
                movements.forEachIndexed { index, movement ->
                    val transaction = movement.transaction
                    ListRow(first = index == 0, onClick = { navigator.open(Route.Edit(transaction.id)) }) {
                        Text(
                            "${movement.date.month.number}/${movement.date.day}",
                            style = AppTheme.type.caption.merge(AppTheme.type.numeric),
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            when (transaction) {
                                is Transaction.Income -> stringResource(Res.string.entry_paid_from, data.categoryName(transaction.categoryId))
                                is Transaction.Expense -> transaction.memo.ifBlank { data.categoryName(transaction.categoryId) }
                            },
                            style = AppTheme.type.label,
                            modifier = Modifier.weight(1f),
                        )
                        AmountText(movement.amount, signed = true)
                    }
                }
            }
        }
    }
}

/**
 * The goal's progress bar with a tick at the current plan, the plan and goal amounts, and when
 * behind, what it takes each month to catch up.
 */
@Composable
private fun GoalProgress(fund: Fund, goal: FundGoal.Target, balance: Long, month: YearMonth) {
    val colors = AppTheme.colors
    val plan = planned(goal, month)
    val fraction = if (goal.amount > 0) (balance.toFloat() / goal.amount).coerceIn(0f, 1f) else 0f
    val planFraction = if (goal.amount > 0) (plan.toFloat() / goal.amount).coerceIn(0f, 1f) else 0f
    val fundColor = Color(fund.color.argb)
    val track = colors.track
    val tick = colors.onSurface
    val negative = colors.negative
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val barHeight = 10.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        val fill = if (balance < 0) size.width else size.width * fraction
        drawRoundRect(if (balance < 0) negative else fundColor, Offset(0f, top), Size(fill, barHeight), radius)
        val x = size.width * planFraction
        drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
    Row {
        Text(
            "↑ " + stringResource(Res.string.fund_plan_now, formatAmount(plan)),
            style = AppTheme.type.badge.merge(AppTheme.type.numeric),
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(Res.string.fund_goal_label, formatAmount(goal.amount)), style = AppTheme.type.badge.merge(AppTheme.type.numeric), color = colors.onSurfaceMuted)
    }
    val difference = planDifference(goal, balance, month)
    when (val requirement = monthlyRequirement(goal, balance, month)) {
        MonthlyRequirement.Reached -> Text(stringResource(Res.string.fund_reached), style = AppTheme.type.caption, color = colors.success)
        is MonthlyRequirement.Overdue -> WarningBanner {
            Text(stringResource(Res.string.fund_overdue, formatAmount(requirement.shortfall)), style = AppTheme.type.caption, color = colors.onWarningContainer)
        }
        is MonthlyRequirement.PerMonth -> if (difference < 0) {
            WarningBanner {
                Text(
                    stringResource(
                        Res.string.fund_behind_detail,
                        formatAmount(difference.absoluteValue),
                        formatYearMonth(goal.dueMonth),
                        requirement.months,
                        formatAmount(requirement.amount),
                    ),
                    style = AppTheme.type.caption.merge(AppTheme.type.numeric),
                    color = colors.onWarningContainer,
                )
            }
        }
    }
}

/** The incomes whose allocations are for [route]'s month, each with its split. */
@Composable
internal fun MonthIncomesScreen(ledger: Ledger, navigator: AppNavigator, route: Route.MonthIncomes) {
    val data = ledger.data
    val colors = AppTheme.colors
    val incomes = incomesFor(data.transactions, route.month)
    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.funds_incoming, formatMonth(route.month)), { navigator.back() }, stringResource(Res.string.back))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (incomes.isEmpty()) EmptyState(stringResource(Res.string.funds_incoming_empty), icon = AppIcons.Funds)
            incomes.forEach { income ->
                Column(Modifier.fillMaxWidth().background(colors.surface, AppShapes.Card)) {
                    ListRow(first = true, onClick = { navigator.open(Route.Edit(income.id)) }) {
                        Text(
                            "${data.categoryName(income.categoryId)} · ${formatShortDate(income.date)}",
                            style = AppTheme.type.bodyBold,
                            modifier = Modifier.weight(1f),
                        )
                        AmountText(income.amount, signed = true)
                    }
                    income.allocations.forEach { allocation ->
                        val fund = data.fund(allocation.fundId)
                        ListRow(first = false) {
                            Box(Modifier.width(4.dp))
                            fund?.let { FundDot(it) }
                            Text(fund?.name.orEmpty(), style = AppTheme.type.label, modifier = Modifier.weight(1f))
                            Text(formatAmount(allocation.amount), style = AppTheme.type.label.merge(AppTheme.type.numeric))
                        }
                    }
                }
            }
        }
    }
}
