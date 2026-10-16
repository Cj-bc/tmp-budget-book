package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.available
import jp.cjbc.kakeibo.model.balance
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.model.incomesFor
import jp.cjbc.kakeibo.model.planDifference
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.funds_add
import jp.cjbc.kakeibo.resources.funds_ahead
import jp.cjbc.kakeibo.resources.funds_behind
import jp.cjbc.kakeibo.resources.funds_empty
import jp.cjbc.kakeibo.resources.funds_goal
import jp.cjbc.kakeibo.resources.funds_incoming
import jp.cjbc.kakeibo.resources.funds_incoming_count
import jp.cjbc.kakeibo.resources.funds_with_goal
import jp.cjbc.kakeibo.resources.tab_funds
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.AmountBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.AppIconButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.EmptyState
import jp.cjbc.kakeibo.ui.component.FundDot
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlin.math.absoluteValue
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * The funds tab: the money that came in for this month, the funds without a goal with their
 * balance against what they could spend this month, then the funds with a goal.
 */
@Composable
internal fun FundsScreen(ledger: Ledger, navigator: AppNavigator) {
    val data = ledger.data
    val colors = AppTheme.colors
    val today = today()
    val month = currentMonth()
    val (withGoal, withoutGoal) = data.activeFunds.partition { it.goal is FundGoal.Target }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.tab_funds), onBack = null) {
            AppIconButton(AppIcons.Add, stringResource(Res.string.funds_add)) { navigator.open(Route.FundEdit(null)) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IncomingRow(data.transactions, month) { navigator.open(Route.MonthIncomes(month)) }
            if (data.activeFunds.isEmpty()) {
                EmptyState(stringResource(Res.string.funds_empty), icon = AppIcons.Funds, action = stringResource(Res.string.funds_add), onAction = {
                    navigator.open(Route.FundEdit(null))
                })
            }
            if (withoutGoal.isNotEmpty()) {
                FundCard(withoutGoal, onClick = { navigator.open(Route.FundDetail(it.id)) }) { fund ->
                    FundRowContent(fund, balance(fund, data.transactions, today)) {
                        AmountBar(balance(fund, data.transactions, today), available(fund, data.transactions, month), Color(fund.color.argb))
                    }
                }
            }
            if (withGoal.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.funds_with_goal))
                FundCard(withGoal, onClick = { navigator.open(Route.FundDetail(it.id)) }) { fund ->
                    val goal = fund.goal as FundGoal.Target
                    val balance = balance(fund, data.transactions, today)
                    FundRowContent(fund, balance) {
                        AmountBar(balance, goal.amount, Color(fund.color.argb))
                        GoalLine(goal, balance, month)
                    }
                }
            }
        }
    }
}

/** "Money in for October 295,000 (2 incomes from September) ›" */
@Composable
private fun IncomingRow(transactions: List<Transaction>, month: YearMonth, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val incomes = incomesFor(transactions, month)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(colors.infoContainer, AppShapes.DayGroup)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.funds_incoming, formatMonth(month)), style = AppTheme.type.label, color = colors.onInfoContainer)
            val sourceMonths = incomes.map { it.date.yearMonth }.distinct()
            if (incomes.isNotEmpty()) {
                Text(
                    stringResource(
                        Res.string.funds_incoming_count,
                        sourceMonths.singleOrNull()?.let { formatMonth(it) }.orEmpty(),
                        incomes.size,
                    ),
                    style = AppTheme.type.caption,
                    color = colors.onInfoContainer,
                )
            }
        }
        Text(formatAmount(incomes.sumOf { it.amount }), style = AppTheme.type.labelBold.merge(AppTheme.type.numeric), color = colors.onInfoContainer)
        Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.onInfoContainer)
    }
}

@Composable
private fun FundCard(funds: List<Fund>, onClick: (Fund) -> Unit, row: @Composable (Fund) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppShapes.Card)) {
        funds.forEachIndexed { index, fund ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.divider))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onClick(fund) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { row(fund) }
        }
    }
}

/** A fund's dot, name and balance (negative in the expense color, with −), over [below]. */
@Composable
private fun FundRowContent(fund: Fund, balance: Long, below: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FundDot(fund)
        Text(fund.name, style = AppTheme.type.bodyBold, modifier = Modifier.weight(1f))
        AmountText(balance)
    }
    below()
}

/** "Goal 240,000 · by Mar 2027" and how far ahead of or behind the plan it is. */
@Composable
internal fun GoalLine(goal: FundGoal.Target, balance: Long, month: YearMonth) {
    val colors = AppTheme.colors
    val difference = planDifference(goal, balance, month)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.funds_goal, formatAmount(goal.amount), formatYearMonth(goal.dueMonth)),
            style = AppTheme.type.caption.merge(AppTheme.type.numeric),
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                if (difference < 0) Res.string.funds_behind else Res.string.funds_ahead,
                formatAmount(difference.absoluteValue),
            ),
            style = AppTheme.type.caption.merge(AppTheme.type.numeric).copy(fontWeight = FontWeight.Bold),
            color = if (difference < 0) colors.negative else colors.success,
        )
    }
}
