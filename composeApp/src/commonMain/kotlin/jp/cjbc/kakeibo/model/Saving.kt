package jp.cjbc.kakeibo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Saving(
    val id: String,
    val name: String,
    val monthlyTarget: Double,
    @SerialName("startYM") val startYearMonth: String,
    @SerialName("targetYM") val targetYearMonth: String,
    val contributions: List<Contribution> = emptyList(),
)
