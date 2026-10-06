package jp.cjbc.kakeibo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.noto_sans_jp_bold
import jp.cjbc.kakeibo.resources.noto_sans_jp_regular
import org.jetbrains.compose.resources.Font

@Composable
internal fun KakeiboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ACCENT_COLOR,
            background = BACKGROUND_COLOR,
            surface = SURFACE_COLOR,
        ),
        typography = appTypography(),
        content = content,
    )
}

/** Returns the default Material typography rendered in Noto Sans JP. */
@Composable
private fun appTypography(): Typography {
    val fontFamily = FontFamily(
        Font(Res.font.noto_sans_jp_regular, FontWeight.Normal),
        Font(Res.font.noto_sans_jp_bold, FontWeight.Bold),
    )
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
