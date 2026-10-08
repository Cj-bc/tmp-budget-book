package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.serialization.Serializable

/** The version of the backup format this app writes. */
const val BACKUP_VERSION = 1

/**
 * All data of the account book. [version] is required, so JSON that is not a backup is rejected.
 */
@Serializable
data class Backup(
    val version: Int,
    val exportedAt: String = currentDate(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val savings: List<Saving> = emptyList(),
    val recurringItems: List<RecurringItem> = emptyList(),
)
