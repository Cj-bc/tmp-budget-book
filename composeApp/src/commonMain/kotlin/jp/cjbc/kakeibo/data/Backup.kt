package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.currentDate
import jp.cjbc.kakeibo.data.files.UnreadableRecords
import jp.cjbc.kakeibo.model.LedgerData
import kotlinx.serialization.Serializable

/**
 * The version of the backup format this app writes. Version 4 added [Backup.unreadable]; this app
 * still reads version 3, which has none.
 */
const val BACKUP_VERSION = 4

/**
 * A backup file: everything the ledger saves, so that restoring it saves the same. [version] is
 * required, so JSON that is not a backup is rejected. Version 1 backups are read by
 * [jp.cjbc.kakeibo.data.v1.V1Backup], and version 2 ones are migrated by
 * [jp.cjbc.kakeibo.data.v2.migrateFromV2].
 */
@Serializable
data class Backup(
    val version: Int,
    val exportedAt: String = currentDate(),
    val data: LedgerData,
    /** The saved records the app could not read when the backup was made, as they were saved. */
    val unreadable: UnreadableRecords = UnreadableRecords(),
)
