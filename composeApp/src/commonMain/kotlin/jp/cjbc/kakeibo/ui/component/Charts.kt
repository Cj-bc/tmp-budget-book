package jp.cjbc.kakeibo.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.max

// Charts drawn on a Canvas, so no chart library is needed. Each has one bar or point per label,
// and the last one (the current month) is emphasized.

/** Month labels under a chart, the last in bold. */
@Composable
internal fun ChartLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                label,
                style = AppTheme.type.badge.merge(AppTheme.type.numeric).let {
                    if (index == labels.lastIndex) it.copy(fontWeight = FontWeight.Bold) else it
                },
                color = if (index == labels.lastIndex) AppTheme.colors.onSurface else AppTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun DrawScope.bar(left: Float, top: Float, width: Float, height: Float, color: Color) {
    if (height <= 0f) return
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(minOf(width, height) / 4))
}

/**
 * Bars around a zero line: positive [values] rise in [positive], negative ones hang below in
 * [negative]. Earlier bars are lighter.
 */
@Composable
internal fun ZeroCenteredBars(values: List<Long>, positive: Color, negative: Color, height: Dp = 124.dp) {
    val zeroLine = AppTheme.colors.outlineStrong
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val maxUp = max(1L, values.maxOfOrNull { it } ?: 0).coerceAtLeast(0).toFloat()
        val maxDown = max(0L, -(values.minOfOrNull { it } ?: 0)).toFloat()
        val total = (maxUp + maxDown).coerceAtLeast(1f)
        val zeroY = size.height * maxUp / total
        val slot = size.width / values.size
        val width = slot * 0.6f
        values.forEachIndexed { index, value ->
            val left = slot * index + (slot - width) / 2
            val alpha = if (index == values.lastIndex) 1f else 0.5f
            val length = size.height * abs(value) / total
            if (value >= 0) {
                bar(left, zeroY - length, width, length, positive.copy(alpha = alpha))
            } else {
                bar(left, zeroY, width, length, negative.copy(alpha = alpha))
            }
        }
        drawLine(zeroLine, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
    }
}

/** Two bars per slot side by side, e.g. income and expense. */
@Composable
internal fun PairedBars(first: List<Long>, second: List<Long>, firstColor: Color, secondColor: Color, height: Dp = 124.dp) {
    val baseline = AppTheme.colors.outline
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val top = max(1L, (first + second).maxOrNull() ?: 0).toFloat()
        val slot = size.width / first.size
        val width = slot * 0.3f
        first.indices.forEach { index ->
            val alpha = if (index == first.lastIndex) 0.6f else 1f
            val center = slot * index + slot / 2
            val h1 = size.height * first[index] / top
            val h2 = size.height * second[index] / top
            bar(center - width - 1.dp.toPx(), size.height - h1, width, h1, firstColor.copy(alpha = alpha))
            bar(center + 1.dp.toPx(), size.height - h2, width, h2, secondColor.copy(alpha = alpha))
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}

/** Stacked bars: each slot's segments ([Color] and amount) stacked from the bottom. */
@Composable
internal fun StackedBars(stacks: List<List<Pair<Color, Long>>>, height: Dp = 104.dp) {
    val baseline = AppTheme.colors.outline
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val top = max(1L, stacks.maxOfOrNull { stack -> stack.sumOf { it.second } } ?: 0).toFloat()
        val slot = size.width / stacks.size.coerceAtLeast(1)
        val width = slot * 0.6f
        stacks.forEachIndexed { index, stack ->
            val alpha = if (index == stacks.lastIndex) 0.7f else 1f
            var bottom = size.height
            stack.forEach { (color, amount) ->
                val h = size.height * amount / top
                drawRect(color.copy(alpha = alpha), Offset(slot * index + (slot - width) / 2, bottom - h), Size(width, h))
                bottom -= h
            }
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}

/** A line with a filled area under it, scaled from the lowest to the highest value. */
@Composable
internal fun LineArea(values: List<Long>, color: Color, height: Dp = 96.dp) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val low = minOf(0L, values.min()).toFloat()
        val high = max(values.max().toFloat(), low + 1f)
        val pad = 4.dp.toPx()
        fun point(index: Int): Offset = Offset(
            if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1),
            pad + (size.height - 2 * pad) * (1 - (values[index] - low) / (high - low)),
        )
        val line = Path().apply {
            values.indices.forEach { if (it == 0) moveTo(point(it).x, point(it).y) else lineTo(point(it).x, point(it).y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, size.height)
            lineTo(point(0).x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, 4.dp.toPx(), point(values.lastIndex))
    }
}

/** A single bar split by share: segments of the given colors and amounts. */
@Composable
internal fun ShareBar(segments: List<Pair<Color, Long>>, height: Dp = 12.dp) {
    val track = AppTheme.colors.track
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val total = segments.sumOf { it.second }.coerceAtLeast(1).toFloat()
        drawRoundRect(track, Offset.Zero, size, CornerRadius(size.height / 2))
        var left = 0f
        segments.forEach { (color, amount) ->
            val w = size.width * amount / total
            drawRect(color, Offset(left, 0f), Size(w, size.height))
            left += w
        }
    }
}

/** A chart with its labels under it. */
@Composable
internal fun LabeledChart(labels: List<String>, chart: @Composable () -> Unit) {
    Column {
        chart()
        ChartLabels(labels)
    }
}
