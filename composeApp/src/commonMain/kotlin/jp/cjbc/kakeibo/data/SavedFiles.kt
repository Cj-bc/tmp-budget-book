package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.TRANSACTIONS_DIRECTORY
import jp.cjbc.kakeibo.data.files.UnreadableMasterException
import jp.cjbc.kakeibo.data.files.layoutRecords
import jp.cjbc.kakeibo.data.files.readRecords
import jp.cjbc.kakeibo.data.files.unreadableRecords

/** The transaction files in [this], by path. */
internal suspend fun FileStorage.readTransactionFiles(): Map<String, String> = list(TRANSACTIONS_DIRECTORY)
    .filter { it.endsWith(".jsonl") }
    .map { "$TRANSACTIONS_DIRECTORY/$it" }
    .associateWith { read(it).orEmpty() }

/**
 * The records saved in [files] as they were when read, neither decoded nor checked, so that the
 * ones the app cannot show can be looked into and fixed. Read them with [SavedFiles.read].
 */
class SavedFiles private constructor(private val files: FileStorage, private val contents: Map<String, String>) {
    /** The records, or `null` without a master.json that can be read. */
    val records: Map<RecordKey, SavedRecord>? = if (MASTER_FILE !in contents) {
        null
    } else {
        try {
            readRecords(contents)
        } catch (_: UnreadableMasterException) {
            null
        }
    }

    /** The records that the app cannot show. */
    fun hidden(): Set<Pair<RecordKey, SavedRecord>> =
        records?.let { unreadableRecords(it).map { (key, record) -> key to record }.toSet() }.orEmpty()

    /** The number of the line [record] is on in its file, counting from 1, or `null` if it is not there. */
    fun lineNumber(record: SavedRecord.TransactionLine): Int? =
        contents[record.file]?.lines()?.indexOf(record.line)?.takeIf { it >= 0 }?.plus(1)

    /** Saves [records] in place of the ones read, and commits them with [message] by [author]. */
    fun save(records: Map<RecordKey, SavedRecord>, message: String, author: CommitAuthor) {
        val saved = layoutRecords(records)
        saved.forEach { (path, content) -> if (contents[path] != content) files.write(path, content) }
        (contents.keys - saved.keys).forEach(files::delete)
        files.commit(message, author)
    }

    companion object {
        suspend fun read(files: FileStorage): SavedFiles {
            val master = files.read(MASTER_FILE)
            return SavedFiles(files, files.readTransactionFiles() + listOfNotNull(master?.let { MASTER_FILE to it }))
        }
    }
}
