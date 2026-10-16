@file:OptIn(ExperimentalWasmJsInterop::class)

package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Downloads [text] as a JSON file named [fileName]. */
private fun download(fileName: String, text: String): Unit = js(
    """{
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    link.download = fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
    }""",
)

/** Opens the browser's file picker and passes the chosen file's text to [onText]. */
private fun pickFile(onText: (String) -> Unit): Unit = js(
    """{
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json,text/plain';
    input.style.display = 'none';
    input.onchange = () => {
        if (input.files.length > 0) input.files[0].text().then(onText);
        input.remove();
    };
    input.oncancel = () => input.remove();
    document.body.appendChild(input);
    input.click();
    }""",
)

@Composable
internal actual fun rememberBackupFiles(): BackupFiles = remember {
    object : BackupFiles {
        override fun save(fileName: String, text: String, onSaved: () -> Unit) {
            download(fileName, text)
            onSaved()
        }

        override fun open(onOpened: (String) -> Unit) = pickFile(onOpened)
    }
}
