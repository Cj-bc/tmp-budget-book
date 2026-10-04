package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
data class Contribution(
    val id: String,
    val date: String,
    val amount: Double,
)
