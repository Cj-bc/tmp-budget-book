package jp.cjbc.kakeibo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.DataCheck
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.data.files.UnreadableMasterException
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.check_saved
import jp.cjbc.kakeibo.resources.tab_analytics
import jp.cjbc.kakeibo.resources.tab_entry
import jp.cjbc.kakeibo.resources.tab_funds
import jp.cjbc.kakeibo.resources.tab_recurring
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.PlatformBackHandler
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.Tab
import jp.cjbc.kakeibo.ui.component.AppSnackbar
import jp.cjbc.kakeibo.ui.component.presetName
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.screen.AllocateScreen
import jp.cjbc.kakeibo.ui.screen.AllocationRulesScreen
import jp.cjbc.kakeibo.ui.screen.AnalyticsScreen
import jp.cjbc.kakeibo.ui.screen.CategoriesScreen
import jp.cjbc.kakeibo.ui.screen.DataCheckScreen
import jp.cjbc.kakeibo.ui.screen.RecurringEditScreen
import jp.cjbc.kakeibo.ui.theme.readThemeMode
import jp.cjbc.kakeibo.ui.theme.writeThemeMode
import jp.cjbc.kakeibo.ui.screen.EntryForm
import jp.cjbc.kakeibo.ui.screen.FundDetailScreen
import jp.cjbc.kakeibo.ui.screen.FundEditScreen
import jp.cjbc.kakeibo.ui.screen.MonthIncomesScreen
import jp.cjbc.kakeibo.ui.screen.EntryScreen
import jp.cjbc.kakeibo.ui.screen.FundsScreen
import jp.cjbc.kakeibo.ui.screen.HistoryScreen
import jp.cjbc.kakeibo.ui.screen.RecurringScreen
import jp.cjbc.kakeibo.ui.screen.SettingsScreen
import jp.cjbc.kakeibo.ui.screen.SyncDialogs
import jp.cjbc.kakeibo.ui.screen.SyncRunner
import jp.cjbc.kakeibo.ui.screen.UnreadableLedgerScreen
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.ui.theme.KakeiboTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val Tab.label: StringResource
    get() = when (this) {
        Tab.ENTRY -> Res.string.tab_entry
        Tab.FUNDS -> Res.string.tab_funds
        Tab.RECURRING -> Res.string.tab_recurring
        Tab.ANALYTICS -> Res.string.tab_analytics
    }

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.ENTRY -> AppIcons.Add
        Tab.FUNDS -> AppIcons.Funds
        Tab.RECURRING -> AppIcons.Recurring
        Tab.ANALYTICS -> AppIcons.Analytics
    }

@Composable
fun App() {
    var ledger by remember { mutableStateOf<Ledger?>(null) }
    val navigator = remember { AppNavigator() }
    val snackbarHostState = remember { SnackbarHostState() }
    // The new entry on the record screen, kept while other screens are open over it.
    val entryForm = remember { mutableStateOf(EntryForm.empty()) }
    var themeMode by remember { mutableStateOf(readThemeMode()) }
    // What makes the ledger unreadable, when its master.json pulled from the repository is.
    var unreadable by remember { mutableStateOf<String?>(null) }
    // The records that cannot be shown, while the user looks at them.
    var dataCheck by remember { mutableStateOf<DataCheck?>(null) }
    // The names of the funds and categories seen, which name those that a pull deletes.
    var knownNames by remember { mutableStateOf(emptyMap<String, String>()) }
    val scope = rememberCoroutineScope()

    suspend fun open() {
        try {
            // A ledger synced with a repository is not started afresh over an unreadable
            // master.json, since that would be pushed over the repository's.
            ledger = openLedger(startAfresh = remoteSync()?.repository == null)
            unreadable = null
        } catch (error: UnreadableMasterException) {
            ledger = null
            unreadable = error.message.orEmpty()
        }
    }

    val sync = remember {
        remoteSync()?.let { remote ->
            SyncRunner(remote, reopen = { left ->
                ledger?.let { knownNames = knownNames + it.data.names() }
                open()
                // Check what the pull took in, unless the user just chose to leave all of it unshown.
                ledger?.check()?.takeIf { (it.hidden.keys - left).isNotEmpty() }?.let { dataCheck = it }
            }, snackbarHostState, scope)
        }
    }

    // Take in the other devices' changes before showing the ledger, and again whenever another
    // repository is set to sync with, from the settings or after signing in.
    LaunchedEffect(sync?.remote?.repository) {
        sync?.sync(report = ledger != null)
        if (ledger == null) open()
    }
    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    KakeiboTheme(themeMode) {
        // The merge that a sync stopped at is drawn over the rest.
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = AppTheme.colors.background,
                bottomBar = { AppBottomBar(navigator) },
                snackbarHost = { SnackbarHost(snackbarHostState) { AppSnackbar(it) } },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    unreadable?.let { reason ->
                        UnreadableLedgerScreen(reason, onPullAgain = sync?.let { runner ->
                            {
                                scope.launch {
                                    runner.sync(report = true)
                                    if (ledger == null) open()
                                }
                            }
                        })
                    }
                    val current = ledger ?: return@Box
                    when (val route = navigator.route) {
                        null -> when (navigator.tab) {
                            Tab.ENTRY -> EntryScreen(current, navigator, snackbarHostState, editing = null, entryForm)
                            Tab.FUNDS -> FundsScreen(current, navigator)
                            Tab.RECURRING -> RecurringScreen(current, navigator, snackbarHostState)
                            Tab.ANALYTICS -> AnalyticsScreen(current)
                        }
                        is Route.History -> HistoryScreen(current, navigator, snackbarHostState, route)
                        is Route.Edit -> EntryScreen(current, navigator, snackbarHostState, editing = route.id, entryForm)
                        Route.Settings -> SettingsScreen(current, navigator, snackbarHostState, sync, themeMode, onCheckData = { dataCheck = it }) {
                            themeMode = it
                            writeThemeMode(it)
                        }
                        is Route.Categories -> CategoriesScreen(current, navigator, route)
                        is Route.Allocate -> AllocateScreen(current, navigator, snackbarHostState, route.draft, entryForm)
                        is Route.AllocationRules -> AllocationRulesScreen(current, navigator, snackbarHostState, route)
                        is Route.FundDetail -> FundDetailScreen(current, navigator, route)
                        is Route.FundEdit -> FundEditScreen(current, navigator, snackbarHostState, route)
                        is Route.RecurringEdit -> RecurringEditScreen(current, navigator, snackbarHostState, route)
                        is Route.MonthIncomes -> MonthIncomesScreen(current, navigator, route)
                    }
                }
            }
            val check = dataCheck
            val current = ledger
            if (check != null && current != null) {
                DataCheckScreen(
                    check,
                    current.data,
                    names = knownNames + current.data.names(),
                    onSave = { records ->
                        current.saveRecords(records, "Fix records that could not be shown")
                        dataCheck = null
                        scope.launch {
                            open()
                            snackbarHostState.showSnackbar(getString(Res.string.check_saved))
                        }
                    },
                    onPullAgain = sync?.let { runner ->
                        {
                            dataCheck = null
                            scope.launch { runner.sync(report = true) }
                        }
                    },
                    onClose = { dataCheck = null },
                )
            }
            sync?.let { SyncDialogs(it) }
        }
    }
}

/** The names of the funds, categories and recurring items, by ID. */
private fun LedgerData.names(): Map<String, String> =
    funds.associate { it.id.value to it.name } + categories.associate { it.id.value to it.name } +
        recurringItems.associate { it.id.value to it.name }

private suspend fun openLedger(startAfresh: Boolean): Ledger =
    Ledger.open(presetName = ::presetName, startAfresh = startAfresh).apply { applyRecurringItems() }

/** The 72dp bottom bar with the four tabs; the current one has a pill behind its icon. */
@Composable
private fun AppBottomBar(navigator: AppNavigator) {
    val colors = AppTheme.colors
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
        Row(Modifier.fillMaxWidth().height(AppSpacing.BottomBar).background(colors.surface)) {
            Tab.entries.forEach { tab ->
                val selected = tab == navigator.tab
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { navigator.select(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(28.dp)
                            .background(if (selected) colors.surfaceMuted else colors.surface, AppShapes.Pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            Modifier.size(22.dp),
                            tint = if (selected) colors.onSurface else colors.onSurfaceMuted,
                        )
                    }
                    Text(
                        stringResource(tab.label),
                        style = if (selected) AppTheme.type.caption.copy(fontWeight = FontWeight.Bold) else AppTheme.type.caption,
                        color = if (selected) colors.onSurface else colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}
