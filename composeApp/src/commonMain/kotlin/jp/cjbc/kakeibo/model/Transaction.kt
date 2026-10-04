package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: String,
    val date: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String = "",
    val isRecurring: Boolean = false,
)
