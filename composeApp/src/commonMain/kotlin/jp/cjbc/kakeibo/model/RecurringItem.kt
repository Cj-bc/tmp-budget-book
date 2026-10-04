package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
data class RecurringItem(
    val id: String,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val dayOfMonth: Int,
    val active: Boolean = true,
    val lastApplied: String? = null,
)
