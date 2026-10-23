package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import jp.cjbc.kakeibo.data.RemoteSync
import jp.cjbc.kakeibo.data.RepositoryCheck
import jp.cjbc.kakeibo.data.SyncStatus
import jp.cjbc.kakeibo.data.readCommitAuthor
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.cancel
import jp.cjbc.kakeibo.resources.ok
import jp.cjbc.kakeibo.resources.sync_account
import jp.cjbc.kakeibo.resources.sync_change_installation
import jp.cjbc.kakeibo.resources.sync_not_configured
import jp.cjbc.kakeibo.resources.sync_now
import jp.cjbc.kakeibo.resources.sync_repository
import jp.cjbc.kakeibo.resources.sync_repository_failed
import jp.cjbc.kakeibo.resources.sync_repository_hint
import jp.cjbc.kakeibo.resources.sync_repository_label
import jp.cjbc.kakeibo.resources.sync_repository_none
import jp.cjbc.kakeibo.resources.sync_repository_not_found
import jp.cjbc.kakeibo.resources.sync_repository_read_only
import jp.cjbc.kakeibo.resources.sync_sign_in
import jp.cjbc.kakeibo.resources.sync_sign_in_failed
import jp.cjbc.kakeibo.resources.sync_sign_in_hint
import jp.cjbc.kakeibo.resources.sync_sign_out
import jp.cjbc.kakeibo.resources.sync_sign_out_confirm
import jp.cjbc.kakeibo.resources.sync_status_conflicts
import jp.cjbc.kakeibo.resources.sync_status_failed
import jp.cjbc.kakeibo.resources.sync_status_none
import jp.cjbc.kakeibo.resources.sync_status_pulled
import jp.cjbc.kakeibo.resources.sync_status_pushed
import jp.cjbc.kakeibo.resources.sync_status_rejected
import jp.cjbc.kakeibo.resources.sync_status_unrelated
import jp.cjbc.kakeibo.resources.sync_status_up_to_date
import jp.cjbc.kakeibo.resources.sync_stop
import jp.cjbc.kakeibo.resources.sync_syncing
import jp.cjbc.kakeibo.resources.sync_title
import jp.cjbc.kakeibo.resources.sync_unrelated_message
import jp.cjbc.kakeibo.resources.sync_unrelated_title
import jp.cjbc.kakeibo.resources.sync_use_remote
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Runs the syncs of [remote] for the app. While one runs, [SyncDialogs] covers the screen, since
 * an edit made meanwhile would be saved over the files the sync changed. When the files changed,
 * the ledger is opened again with [reopen].
 */
@Stable
internal class SyncRunner(
    val remote: RemoteSync,
    private val reopen: suspend () -> Unit,
    private val snackbarHostState: SnackbarHostState,
) {
    var busy by mutableStateOf(false)
        private set

    /** Whether the user is asked to take the repository's history instead of the device's. */
    var askReplace by mutableStateOf(false)
        private set

    /**
     * Syncs, and opens the ledger again if the files changed. Says how it went in a snackbar if
     * [report], or else only when the sync stopped at what the user must settle.
     */
    suspend fun sync(report: Boolean) {
        busy = true
        val status = try {
            remote.sync(readCommitAuthor())?.also { if (it == SyncStatus.Pulled) reopen() }
        } finally {
            busy = false
        }
        when (status) {
            null -> {}
            SyncStatus.UnrelatedHistories -> askReplace = true
            is SyncStatus.Conflicts -> snackbarHostState.showSnackbar(statusText(status))
            else -> if (report) snackbarHostState.showSnackbar(statusText(status))
        }
    }

    /** Takes the repository's history, keeping the device's aside, after [askReplace]. */
    suspend fun replaceWithRemote() {
        askReplace = false
        busy = true
        val status = try {
            remote.replaceWithRemote(readCommitAuthor())?.also { if (it == SyncStatus.Pulled) reopen() }
        } finally {
            busy = false
        }
        status?.let { snackbarHostState.showSnackbar(statusText(it)) }
    }

    /** Keeps the device's history, and stops syncing, after [askReplace]. */
    fun keepLocal() {
        askReplace = false
        remote.clearRepository()
    }
}

/** What [status] means for the user. */
private suspend fun statusText(status: SyncStatus?): String = when (status) {
    null -> getString(Res.string.sync_status_none)
    SyncStatus.UpToDate -> getString(Res.string.sync_status_up_to_date)
    SyncStatus.Pushed -> getString(Res.string.sync_status_pushed)
    SyncStatus.Pulled -> getString(Res.string.sync_status_pulled)
    is SyncStatus.Conflicts -> getString(Res.string.sync_status_conflicts, status.count.toString())
    SyncStatus.UnrelatedHistories -> getString(Res.string.sync_status_unrelated)
    SyncStatus.Rejected -> getString(Res.string.sync_status_rejected)
    is SyncStatus.Failed -> getString(Res.string.sync_status_failed, status.message)
}

@Composable
private fun statusLabel(status: SyncStatus?): String = when (status) {
    null -> stringResource(Res.string.sync_status_none)
    SyncStatus.UpToDate -> stringResource(Res.string.sync_status_up_to_date)
    SyncStatus.Pushed -> stringResource(Res.string.sync_status_pushed)
    SyncStatus.Pulled -> stringResource(Res.string.sync_status_pulled)
    is SyncStatus.Conflicts -> stringResource(Res.string.sync_status_conflicts, status.count.toString())
    SyncStatus.UnrelatedHistories -> stringResource(Res.string.sync_status_unrelated)
    SyncStatus.Rejected -> stringResource(Res.string.sync_status_rejected)
    is SyncStatus.Failed -> stringResource(Res.string.sync_status_failed, status.message)
}

/** The dialog covering the screen while [runner] syncs, and the question it may ask afterwards. */
@Composable
internal fun SyncDialogs(runner: SyncRunner) {
    val scope = rememberCoroutineScope()
    if (runner.busy) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(Res.string.sync_syncing), style = AppTheme.type.body)
                }
            },
            confirmButton = {},
        )
    }
    if (runner.askReplace) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(Res.string.sync_unrelated_title)) },
            text = { Text(stringResource(Res.string.sync_unrelated_message), style = AppTheme.type.body) },
            confirmButton = {
                TextButton(onClick = { scope.launch { runner.replaceWithRemote() } }) {
                    Text(stringResource(Res.string.sync_use_remote), color = AppTheme.colors.negative)
                }
            },
            dismissButton = { TextButton(onClick = runner::keepLocal) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** The settings of syncing with GitHub: signing in, the repository, and syncing now. */
@Composable
internal fun SyncSection(runner: SyncRunner) {
    val remote = runner.remote
    val scope = rememberCoroutineScope()
    var editingRepository by remember { mutableStateOf(false) }
    var confirmingSignOut by remember { mutableStateOf(false) }

    SectionHeader(stringResource(Res.string.sync_title))
    SectionCard(padding = 0.dp) {
        val account = remote.account
        when {
            !remote.isConfigured -> TwoLineRow(
                first = true,
                title = stringResource(Res.string.sync_title),
                detail = stringResource(Res.string.sync_not_configured),
                icon = AppIcons.Warning,
                onClick = null,
            )
            account == null -> TwoLineRow(
                first = true,
                title = stringResource(Res.string.sync_sign_in),
                detail = stringResource(if (remote.signInFailed) Res.string.sync_sign_in_failed else Res.string.sync_sign_in_hint),
                icon = AppIcons.OpenInNew,
                onClick = remote::signIn,
            )
            else -> {
                TwoLineRow(
                    first = true,
                    title = stringResource(Res.string.sync_account),
                    detail = "@$account",
                    icon = AppIcons.Close,
                ) { confirmingSignOut = true }
                if (remote.canChangeInstallation) {
                    TwoLineRow(
                        first = false,
                        title = stringResource(Res.string.sync_change_installation),
                        detail = stringResource(Res.string.sync_sign_in_hint),
                        icon = AppIcons.OpenInNew,
                        onClick = remote::changeInstallation,
                    )
                }
                TwoLineRow(
                    first = false,
                    title = stringResource(Res.string.sync_repository),
                    detail = remote.repository ?: stringResource(Res.string.sync_repository_none),
                    icon = AppIcons.Edit,
                ) { editingRepository = true }
                if (remote.repository != null) {
                    TwoLineRow(
                        first = false,
                        title = stringResource(Res.string.sync_now),
                        detail = statusLabel(remote.status),
                        icon = AppIcons.Sync,
                    ) { scope.launch { runner.sync(report = true) } }
                }
            }
        }
    }

    if (editingRepository) {
        // The app syncs when the repository changes.
        RepositoryDialog(remote, onSet = { editingRepository = false }, onDismiss = { editingRepository = false })
    }
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(Res.string.sync_sign_out)) },
            text = { Text(stringResource(Res.string.sync_sign_out_confirm), style = AppTheme.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    scope.launch { remote.signOut() }
                }) { Text(stringResource(Res.string.sync_sign_out), color = AppTheme.colors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** Picking the repository to sync with, checked with GitHub before it is set. */
@Composable
private fun RepositoryDialog(remote: RemoteSync, onSet: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(remote.repository.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<RepositoryCheck?>(null) }
    val error = when (check) {
        RepositoryCheck.NOT_FOUND -> stringResource(Res.string.sync_repository_not_found)
        RepositoryCheck.READ_ONLY -> stringResource(Res.string.sync_repository_read_only)
        RepositoryCheck.FAILED -> stringResource(Res.string.sync_repository_failed)
        RepositoryCheck.OK, null -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sync_repository)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.sync_repository_hint), style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
                LabeledTextField(name, stringResource(Res.string.sync_repository_label), { name = it }, error = error)
                if (checking) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !checking && name.isNotBlank(), onClick = {
                checking = true
                scope.launch {
                    check = remote.setRepository(name)
                    checking = false
                    if (check == RepositoryCheck.OK) onSet()
                }
            }) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = {
            Row {
                if (remote.repository != null) {
                    TextButton(onClick = {
                        remote.clearRepository()
                        onDismiss()
                    }) { Text(stringResource(Res.string.sync_stop), color = AppTheme.colors.negative) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}
