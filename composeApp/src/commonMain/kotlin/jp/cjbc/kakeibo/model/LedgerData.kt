package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

/** All data of the account book. */
@Serializable
data class LedgerData(
    val funds: List<Fund> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val allocationRules: List<AllocationRule> = emptyList(),
    val recurringItems: List<RecurringItem> = emptyList(),
)
