package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
data class Budget(
    val id: String,
    val category: String,
    val monthlyAmount: Double,
)
