package jp.cjbc.kakeibo.data.v1

import jp.cjbc.kakeibo.model.BUILT_IN_EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.BUILT_IN_INCOME_CATEGORIES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The data format up to version 1, read only to migrate it. Categories are a built-in category ID
// or the name of a user-defined category.

@Serializable
enum class V1TransactionType {
    INCOME,
    EXPENSE,
}

@Serializable
data class V1Transaction(
    val id: String,
    val date: String,
    val amount: Double,
    val type: V1TransactionType,
    val category: String,
    val description: String = "",
    val isRecurring: Boolean = false,
)

@Serializable
data class V1Budget(
    val id: String,
    val category: String,
    val monthlyAmount: Double,
)

@Serializable
data class V1RecurringItem(
    val id: String,
    val description: String,
    val amount: Double,
    val type: V1TransactionType,
    val category: String,
    val dayOfMonth: Int,
    val active: Boolean = true,
    val lastApplied: String? = null,
)

@Serializable
data class V1Settings(
    val expenseCategories: List<String> = BUILT_IN_EXPENSE_CATEGORIES.map { it.id.value },
    val incomeCategories: List<String> = BUILT_IN_INCOME_CATEGORIES.map { it.id.value },
)

/** All version 1 data. Savings are only counted, since they are discarded. */
@Serializable
data class V1Backup(
    val version: Int,
    val transactions: List<V1Transaction> = emptyList(),
    val budgets: List<V1Budget> = emptyList(),
    val savings: List<JsonElement> = emptyList(),
    val recurringItems: List<V1RecurringItem> = emptyList(),
    val settings: V1Settings = V1Settings(),
)
