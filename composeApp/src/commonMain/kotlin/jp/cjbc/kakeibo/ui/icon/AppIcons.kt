package jp.cjbc.kakeibo.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** An SVG path of a full circle. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/** An SVG path of a rounded rectangle. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

/**
 * A 24-unit outlined icon drawn with round 1.8-unit strokes, like the design's icons. [filled]
 * paths are filled instead. Tint it with `Icon(tint = …)`.
 */
private fun icon(name: String, vararg stroked: String, filled: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        stroked.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        filled.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

/** The icons of the app (Material Symbols Outlined equivalents, as drawn in the design). */
object AppIcons {
    val Add = icon("add", "M12 5v14M5 12h14")
    val Funds = icon("track_changes", circle(12f, 12f, 8.5f), circle(12f, 12f, 4.5f), circle(12f, 12f, 0.8f))
    val Recurring = icon(
        "repeat",
        "M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4",
    )
    val Analytics = icon("bar_chart", "M3 20h18", "M6 16v-5M11 16V6M16 16v-8M21 16v-3")
    val History = icon("history", "M4 12a8 8 0 1 0 2.3-5.6", "M4 4v4h4", "M12 8v4l3 2")
    val Settings = icon("tune", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val Calendar = icon("calendar_today", roundRect(3f, 4.5f, 18f, 16.5f, 2f), "M3 9.5h18M8 2.5v4M16 2.5v4")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Backspace = icon("backspace", "M21 5H9l-6 7 6 7h12z", "M17 9l-6 6M11 9l6 6")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Back = icon("arrow_back", "M19 12H5M11 6l-6 6 6 6")
    val Forward = icon("arrow_forward", "M5 12h14M13 6l6 6-6 6")
    val ChevronRight = icon("chevron_right", "M9 5l7 7-7 7")
    val ExpandMore = icon("expand_more", "M7 10l5 5 5-5")
    val Search = icon("search", circle(11f, 11f, 6.5f), "M20 20l-4.4-4.4")
    val Delete = icon("delete", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Warning = icon("warning", "M12 3l10 18H2z", "M12 10v5M12 18v.01")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val MoveUp = icon("arrow_upward", "M12 19V5M6 11l6-6 6 6")
    val MoveDown = icon("arrow_downward", "M12 5v14M6 13l6 6 6-6")
    val DragIndicator = icon(
        "drag_indicator",
        filled = listOf(9f to 6f, 15f to 6f, 9f to 12f, 15f to 12f, 9f to 18f, 15f to 18f)
            .map { (x, y) -> circle(x, y, 1.4f) },
    )
    val Pin = icon("push_pin", filled = listOf("M14 3l7 7-3 1-4 4 1 5-2 1-4-5-5 5-1-1 5-5-5-4 1-2 5 1 4-4z"))
    val Sync = icon("sync", "M20 11a8 8 0 0 0-14.3-4.9", "M4 4v4h4", "M4 13a8 8 0 0 0 14.3 4.9", "M20 20v-4h-4")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload = icon("upload", "M12 20V9M7 14l5-5 5 5M5 4h14")
    val OpenInNew = icon("open_in_new", "M14 4h6v6M20 4l-9 9", "M18 14v6H4V6h6")
}
