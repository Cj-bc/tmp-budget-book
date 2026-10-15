package jp.cjbc.kakeibo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner shapes of the design system. Chips, segments and bars are pills: [Pill]. */
object AppShapes {
    val Card = RoundedCornerShape(16.dp)
    val DayGroup = RoundedCornerShape(14.dp)
    val Tile = RoundedCornerShape(12.dp)
    val Banner = RoundedCornerShape(10.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Spacing of the design system. */
object AppSpacing {
    /** Left and right margin of a screen. */
    val Screen = 12.dp
    /** Padding inside a card. */
    val Card = 16.dp
    val TopBar = 60.dp
    val BottomBar = 72.dp
}
