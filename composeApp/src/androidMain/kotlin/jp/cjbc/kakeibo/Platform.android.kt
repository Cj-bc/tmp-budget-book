package jp.cjbc.kakeibo

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.time.LocalDate

private const val PREFERENCES_NAME = "kakeibo"

/** The directory in the app's own storage that holds the ledger's files. */
private const val DATA_DIRECTORY = "ledger"

private lateinit var preferences: SharedPreferences
private lateinit var dataDirectory: File

/** Opens the storage backing [readStorage], [writeStorage] and the files. Call before using them. */
internal fun initStorage(context: Context) {
    preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    dataDirectory = File(context.filesDir, DATA_DIRECTORY)
}

actual fun readStorage(key: String): String? = preferences.getString(key, null)

actual fun writeStorage(key: String, value: String) {
    preferences.edit().putString(key, value).apply()
}

actual suspend fun readFile(path: String): String? =
    File(dataDirectory, path).takeIf { it.isFile }?.readText()

actual suspend fun listFiles(directory: String): List<String> =
    File(dataDirectory, directory).listFiles { file -> file.isFile }.orEmpty().map { it.name }

actual fun writeFile(path: String, content: String) {
    val file = File(dataDirectory, path)
    file.parentFile?.mkdirs()
    // Write a copy and rename it, so that a crash never leaves the file half written.
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeText(content)
    if (!temporary.renameTo(file)) throw IOException("could not replace $file")
}

actual fun deleteFile(path: String) {
    File(dataDirectory, path).delete()
}

actual fun currentDate(): String = LocalDate.now().toString()
