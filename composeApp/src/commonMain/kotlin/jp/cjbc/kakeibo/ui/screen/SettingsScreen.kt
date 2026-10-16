package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.ImportResult
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.ui.component.migratedFundName
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeIncomeCategories
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.backup_choose_file
import jp.cjbc.kakeibo.resources.backup_export_file
import jp.cjbc.kakeibo.resources.backup_export_file_hint
import jp.cjbc.kakeibo.resources.backup_import_failure
import jp.cjbc.kakeibo.resources.backup_import_success
import jp.cjbc.kakeibo.resources.backup_label
import jp.cjbc.kakeibo.resources.backup_paste
import jp.cjbc.kakeibo.resources.backup_restore_confirm
import jp.cjbc.kakeibo.resources.backup_restore_do
import jp.cjbc.kakeibo.resources.backup_restore_file
import jp.cjbc.kakeibo.resources.backup_restore_hint
import jp.cjbc.kakeibo.resources.backup_saved
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.settings_categories
import jp.cjbc.kakeibo.resources.settings_data
import jp.cjbc.kakeibo.resources.settings_display
import jp.cjbc.kakeibo.resources.settings_expense_categories
import jp.cjbc.kakeibo.resources.settings_income_categories
import jp.cjbc.kakeibo.resources.settings_language
import jp.cjbc.kakeibo.resources.settings_language_value
import jp.cjbc.kakeibo.resources.settings_rules
import jp.cjbc.kakeibo.resources.settings_theme
import jp.cjbc.kakeibo.resources.settings_title
import jp.cjbc.kakeibo.resources.theme_dark
import jp.cjbc.kakeibo.resources.theme_light
import jp.cjbc.kakeibo.resources.theme_system
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ListRow
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.Segmented
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.rememberBackupFiles
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The settings: backups, the categories and allocation rules, and the theme. */
@Composable
internal fun SettingsScreen(
    ledger: Ledger,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val data = ledger.data
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val files = rememberBackupFiles()
    var showRestore by remember { mutableStateOf(false) }
    // The backup waiting for the user to confirm that it replaces everything.
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restoreFailed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(Res.string.settings_title), navigator::back, stringResource(Res.string.back))
        ScreenColumn {
            SectionHeader(stringResource(Res.string.settings_data))
            SectionCard(padding = 0.dp) {
                TwoLineRow(
                    first = true,
                    title = stringResource(Res.string.backup_export_file),
                    detail = stringResource(Res.string.backup_export_file_hint),
                    icon = AppIcons.Download,
                ) {
                    files.save("kakeibo-backup-${today()}.json", ledger.exportBackup()) {
                        scope.launch { snackbarHostState.showSnackbar(getString(Res.string.backup_saved)) }
                    }
                }
                TwoLineRow(
                    first = false,
                    title = stringResource(Res.string.backup_restore_file),
                    detail = stringResource(Res.string.backup_restore_hint),
                    icon = AppIcons.Upload,
                ) { showRestore = true }
            }

            SectionHeader(stringResource(Res.string.settings_categories))
            SectionCard(padding = 0.dp) {
                CountRow(true, stringResource(Res.string.settings_expense_categories), data.activeExpenseCategories.size) {
                    navigator.open(Route.Categories(isExpense = true))
                }
                CountRow(false, stringResource(Res.string.settings_income_categories), data.activeIncomeCategories.size) {
                    navigator.open(Route.Categories(isExpense = false))
                }
                CountRow(false, stringResource(Res.string.settings_rules), count = null) {
                    navigator.open(Route.AllocationRules())
                }
            }

            SectionHeader(stringResource(Res.string.settings_display))
            SectionCard(padding = 0.dp) {
                ListRow(first = true) {
                    Text(stringResource(Res.string.settings_theme), style = AppTheme.type.body, modifier = Modifier.weight(1f))
                    val labels = mapOf(
                        ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                        ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                        ThemeMode.DARK to stringResource(Res.string.theme_dark),
                    )
                    Segmented(ThemeMode.entries, themeMode, onThemeMode) { labels.getValue(it) }
                }
                ListRow(first = false) {
                    Text(stringResource(Res.string.settings_language), style = AppTheme.type.body, modifier = Modifier.weight(1f))
                    Text(stringResource(Res.string.settings_language_value), style = AppTheme.type.label, color = colors.onSurfaceMuted)
                }
            }
        }
    }

    if (showRestore) {
        RestoreSheet(
            failed = restoreFailed,
            onChooseFile = { files.open { text -> pendingRestore = text } },
            onPaste = { pendingRestore = it },
            onDismiss = {
                showRestore = false
                restoreFailed = false
            },
        )
    }
    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(Res.string.backup_restore_file)) },
            text = { Text(stringResource(Res.string.backup_restore_confirm), style = AppTheme.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        when (val result = ledger.importBackup(text, ::migratedFundName)) {
                            is ImportResult.Success -> {
                                result.log.forEach { println("Import: $it") }
                                showRestore = false
                                snackbarHostState.showSnackbar(getString(Res.string.backup_import_success))
                            }
                            // The sheet is still open, so the error shows in it.
                            ImportResult.Failure -> restoreFailed = true
                        }
                    }
                }) { Text(stringResource(Res.string.backup_restore_do), color = colors.negative) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** A row with a title over a muted line, and an icon at its end. */
@Composable
private fun TwoLineRow(first: Boolean, title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    ListRow(first, onClick) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = AppTheme.type.body, color = AppTheme.colors.onSurface)
            Text(detail, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        }
        Icon(icon, null, Modifier.size(20.dp), tint = AppTheme.colors.onSurfaceMuted)
    }
}

/** A row opening a list, with the number of its items when [count] is given. */
@Composable
private fun CountRow(first: Boolean, title: String, count: Int?, onClick: () -> Unit) {
    ListRow(first, onClick) {
        Text(title, style = AppTheme.type.body, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(
                count.toString(),
                style = AppTheme.type.label.merge(AppTheme.type.numeric),
                color = AppTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp), tint = AppTheme.colors.onSurfaceMuted)
    }
}

/** Restoring a backup: pick a file, or paste the backup's text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSheet(failed: Boolean, onChooseFile: () -> Unit, onPaste: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.Sheet,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.backup_restore_file), style = AppTheme.type.month)
            Text(stringResource(Res.string.backup_restore_confirm), style = AppTheme.type.caption, color = colors.onSurfaceMuted)
            ActionButton(stringResource(Res.string.backup_choose_file), onChooseFile, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            LabeledTextField(
                text,
                stringResource(Res.string.backup_label),
                { text = it },
                Modifier.fillMaxWidth().heightIn(min = 120.dp),
                singleLine = false,
            )
            if (failed) FieldError(stringResource(Res.string.backup_import_failure))
            Row {
                ActionButton(
                    stringResource(Res.string.backup_paste),
                    { onPaste(text) },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = text.isNotBlank(),
                )
            }
        }
    }
}
