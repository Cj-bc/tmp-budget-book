package jp.cjbc.kakeibo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.time.LocalDate

private lateinit var preferences: android.content.SharedPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("kakeibo", Context.MODE_PRIVATE)
        setContent { App() }
    }
}

actual fun storageGet(key: String): String? = preferences.getString(key, null)
actual fun storagePut(key: String, value: String) { preferences.edit().putString(key, value).apply() }
actual fun today(): String = LocalDate.now().toString()
