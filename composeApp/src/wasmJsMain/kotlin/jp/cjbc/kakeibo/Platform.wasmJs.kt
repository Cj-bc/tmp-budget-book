package jp.cjbc.kakeibo

import kotlinx.browser.localStorage

actual fun readStorage(key: String): String? = localStorage.getItem(key)

actual fun writeStorage(key: String, value: String) {
    localStorage.setItem(key, value)
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun currentDate(): String = js("new Date().toISOString().slice(0, 10)")
