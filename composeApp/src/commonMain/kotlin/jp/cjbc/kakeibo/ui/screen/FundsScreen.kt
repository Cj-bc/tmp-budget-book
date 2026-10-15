package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.available
import jp.cjbc.kakeibo.model.balance
import jp.cjbc.kakeibo.model.planDifference
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.funds_ahead
import jp.cjbc.kakeibo.resources.funds_behind
import jp.cjbc.kakeibo.resources.funds_empty
import jp.cjbc.kakeibo.resources.funds_goal
import jp.cjbc.kakeibo.resources.funds_with_goal
import jp.cjbc.kakeibo.resources.tab_funds
import jp.cjbc.kakeibo.ui.component.AmountProgressBar
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.FundLabel
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlin.math.absoluteValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/** The interim, read-only list of funds with their balances. */
@Composable
internal fun FundsScreen(ledger: Ledger) {
    val today = today()
    val funds = ledger.data.activeFunds
    val transactions = ledger.data.transactions
    val (withGoal, withoutGoal) = funds.partition { it.goal is FundGoal.Target }

    Column {
    AppTopBar(stringResource(Res.string.tab_funds), onBack = null)
    ScreenColumn {
        SectionCard {
            if (withoutGoal.isEmpty()) EmptyMessage(stringResource(Res.string.funds_empty))
            withoutGoal.forEach { fund -> FundRow(fund, transactions, today) }
        }
        if (withGoal.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.funds_with_goal))
            SectionCard {
                withGoal.forEach { fund -> FundRow(fund, transactions, today) }
            }
        }
    }
    }
}

@Composable
private fun FundRow(fund: Fund, transactions: List<Transaction>, today: LocalDate) {
    val balance = balance(fund, transactions, today)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)) { FundLabel(fund) }
        AmountText(balance)
    }
    Spacer(Modifier.height(4.dp))
    when (val goal = fund.goal) {
        FundGoal.None -> AmountProgressBar(balance, available(fund, transactions, today.yearMonth), Color(fund.color.argb))
        is FundGoal.Target -> {
            AmountProgressBar(balance, goal.amount, Color(fund.color.argb))
            GoalSummary(goal, balance, today.yearMonth)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun GoalSummary(goal: FundGoal.Target, balance: Long, month: YearMonth) {
    val difference = planDifference(goal, balance, month)
    Text(
        stringResource(Res.string.funds_goal, formatAmount(goal.amount), formatYearMonth(goal.dueMonth)),
        color = AppTheme.colors.onSurfaceMuted,
        fontSize = 12.sp,
    )
    Text(
        stringResource(
            if (difference < 0) Res.string.funds_behind else Res.string.funds_ahead,
            formatAmount(difference.absoluteValue),
        ),
        color = if (difference < 0) AppTheme.colors.negative else AppTheme.colors.positive,
        fontSize = 12.sp,
    )
}
