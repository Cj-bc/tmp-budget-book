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
import jp.cjbc.kakeibo.ui.screen.AnalyticsScreen
import jp.cjbc.kakeibo.ui.screen.HomeScreen
import jp.cjbc.kakeibo.ui.screen.MonthlyScreen
import jp.cjbc.kakeibo.ui.screen.RecurringScreen
import jp.cjbc.kakeibo.ui.screen.SavingsScreen
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.BACKGROUND_COLOR
import jp.cjbc.kakeibo.ui.theme.BORDER_COLOR
import jp.cjbc.kakeibo.ui.theme.KakeiboTheme
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.SURFACE_COLOR
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.formatYearMonth

private enum class AppTab(val label: String, val icon: String) {
    HOME("ホーム", "🏠"),
    MONTHLY("月次", "📊"),
    SAVINGS("積立", "🎯"),
    RECURRING("固定費", "🔄"),
    ANALYTICS("分析", "📈"),
}

@Composable
fun App() {
    val ledger = remember { Ledger().also { it.applyRecurringItems() } }
    var revision by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    val onLedgerChange: () -> Unit = { revision++ }

    KakeiboTheme {
        Scaffold(
            containerColor = BACKGROUND_COLOR,
            topBar = { AppTopBar() },
            bottomBar = { AppBottomBar(selectedTab, onTabSelect = { selectedTab = it }) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                // Reading revision makes every persistence operation immediately redraw
                // shared UI state.
                revision
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

@Composable
private fun AppTopBar() {
    Row(
        Modifier.fillMaxWidth().background(SURFACE_COLOR).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("家計簿", color = ACCENT_COLOR, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text(formatYearMonth(currentYearMonth()), color = MUTED_COLOR, fontSize = 12.sp)
    }
}

@Composable
private fun AppBottomBar(selectedTab: AppTab, onTabSelect: (AppTab) -> Unit) {
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
                    tab.label,
                    color = if (isSelected) ACCENT_COLOR else MUTED_COLOR,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
