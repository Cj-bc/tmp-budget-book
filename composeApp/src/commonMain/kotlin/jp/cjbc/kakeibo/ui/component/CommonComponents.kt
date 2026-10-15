package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.ui.icon.AppIcons
import jp.cjbc.kakeibo.ui.theme.AppShapes
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.ui.theme.fundTint
import jp.cjbc.kakeibo.util.formatAmount

/** Vertically scrollable container used as the root of each screen. */
@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A flat card on the screen background. */
@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = AppSpacing.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppShapes.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = AppTheme.type.sectionHeader,
        color = AppTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** The color an amount is shown in: negative ones always, positive ones if [signed]. */
@Composable
internal fun amountColor(amount: Long, signed: Boolean): Color = when {
    amount < 0 -> AppTheme.colors.negative
    amount > 0 && signed -> AppTheme.colors.positive
    else -> AppTheme.colors.onSurface
}

/**
 * Shows the yen [amount]. Positive amounts start with `+` and are in the income color if
 * [signed]; negative amounts are always in the expense color. Color is never the only cue.
 */
@Composable
internal fun AmountText(
    amount: Long,
    signed: Boolean = false,
    style: TextStyle = AppTheme.type.listAmount,
    modifier: Modifier = Modifier,
) {
    Text(formatAmount(amount, signed), modifier, color = amountColor(amount, signed), style = style)
}

/**
 * A pill-shaped bar showing [value] out of [total]. A negative [value] fills it in the expense
 * color. [marker] draws a tick at that fraction.
 */
@Composable
internal fun AmountBar(
    value: Long,
    total: Long,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val fraction = when {
        value < 0 -> 1f
        total <= 0 -> 0f
        else -> (value.toFloat() / total).coerceIn(0f, 1f)
    }
    Box(
        modifier.fillMaxWidth().height(height).clip(AppShapes.Pill).background(AppTheme.colors.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(AppShapes.Pill)
                .background(if (value < 0) AppTheme.colors.negative else color),
        )
    }
}

/** Kept for the interim screens: [AmountBar] with the default color. */
@Composable
internal fun AmountProgressBar(value: Long, total: Long, color: Color = AppTheme.colors.onSurface) {
    AmountBar(value, total, color)
}

/** A pill button: filled in [AppTheme.colors.onSurface] if [isPrimary], else outlined. */
@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(AppShapes.Pill)
            .background(
                when {
                    !isPrimary -> colors.surface
                    enabled -> colors.onSurface
                    else -> colors.surfaceMuted
                },
            )
            .let { if (isPrimary) it else it.border(1.dp, colors.outlineStrong, AppShapes.Pill) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppTheme.type.labelBold,
            color = when {
                !enabled -> colors.disabled
                isPrimary -> colors.onFill
                else -> colors.onSurface
            },
        )
    }
}

@Composable
internal fun LabeledTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    error: String? = null,
) {
    Column(modifier) {
        OutlinedTextField(
            value,
            onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = error != null,
            textStyle = AppTheme.type.body,
        )
        // Validation errors go under their field, in words.
        error?.let { FieldError(it) }
    }
}

/** An empty state: an icon, one line, and an optional action. */
@Composable
internal fun EmptyState(
    text: String,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let {
            Box(
                Modifier.size(52.dp).background(AppTheme.colors.surfaceMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(it, contentDescription = null, tint = AppTheme.colors.onSurfaceMuted)
            }
        }
        Text(
            text,
            style = AppTheme.type.body,
            color = AppTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        action?.let { ActionButton(it, onAction, isPrimary = false) }
    }
}

/** Kept for the interim screens: an [EmptyState] without an icon. */
@Composable
internal fun EmptyMessage(text: String) {
    EmptyState(text)
}

/** A label stacked over its value, so long labels never run into the value. */
@Composable
internal fun MetricItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.onSurfaceMuted)
        Text(value, style = AppTheme.type.listAmount, color = color)
    }
}

/** A pill chip: filled in [AppTheme.colors.onSurface] when [selected], else outlined. */
@Composable
internal fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(AppShapes.Pill)
            .background(if (selected) colors.onSurface else colors.surface)
            .let { if (selected) it else it.border(1.dp, colors.outlineStrong, AppShapes.Pill) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = if (trailingIcon == null) 14.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = if (selected) AppTheme.type.labelBold else AppTheme.type.label,
            color = if (selected) colors.onFill else colors.onSurface,
            maxLines = 1,
        )
        trailingIcon?.let {
            Icon(it, null, Modifier.size(18.dp), tint = if (selected) colors.onFill else colors.onSurface)
        }
    }
}

/** A wrapping row of [AppChip]s that selects one of [options]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(AppShapes.Pill)
                    .background(if (isSelected) AppTheme.colors.surfaceMuted else AppTheme.colors.surface)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) AppTheme.colors.onSurface else AppTheme.colors.outline,
                        AppShapes.Pill,
                    )
                    .clickable(role = Role.Button) { onSelect(option) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { label(option) }
        }
    }
}

/** A dot of a fund's color. */
@Composable
internal fun FundDot(fund: Fund, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(Color(fund.color.argb), CircleShape))
}

/** A fund's name after a dot of its color, followed by a small [badge] if given. */
@Composable
internal fun FundLabel(fund: Fund, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FundDot(fund)
        Text(fund.name, style = AppTheme.type.label)
        badge?.let { Text(it, style = AppTheme.type.badge, color = AppTheme.colors.onSurfaceMuted) }
    }
}

/**
 * A fund chip. Selected: a tint of the fund's color with a 2dp border in it; [badge] marks the
 * category's default fund.
 */
@Composable
internal fun FundChip(fund: Fund, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    val colors = AppTheme.colors
    val fundColor = Color(fund.color.argb)
    Row(
        Modifier
            .height(44.dp)
            .clip(AppShapes.Pill)
            .background(if (selected) fundTint(fund.color.argb, colors.surface) else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) fundColor else colors.outline, AppShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FundDot(fund)
        Text(fund.name, style = if (selected) AppTheme.type.labelBold else AppTheme.type.label, maxLines = 1)
        badge?.let { Text(it, style = AppTheme.type.badge, color = colors.onSurfaceMuted) }
    }
}

/** A validation error shown under the field it is about. */
@Composable
internal fun FieldError(text: String) {
    Text(
        text,
        style = AppTheme.type.caption,
        color = AppTheme.colors.negative,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** A warning band with an icon. */
@Composable
internal fun WarningBanner(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.warningContainer, AppShapes.Banner)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.Warning, null, Modifier.size(16.dp), tint = AppTheme.colors.onWarningContainer)
        content()
    }
}

/** A 48dp icon button. */
@Composable
internal fun AppIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp)) {
        Icon(icon, contentDescription, Modifier.size(22.dp), tint = AppTheme.colors.onSurface)
    }
}

/**
 * The 60dp top bar of a screen opened over the tabs: a back arrow, [title] and [actions]. Without
 * [onBack] the title starts at the margin.
 */
@Composable
internal fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(AppSpacing.TopBar).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AppIconButton(AppIcons.Back, backLabel, onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = AppTheme.type.screenTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

/** The pill segmented control: options in a groove, the selected one filled. */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    val colors = AppTheme.colors
    Row(
        modifier.height(44.dp).background(colors.surfaceMuted, AppShapes.Pill).padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxSize()
                    .clip(AppShapes.Pill)
                    .background(if (isSelected) colors.onSurface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = if (isSelected) AppTheme.type.labelBold else AppTheme.type.label,
                    color = if (isSelected) colors.onFill else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The snackbar of the design: dark, with a light-blue action. */
@Composable
internal fun AppSnackbar(data: SnackbarData) {
    val colors = AppTheme.colors
    Snackbar(
        modifier = Modifier.padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
        containerColor = colors.snackbar,
        contentColor = colors.onSnackbar,
        shape = AppShapes.Banner,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = { data.performAction() }) {
                    Text(label, style = AppTheme.type.labelBold, color = colors.snackbarAction)
                }
            }
        },
    ) {
        Text(data.visuals.message, style = AppTheme.type.label, color = colors.onSnackbar)
    }
}

/** A list row at least 52dp tall with a divider above unless it is [first]. */
@Composable
internal fun ListRow(
    first: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = AppSpacing.Card, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
