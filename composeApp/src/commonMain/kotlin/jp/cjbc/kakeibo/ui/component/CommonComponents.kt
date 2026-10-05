package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.BORDER_COLOR
import jp.cjbc.kakeibo.ui.theme.CARD_SHAPE
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.ui.theme.SURFACE_COLOR
import jp.cjbc.kakeibo.ui.theme.TEXT_COLOR
import jp.cjbc.kakeibo.util.formatSignedYen
import jp.cjbc.kakeibo.util.formatYen
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

/** Shows [value] in yen, signed and colored by [type] when it is given. */
@Composable
internal fun AmountText(value: Double, type: TransactionType? = null) {
    Text(
        if (type == null) formatYen(value) else formatSignedYen(value, type),
        color = when (type) {
            TransactionType.INCOME -> INCOME_COLOR
            TransactionType.EXPENSE -> EXPENSE_COLOR
            null -> TEXT_COLOR
        },
        fontWeight = FontWeight.Bold,
    )
}

/** Shows the ratio of [value] to [total], turning red once [value] exceeds [total]. */
@Composable
internal fun AmountProgressBar(value: Double, total: Double, color: Color = ACCENT_COLOR) {
    LinearProgressIndicator(
        progress = { min(1.0, if (total == 0.0) 0.0 else value / total).toFloat() },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = if (value > total && total > 0) EXPENSE_COLOR else color,
        trackColor = BORDER_COLOR,
    )
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
