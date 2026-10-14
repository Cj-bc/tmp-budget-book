package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.BORDER_COLOR
import jp.cjbc.kakeibo.ui.theme.CARD_SHAPE
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.SURFACE_COLOR
import jp.cjbc.kakeibo.ui.theme.TEXT_COLOR
import jp.cjbc.kakeibo.util.formatAmount
import kotlin.math.min

/** Vertically scrollable container used as the root of each screen. */
@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(SURFACE_COLOR, CARD_SHAPE)
            .border(1.dp, BORDER_COLOR, CARD_SHAPE)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        color = MUTED_COLOR,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

/**
 * Shows the yen [amount]. Positive amounts start with `+` and are in the income color if
 * [signed]; negative amounts are always in the expense color.
 */
@Composable
internal fun AmountText(amount: Long, signed: Boolean = false, fontSize: TextUnit = TextUnit.Unspecified) {
    Text(
        formatAmount(amount, signed),
        color = when {
            amount < 0 -> EXPENSE_COLOR
            amount > 0 && signed -> INCOME_COLOR
            else -> TEXT_COLOR
        },
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
    )
}

/** Shows the ratio of [value] to [total]; full and in the expense color when [value] is negative. */
@Composable
internal fun AmountProgressBar(value: Long, total: Long, color: Color = ACCENT_COLOR) {
    LinearProgressIndicator(
        progress = {
            when {
                value < 0 -> 1f
                total <= 0 -> 0f
                else -> min(1.0, value.toDouble() / total).toFloat()
            }
        },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = if (value < 0) EXPENSE_COLOR else color,
        trackColor = BORDER_COLOR,
    )
}

/** A wrapping row of chips that selects one of [options]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { label(option) },
            )
        }
    }
}

/** A fund's name after a dot of its color. */
@Composable
internal fun FundLabel(fund: Fund, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(fund.color.argb), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(fund.name + suffix)
    }
}

/** A validation error shown under the field it is about. */
@Composable
internal fun FieldError(text: String) {
    Text(text, color = EXPENSE_COLOR, fontSize = 12.sp)
}

@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick,
        modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) ACCENT_COLOR else SURFACE_COLOR,
            contentColor = if (isPrimary) Color.White else ACCENT_COLOR,
        ),
        border = if (isPrimary) null else ButtonDefaults.outlinedButtonBorder,
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
internal fun LabeledTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
internal fun EmptyMessage(text: String) {
    Text(
        text,
        color = MUTED_COLOR,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(28.dp),
    )
}

/** A small label-and-value pair used in summary rows. */
@Composable
internal fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MUTED_COLOR, fontSize = 10.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
