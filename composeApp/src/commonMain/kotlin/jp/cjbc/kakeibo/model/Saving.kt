package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
data class Saving(
    val id: String,
    val name: String,
    val monthlyTarget: Double,
    val startYearMonth: String,
    val targetYearMonth: String,
    val contributions: List<Contribution> = emptyList(),
)
