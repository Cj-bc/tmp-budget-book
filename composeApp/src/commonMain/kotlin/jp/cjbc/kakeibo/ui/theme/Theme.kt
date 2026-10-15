package jp.cjbc.kakeibo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.noto_sans_jp_bold
import jp.cjbc.kakeibo.resources.noto_sans_jp_regular
import org.jetbrains.compose.resources.Font

/** The theme the user picked. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Access to the design system's tokens. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current

    val type: AppTypography
        @Composable @ReadOnlyComposable
        get() = LocalAppTypography.current
}

@Composable
internal fun KakeiboTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkAppColors else LightAppColors
    val fontFamily = FontFamily(
        Font(Res.font.noto_sans_jp_regular, FontWeight.Normal),
        Font(Res.font.noto_sans_jp_bold, FontWeight.Bold),
    )
    val base = TextStyle(fontFamily = fontFamily, color = colors.onSurface)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.onSurface,
        onPrimary = colors.onFill,
        background = colors.background,
        onBackground = colors.onSurface,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceMuted,
        onSurfaceVariant = colors.onSurfaceMuted,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surfaceMuted,
        secondaryContainer = colors.surfaceMuted,
        onSecondaryContainer = colors.onSurface,
        outline = colors.outlineStrong,
        outlineVariant = colors.outline,
        error = colors.negative,
        inverseSurface = colors.snackbar,
        inverseOnSurface = colors.onSnackbar,
        inversePrimary = colors.snackbarAction,
        scrim = colors.scrim,
    )
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides AppTypography(base),
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialTypography(fontFamily), content = content)
    }
}

/** The default Material typography rendered in Noto Sans JP. */
private fun materialTypography(fontFamily: FontFamily): Typography {
    fun TextStyle.withFont() = copy(fontFamily = fontFamily)
    return with(Typography()) {
        Typography(
            displayLarge = displayLarge.withFont(),
            displayMedium = displayMedium.withFont(),
            displaySmall = displaySmall.withFont(),
            headlineLarge = headlineLarge.withFont(),
            headlineMedium = headlineMedium.withFont(),
            headlineSmall = headlineSmall.withFont(),
            titleLarge = titleLarge.withFont(),
            titleMedium = titleMedium.withFont(),
            titleSmall = titleSmall.withFont(),
            bodyLarge = bodyLarge.withFont(),
            bodyMedium = bodyMedium.withFont(),
            bodySmall = bodySmall.withFont(),
            labelLarge = labelLarge.withFont(),
            labelMedium = labelMedium.withFont(),
            labelSmall = labelSmall.withFont(),
        )
    }
}
