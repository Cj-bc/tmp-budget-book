package jp.cjbc.kakeibo.ui.theme

import jp.cjbc.kakeibo.data.KeyValueStorage
import jp.cjbc.kakeibo.data.PlatformStorage

private const val THEME_KEY = "kakeibo:v2:theme"

/** The theme picked in the settings, [ThemeMode.SYSTEM] until one is. */
internal fun readThemeMode(storage: KeyValueStorage = PlatformStorage): ThemeMode =
    ThemeMode.entries.find { it.name == storage.read(THEME_KEY) } ?: ThemeMode.SYSTEM

internal fun writeThemeMode(mode: ThemeMode, storage: KeyValueStorage = PlatformStorage) {
    storage.write(THEME_KEY, mode.name)
}
