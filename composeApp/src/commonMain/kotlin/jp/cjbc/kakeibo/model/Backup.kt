package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.serialization.Serializable

/** The version of the backup and storage format this app writes. */
const val BACKUP_VERSION = 2

/** All data of the account book. */
@Serializable
data class LedgerData(
    val funds: List<Fund> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val allocationRules: List<AllocationRule> = emptyList(),
    val recurringItems: List<RecurringItem> = emptyList(),
)

/**
 * A backup file. [version] is required, so JSON that is not a backup is rejected. Version 1
 * backups are read by [jp.cjbc.kakeibo.data.v1.V1Backup].
 */
@Serializable
data class Backup(
    val version: Int,
    val exportedAt: String = currentDate(),
    val data: LedgerData,
)
