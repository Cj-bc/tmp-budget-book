package jp.cjbc.kakeibo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The color tokens of the design system. */
@Immutable
data class AppColors(
    /** Screen background. */
    val background: Color,
    /** Cards, the top and bottom bars, keypad keys. */
    val surface: Color,
    /** Segment grooves, the backspace key, the tab indicator. */
    val surfaceMuted: Color,
    /** The groove of bars. */
    val track: Color,
    val outline: Color,
    /** Strong outlines, such as unselected chips. */
    val outlineStrong: Color,
    val divider: Color,
    /** Body text, and the fill of primary buttons and selected chips. */
    val onSurface: Color,
    /** Text on an [onSurface] fill. */
    val onFill: Color,
    /** Secondary text and section headers. */
    val onSurfaceMuted: Color,
    val disabled: Color,
    /** The dashed border of the "all categories" tile. */
    val dashed: Color,
    /** Income and positive amounts. */
    val positive: Color,
    /** Expenses, negative amounts, overspending, being behind plan. */
    val negative: Color,
    /** Balanced, ahead of plan. */
    val success: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val infoDivider: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val snackbarAction: Color,
    /** Behind bottom sheets. */
    val scrim: Color,
)

/** The light theme, as designed. */
val LightAppColors = AppColors(
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFE6E8EC),
    track = Color(0xFFECEEF1),
    outline = Color(0xFFDFE2E7),
    outlineStrong = Color(0xFFC9CDD4),
    divider = Color(0xFFEEF0F3),
    onSurface = Color(0xFF14171C),
    onFill = Color(0xFFFFFFFF),
    onSurfaceMuted = Color(0xFF5A616D),
    disabled = Color(0xFF8A909A),
    dashed = Color(0xFFB5BAC3),
    positive = Color(0xFF1A5BC4),
    negative = Color(0xFFB04A0A),
    success = Color(0xFF1F6146),
    warningContainer = Color(0xFFFBEFE6),
    onWarningContainer = Color(0xFF6E2E05),
    infoContainer = Color(0xFFE3ECFA),
    onInfoContainer = Color(0xFF123B80),
    infoDivider = Color(0xFFC6D6F2),
    snackbar = Color(0xFF2A2E35),
    onSnackbar = Color(0xFFFFFFFF),
    snackbarAction = Color(0xFF9DC0FF),
    scrim = Color(0x7314171C),
)

/**
 * The dark theme. The design defines no dark tokens, so these mirror the light ones: the same
 * roles, with lightness inverted and the accents lightened to keep their contrast.
 */
val DarkAppColors = AppColors(
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D23),
    surfaceMuted = Color(0xFF2A2E36),
    track = Color(0xFF2A2E36),
    outline = Color(0xFF30343C),
    outlineStrong = Color(0xFF4A505B),
    divider = Color(0xFF262A31),
    onSurface = Color(0xFFECEEF2),
    onFill = Color(0xFF14171C),
    onSurfaceMuted = Color(0xFFA3A9B4),
    disabled = Color(0xFF6F7580),
    dashed = Color(0xFF5A606B),
    positive = Color(0xFF8AB4F8),
    negative = Color(0xFFF2964F),
    success = Color(0xFF6CC79A),
    warningContainer = Color(0xFF3A2414),
    onWarningContainer = Color(0xFFF7CFAE),
    infoContainer = Color(0xFF17243A),
    onInfoContainer = Color(0xFFBCD2F7),
    infoDivider = Color(0xFF2B3D5C),
    snackbar = Color(0xFFE6E8EC),
    onSnackbar = Color(0xFF14171C),
    snackbarAction = Color(0xFF1A5BC4),
    scrim = Color(0x99000000),
)

internal val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** The light tint a selected fund chip is filled with: [argb] mixed 14% into [background]. */
fun fundTint(argb: Long, background: Color): Color {
    val color = Color(argb)
    val ratio = 0.14f
    return Color(
        red = background.red + (color.red - background.red) * ratio,
        green = background.green + (color.green - background.green) * ratio,
        blue = background.blue + (color.blue - background.blue) * ratio,
    )
}
