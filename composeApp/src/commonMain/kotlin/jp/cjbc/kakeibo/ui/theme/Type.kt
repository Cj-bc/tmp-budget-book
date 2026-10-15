package jp.cjbc.kakeibo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Tabular figures, slightly tightened, for every number on screen. */
private fun TextStyle.numeric() = copy(fontFeatureSettings = "tnum", letterSpacing = (-0.02).em)

/** The text styles of the design system, built on [base] (the font and text color). */
@Immutable
class AppTypography(base: TextStyle) {
    /** The amount on the entry screen. */
    val entryAmount = base.copy(fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp).numeric()
    /** A main balance. */
    val balance = base.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp).numeric()
    val screenTitle = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val month = base.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    /** An amount in a list. */
    val listAmount = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold).numeric()
    val body = base.copy(fontSize = 15.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    /** Buttons and chips. */
    val label = base.copy(fontSize = 14.sp)
    val labelBold = label.copy(fontWeight = FontWeight.Bold)
    val sectionHeader = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val caption = base.copy(fontSize = 12.sp)
    /** Badges and chart axes; nothing is smaller. */
    val badge = base.copy(fontSize = 11.sp)
    val keypad = base.copy(fontSize = 24.sp).numeric()
    val numeric = base.numeric()
}

internal val LocalAppTypography = staticCompositionLocalOf { AppTypography(TextStyle.Default) }
