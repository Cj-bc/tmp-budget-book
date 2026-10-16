package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppTheme

/** A pill showing [label] and ▾ that opens a menu of [options]. */
@Composable
internal fun <T> DropdownChip(
    label: @Composable () -> Unit,
    options: List<T>,
    onSelect: (T) -> Unit,
    item: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .clip(AppShapes.Pill)
                .border(1.dp, AppTheme.colors.outlineStrong, AppShapes.Pill)
                .clickable(role = Role.Button) { open = true }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            label()
            Icon(AppIcons.ExpandMore, null, Modifier.size(18.dp), tint = AppTheme.colors.onSurface)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { item(option) }, onClick = {
                    onSelect(option)
                    open = false
                })
            }
        }
    }
}

/** A [DropdownChip] choosing one of [funds]; shows [placeholder] while nothing is chosen. */
@Composable
internal fun FundPicker(
    funds: List<Fund>,
    selected: Fund?,
    placeholder: String,
    onSelect: (Fund) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownChip(
        label = {
            if (selected != null) {
                FundDot(selected)
                Text(
                    selected.name,
                    style = AppTheme.type.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            } else {
                Text(placeholder, style = AppTheme.type.label, color = AppTheme.colors.onSurfaceMuted)
            }
        },
        options = funds,
        onSelect = onSelect,
        item = { FundLabel(it) },
        modifier = modifier,
    )
}
