package jp.cjbc.kakeibo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A category of transactions, of expenses or of incomes. */
@Serializable
sealed interface Category {
    val id: CategoryId

    /** The name shown on screens. */
    val name: String

    /** Whether the entry screen's category grid shows this category. Only expenses use it. */
    val pinned: Boolean
    val sortOrder: Int
    val archived: Boolean

    @Serializable
    @SerialName("expense")
    data class Expense(
        override val id: CategoryId,
        override val name: String,
        override val pinned: Boolean,
        override val sortOrder: Int,
        override val archived: Boolean,
        /** The fund an expense of this category is paid from unless the user picks another. */
        val defaultFundId: FundId,
    ) : Category

    @Serializable
    @SerialName("income")
    data class Income(
        override val id: CategoryId,
        override val name: String,
        override val pinned: Boolean,
        override val sortOrder: Int,
        override val archived: Boolean,
    ) : Category
}
