package jp.cjbc.kakeibo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.app_title
import jp.cjbc.kakeibo.resources.recurring_auto_description
import jp.cjbc.kakeibo.resources.settings_back
import jp.cjbc.kakeibo.resources.settings_title
import jp.cjbc.kakeibo.resources.tab_analytics
import jp.cjbc.kakeibo.resources.tab_home
import jp.cjbc.kakeibo.resources.tab_monthly
import jp.cjbc.kakeibo.resources.tab_recurring
import jp.cjbc.kakeibo.resources.tab_savings
import jp.cjbc.kakeibo.ui.screen.AnalyticsScreen
import jp.cjbc.kakeibo.ui.screen.HomeScreen
import jp.cjbc.kakeibo.ui.screen.MonthlyScreen
import jp.cjbc.kakeibo.ui.screen.RecurringScreen
import jp.cjbc.kakeibo.ui.screen.SavingsScreen
import jp.cjbc.kakeibo.ui.screen.SettingsScreen
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.BACKGROUND_COLOR
import jp.cjbc.kakeibo.ui.theme.BORDER_COLOR
import jp.cjbc.kakeibo.ui.theme.KakeiboTheme
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.SURFACE_COLOR
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private enum class AppTab(val label: StringResource, val icon: String) {
    HOME(Res.string.tab_home, "🏠"),
    MONTHLY(Res.string.tab_monthly, "📊"),
    SAVINGS(Res.string.tab_savings, "🎯"),
    RECURRING(Res.string.tab_recurring, "🔄"),
    ANALYTICS(Res.string.tab_analytics, "📈"),
}

@Composable
fun App() {
    val ledger = remember { Ledger() }
    var revision by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    val onLedgerChange: () -> Unit = { revision++ }

    LaunchedEffect(ledger) {
        ledger.applyRecurringItems { getString(Res.string.recurring_auto_description, it) }
        onLedgerChange()
    }

    KakeiboTheme {
        Scaffold(
            containerColor = BACKGROUND_COLOR,
            topBar = {
                AppTopBar(isSettingsOpen, onSettingsOpenChange = { isSettingsOpen = it })
            },
            bottomBar = {
                AppBottomBar(
                    selectedTab.takeUnless { isSettingsOpen },
                    onTabSelect = {
                        selectedTab = it
                        isSettingsOpen = false
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                // Reading revision makes every persistence operation immediately redraw
                // shared UI state.
                revision
                if (isSettingsOpen) {
                    SettingsScreen(ledger, onLedgerChange)
                } else {
                    when (selectedTab) {
                        AppTab.HOME -> HomeScreen(ledger, onLedgerChange)
                        AppTab.MONTHLY -> MonthlyScreen(ledger, onLedgerChange)
                        AppTab.SAVINGS -> SavingsScreen(ledger, onLedgerChange)
                        AppTab.RECURRING -> RecurringScreen(ledger, onLedgerChange)
                        AppTab.ANALYTICS -> AnalyticsScreen(ledger, onLedgerChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(isSettingsOpen: Boolean, onSettingsOpenChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SURFACE_COLOR).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (isSettingsOpen) Res.string.settings_title else Res.string.app_title),
            color = ACCENT_COLOR,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isSettingsOpen) {
                Text(formatYearMonth(currentYearMonth()), color = MUTED_COLOR, fontSize = 12.sp)
            }
            Text(
                stringResource(
                    if (isSettingsOpen) Res.string.settings_back else Res.string.settings_title,
                ),
                color = ACCENT_COLOR,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onSettingsOpenChange(!isSettingsOpen) },
            )
        }
    }
}

@Composable
private fun AppBottomBar(selectedTab: AppTab?, onTabSelect: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SURFACE_COLOR).border(1.dp, BORDER_COLOR),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Column(
                Modifier.weight(1f).clickable { onTabSelect(tab) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tab.icon, fontSize = 16.sp)
                Text(
                    stringResource(tab.label),
                    color = if (isSelected) ACCENT_COLOR else MUTED_COLOR,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
