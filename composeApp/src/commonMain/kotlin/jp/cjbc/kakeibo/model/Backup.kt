package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: String = currentDate(),
    // Short serial names keep compatibility with previously exported backups.
    @SerialName("tx") val transactions: List<Transaction> = emptyList(),
    @SerialName("bud") val budgets: List<Budget> = emptyList(),
    @SerialName("sav") val savings: List<Saving> = emptyList(),
    @SerialName("rec") val recurringItems: List<RecurringItem> = emptyList(),
)
