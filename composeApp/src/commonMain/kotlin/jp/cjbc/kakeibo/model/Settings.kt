package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

/**
 * User preferences edited on the settings screen.
 *
 * Categories are stored the same way as in transactions: a built-in [Category.id] or the
 * name of a user-defined category.
 */
@Serializable
data class Settings(
    val expenseCategories: List<String> = EXPENSE_CATEGORIES.map { it.id },
    val incomeCategories: List<String> = INCOME_CATEGORIES.map { it.id },
) {
    /** Returns the categories offered for transactions of the given [type]. */
    fun categoriesFor(type: TransactionType): List<String> =
        if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    /** Returns a copy whose categories for [type] are replaced with [categories]. */
    fun withCategories(type: TransactionType, categories: List<String>): Settings =
        if (type == TransactionType.EXPENSE) {
            copy(expenseCategories = categories)
        } else {
            copy(incomeCategories = categories)
        }
}
