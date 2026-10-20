package jp.cjbc.kakeibo.data.v1

import jp.cjbc.kakeibo.model.PRESET_EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.PRESET_INCOME_CATEGORIES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The data format up to version 1, read only to migrate it. Categories are a preset's ID or the
// name of a category the user added.

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
    val expenseCategories: List<String> = PRESET_EXPENSE_CATEGORIES.map { it.legacyId },
    val incomeCategories: List<String> = PRESET_INCOME_CATEGORIES.map { it.legacyId },
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
