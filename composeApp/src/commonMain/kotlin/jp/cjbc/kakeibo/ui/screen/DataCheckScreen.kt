package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.check_pull_again
import jp.cjbc.kakeibo.resources.unreadable_ledger_message
import jp.cjbc.kakeibo.resources.unreadable_ledger_title
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/**
 * In place of the ledger, whose master.json pulled from the repository cannot be read for
 * [reason]: the user fixes it there, and pulls again with [onPullAgain] (`null` without syncing).
 */
@Composable
internal fun UnreadableLedgerScreen(reason: String, onPullAgain: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.Screen, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Warning, null, Modifier.size(40.dp), tint = AppTheme.colors.negative)
        Text(stringResource(Res.string.unreadable_ledger_title), style = AppTheme.type.screenTitle)
        Text(stringResource(Res.string.unreadable_ledger_message), style = AppTheme.type.body)
        Text(reason, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        onPullAgain?.let { ActionButton(stringResource(Res.string.check_pull_again), it) }
    }
}
