package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Composable

/** Saving a backup to a file and reading one back, through the platform's file pickers. */
internal interface BackupFiles {
    /** Saves [text] as a file named [fileName], then calls [onSaved]. */
    fun save(fileName: String, text: String, onSaved: () -> Unit)

    /** Lets the user pick a file, then calls [onOpened] with its text. */
    fun open(onOpened: (String) -> Unit)
}

@Composable
internal expect fun rememberBackupFiles(): BackupFiles
