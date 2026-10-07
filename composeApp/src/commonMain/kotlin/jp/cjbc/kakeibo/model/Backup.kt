package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: String = currentDate(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val savings: List<Saving> = emptyList(),
    val recurringItems: List<RecurringItem> = emptyList(),
    val settings: Settings = Settings(),
)
