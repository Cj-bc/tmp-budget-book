package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.deleteFile
import jp.cjbc.kakeibo.listFiles
import jp.cjbc.kakeibo.readFile
import jp.cjbc.kakeibo.writeFile

/**
 * Files that [Ledger] saves its data in, by paths relative to its data directory with `/` between
 * directories.
 */
interface FileStorage {
    /** The contents of the file at [path], or `null` if there is none. Sees every write made before. */
    suspend fun read(path: String): String?

    /** The names of the files directly in [directory]. */
    suspend fun list(directory: String): List<String>

    /** Replaces the file at [path]. May finish later, but in order with the other writes and deletes. */
    fun write(path: String, content: String)

    /** Deletes the file at [path] if there is one, in order with [write]. */
    fun delete(path: String)
}

/** The app's own data directory on the platform. */
object PlatformFileStorage : FileStorage {
    override suspend fun read(path: String): String? = readFile(path)

    override suspend fun list(directory: String): List<String> = listFiles(directory)

    override fun write(path: String, content: String) = writeFile(path, content)

    override fun delete(path: String) = deleteFile(path)
}
