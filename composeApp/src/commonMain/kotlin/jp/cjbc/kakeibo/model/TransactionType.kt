package jp.cjbc.kakeibo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    // Serial names keep compatibility with data saved before the constants were renamed.
    @SerialName("income")
    INCOME,

    @SerialName("expense")
    EXPENSE,
}
