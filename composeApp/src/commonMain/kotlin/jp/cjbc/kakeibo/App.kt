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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import jp.cjbc.kakeibo.model.currentMonth
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.app_title
import jp.cjbc.kakeibo.resources.settings_title
import jp.cjbc.kakeibo.resources.tab_analytics
import jp.cjbc.kakeibo.resources.tab_entry
import jp.cjbc.kakeibo.resources.tab_funds
import jp.cjbc.kakeibo.resources.tab_recurring
import jp.cjbc.kakeibo.ui.component.migratedFundName
import jp.cjbc.kakeibo.ui.screen.AnalyticsScreen
import jp.cjbc.kakeibo.ui.screen.EntryScreen
import jp.cjbc.kakeibo.ui.screen.FundsScreen
import jp.cjbc.kakeibo.ui.screen.RecurringScreen
import jp.cjbc.kakeibo.ui.screen.SettingsScreen
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.ui.theme.KakeiboTheme
import jp.cjbc.kakeibo.util.formatYearMonth
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class AppTab(val label: StringResource) {
    ENTRY(Res.string.tab_entry),
    FUNDS(Res.string.tab_funds),
    RECURRING(Res.string.tab_recurring),
    ANALYTICS(Res.string.tab_analytics),

    /** Opened from the top bar instead of the bottom bar. */
    SETTINGS(Res.string.settings_title),
    ;

    companion object {
        val BOTTOM_BAR_TABS = entries - SETTINGS
    }
}

@Composable
fun App() {
    var ledger by remember { mutableStateOf<Ledger?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.ENTRY) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        ledger = Ledger.open(fundName = ::migratedFundName).apply { applyRecurringItems() }
    }

    KakeiboTheme {
        Scaffold(
            containerColor = AppTheme.colors.background,
            topBar = { AppTopBar(selectedTab, onTabSelect = { selectedTab = it }) },
            bottomBar = { AppBottomBar(selectedTab, onTabSelect = { selectedTab = it }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val current = ledger ?: return@Box
                when (selectedTab) {
                    AppTab.ENTRY -> EntryScreen(current, snackbarHostState)
                    AppTab.FUNDS -> FundsScreen(current)
                    AppTab.RECURRING -> RecurringScreen(current, snackbarHostState)
                    AppTab.ANALYTICS -> AnalyticsScreen(current)
                    AppTab.SETTINGS -> SettingsScreen(current)
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(selectedTab: AppTab, onTabSelect: (AppTab) -> Unit) {
    val isSettingsSelected = selectedTab == AppTab.SETTINGS
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (isSettingsSelected) selectedTab.label else Res.string.app_title),
            color = AppTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatYearMonth(currentMonth()), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
            if (!isSettingsSelected) {
                Text(
                    stringResource(Res.string.settings_title),
                    color = AppTheme.colors.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onTabSelect(AppTab.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(selectedTab: AppTab, onTabSelect: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface).border(1.dp, AppTheme.colors.outline),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppTab.BOTTOM_BAR_TABS.forEach { tab ->
            val isSelected = tab == selectedTab
            Column(
                Modifier.weight(1f).clickable { onTabSelect(tab) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(tab.label),
                    color = if (isSelected) AppTheme.colors.onSurface else AppTheme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
