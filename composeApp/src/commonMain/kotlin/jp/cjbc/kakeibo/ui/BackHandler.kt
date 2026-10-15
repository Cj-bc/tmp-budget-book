package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Composable

/** Calls [onBack] on the platform's back gesture while [enabled]. The browser has none. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
