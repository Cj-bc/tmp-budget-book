package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    INCOME,
    EXPENSE,
}
