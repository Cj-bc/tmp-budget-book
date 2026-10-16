package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.entry_delete
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Swiping a row to the start reveals a delete background and deletes it. */
@Composable
internal fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            it == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Drawn only while swiping, so that it never shows around a row's rounded corners.
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Row(
                Modifier.fillMaxSize().background(colors.negative).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Delete, null, Modifier.size(20.dp), tint = Color.White)
                    Text(stringResource(Res.string.entry_delete), style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        },
    ) { content() }
}
