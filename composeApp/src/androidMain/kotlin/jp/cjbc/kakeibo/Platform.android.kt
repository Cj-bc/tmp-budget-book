package jp.cjbc.kakeibo

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

private const val PREFERENCES_NAME = "kakeibo"

private lateinit var preferences: SharedPreferences

/** Opens the storage backing [readStorage] and [writeStorage]. Call before using them. */
fun initStorage(context: Context) {
    preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

actual fun readStorage(key: String): String? = preferences.getString(key, null)

actual fun writeStorage(key: String, value: String) {
    preferences.edit().putString(key, value).apply()
}

actual fun currentDate(): String = LocalDate.now().toString()
