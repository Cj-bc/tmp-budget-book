package jp.cjbc.kakeibo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
internal fun KakeiboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ACCENT_COLOR,
            background = BACKGROUND_COLOR,
            surface = SURFACE_COLOR,
        ),
        content = content,
    )
}
