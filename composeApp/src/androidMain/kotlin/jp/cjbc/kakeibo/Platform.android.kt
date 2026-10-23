package jp.cjbc.kakeibo

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.time.LocalDate
import jp.cjbc.kakeibo.data.CommitAuthor
import jp.cjbc.kakeibo.data.GitDataDirectory
import jp.cjbc.kakeibo.data.RemoteSync
import jp.cjbc.kakeibo.sync.GitHubSync

private const val PREFERENCES_NAME = "kakeibo"

/** The directory in the app's own storage that holds the ledger's files. */
private const val DATA_DIRECTORY = "ledger"

private lateinit var preferences: SharedPreferences
private lateinit var dataDirectory: GitDataDirectory
internal lateinit var gitHubSync: GitHubSync

/** Opens the storage backing [readStorage], [writeStorage] and the files. Call before using them. */
internal fun initStorage(context: Context) {
    preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    if (!::dataDirectory.isInitialized) {
        dataDirectory = GitDataDirectory(File(context.filesDir, DATA_DIRECTORY))
        gitHubSync = GitHubSync(context, dataDirectory)
    }
}

actual fun readStorage(key: String): String? = preferences.getString(key, null)

actual fun writeStorage(key: String, value: String) {
    preferences.edit().putString(key, value).apply()
}

actual suspend fun readFile(path: String): String? = dataDirectory.read(path)

actual suspend fun listFiles(directory: String): List<String> = dataDirectory.list(directory)

actual fun writeFile(path: String, content: String) = dataDirectory.write(path, content)

actual fun deleteFile(path: String) = dataDirectory.delete(path)

actual fun commitFiles(message: String, authorName: String, authorEmail: String) {
    dataDirectory.commit(message, CommitAuthor(authorName, authorEmail))
    gitHubSync.push()
}

actual fun remoteSync(): RemoteSync? = gitHubSync

actual fun currentDate(): String = LocalDate.now().toString()
