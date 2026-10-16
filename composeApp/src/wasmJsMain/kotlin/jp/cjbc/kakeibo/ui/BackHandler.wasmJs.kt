package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The browser's back button leaves the page; the app has no history entries of its own.
}
