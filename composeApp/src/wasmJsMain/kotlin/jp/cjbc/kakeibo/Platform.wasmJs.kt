@file:OptIn(ExperimentalWasmJsInterop::class)

package jp.cjbc.kakeibo

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise
import kotlinx.browser.localStorage

actual fun readStorage(key: String): String? = localStorage.getItem(key)

actual fun writeStorage(key: String, value: String) {
    localStorage.setItem(key, value)
}

actual suspend fun readFile(path: String): String? = opfsRead(opfs(), path).await()?.toString()

actual suspend fun listFiles(directory: String): List<String> =
    opfsList(opfs(), directory).await().toString().split('\n').filter { it.isNotEmpty() }

actual fun writeFile(path: String, content: String) = opfsWrite(opfs(), path, content)

actual fun deleteFile(path: String) = opfsDelete(opfs(), path)

actual fun currentDate(): String = js("new Date().toISOString().slice(0, 10)")

private suspend fun <T : JsAny?> Promise<T>.await(): T = suspendCoroutine { continuation ->
    then<JsAny?>(
        { value -> continuation.resume(value); null },
        { error -> continuation.resumeWithException(IllegalStateException(error.toString())); null },
    )
}

// The files live in the "ledger" directory of the origin private file system (OPFS). Writes are
// chained on one promise, so they happen in order, and reads wait for the writes made before.
// The browser is asked to keep the storage instead of evicting it under storage pressure.
private fun opfs(): JsAny = js(
    """
    globalThis.kakeiboOpfs ??= (() => {
        navigator.storage.persist?.().catch(() => {});
        const directory = async (parts, create) => {
            let handle = await (await navigator.storage.getDirectory()).getDirectoryHandle('ledger', { create });
            for (const part of parts) handle = await handle.getDirectoryHandle(part, { create });
            return handle;
        };
        const parent = (path, create) => {
            const parts = path.split('/');
            return directory(parts.slice(0, -1), create).then(handle => [handle, parts[parts.length - 1]]);
        };
        const ignoreMissing = error => { if (error.name !== 'NotFoundError') throw error; return null; };
        let queue = Promise.resolve();
        const enqueue = (description, task) => {
            queue = queue.then(task).catch(error => console.error('Ledger: could not ' + description, error));
        };
        return {
            read: path => queue.then(async () => {
                const [handle, name] = await parent(path, false);
                return await (await (await handle.getFileHandle(name)).getFile()).text();
            }).catch(ignoreMissing),
            list: path => queue.then(async () => {
                const names = [];
                for await (const [name, entry] of (await directory(path.split('/'), false)).entries()) {
                    if (entry.kind === 'file') names.push(name);
                }
                return names.join('\n');
            }).catch(error => { ignoreMissing(error); return ''; }),
            write: (path, content) => enqueue('write ' + path, async () => {
                const [handle, name] = await parent(path, true);
                const writable = await (await handle.getFileHandle(name, { create: true })).createWritable();
                await writable.write(content);
                await writable.close();
            }),
            remove: path => enqueue('delete ' + path, async () => {
                try {
                    const [handle, name] = await parent(path, false);
                    await handle.removeEntry(name);
                } catch (error) {
                    ignoreMissing(error);
                }
            }),
        };
    })()
    """,
)

private fun opfsRead(files: JsAny, path: String): Promise<JsString?> = js("files.read(path)")

/** The names, separated by newlines. */
private fun opfsList(files: JsAny, directory: String): Promise<JsString> = js("files.list(directory)")

private fun opfsWrite(files: JsAny, path: String, content: String): Unit = js("files.write(path, content)")

private fun opfsDelete(files: JsAny, path: String): Unit = js("files.remove(path)")
