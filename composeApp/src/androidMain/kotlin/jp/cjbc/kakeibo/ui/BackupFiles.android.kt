package jp.cjbc.kakeibo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Uses the system's document picker to create and open files. */
@Composable
internal actual fun rememberBackupFiles(): BackupFiles {
    val resolver = LocalContext.current.contentResolver
    // What to write once the user has chosen where, and what to do after.
    val pendingSave = remember { arrayOfNulls<Pair<String, () -> Unit>>(1) }
    val pendingOpen = remember { arrayOfNulls<(String) -> Unit>(1) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val (text, onSaved) = pendingSave[0] ?: return@rememberLauncherForActivityResult
        pendingSave[0] = null
        if (uri == null) return@rememberLauncherForActivityResult
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        onSaved()
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onOpened = pendingOpen[0] ?: return@rememberLauncherForActivityResult
        pendingOpen[0] = null
        if (uri == null) return@rememberLauncherForActivityResult
        resolver.openInputStream(uri)?.use { onOpened(it.readBytes().decodeToString()) }
    }
    return remember(createLauncher, openLauncher) {
        object : BackupFiles {
            override fun save(fileName: String, text: String, onSaved: () -> Unit) {
                pendingSave[0] = text to onSaved
                createLauncher.launch(fileName)
            }

            override fun open(onOpened: (String) -> Unit) {
                pendingOpen[0] = onOpened
                openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    }
}
