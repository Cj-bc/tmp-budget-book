package jp.cjbc.kakeibo

import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.localStorage
import kotlinx.browser.window

fun main() { ComposeViewport(viewportContainerId = "composeApp") { App() } }
actual fun storageGet(key: String): String? = localStorage.getItem(key)
actual fun storagePut(key: String, value: String) { localStorage.setItem(key, value) }
actual fun today(): String = js("new Date().toISOString().slice(0, 10)") as String
