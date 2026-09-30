package jp.cjbc.kakeibo

import kotlinx.serialization.Serializable

@Serializable enum class TransactionType { income, expense }

@Serializable data class Transaction(
    val id: String, val date: String, val amount: Double, val type: TransactionType,
    val category: String, val description: String = "", val isRecurring: Boolean = false,
)
@Serializable data class Budget(val id: String, val category: String, val monthlyAmount: Double)
@Serializable data class Contribution(val id: String, val date: String, val amount: Double)
@Serializable data class Saving(
    val id: String, val name: String, val monthlyTarget: Double, val startYM: String,
    val targetYM: String, val contributions: List<Contribution> = emptyList(),
)
@Serializable data class RecurringItem(
    val id: String, val description: String, val amount: Double, val type: TransactionType,
    val category: String, val dayOfMonth: Int, val active: Boolean = true, val lastApplied: String? = null,
)
@Serializable data class Backup(
    val version: Int = 1, val exportedAt: String = today(), val tx: List<Transaction> = emptyList(),
    val bud: List<Budget> = emptyList(), val sav: List<Saving> = emptyList(), val rec: List<RecurringItem> = emptyList(),
)

val expenseCategories = listOf("食費", "交通費", "住居費", "光熱費", "医療費", "娯楽費", "衣服費", "日用品", "教育費", "通信費", "その他")
val incomeCategories = listOf("給与", "副収入", "賞与", "その他")
val categoryColors = mapOf("食費" to 0xFFE85D4A, "交通費" to 0xFFE8914A, "住居費" to 0xFFD4A843, "光熱費" to 0xFF7BC67A, "医療費" to 0xFF5DB8E8, "娯楽費" to 0xFF9B6BE8, "衣服費" to 0xFFE86BAF, "日用品" to 0xFF6BB5E8, "教育費" to 0xFF4A9E8B, "通信費" to 0xFF8B8B8B, "その他" to 0xFFB0B0B0, "給与" to 0xFF2D6A4F, "副収入" to 0xFF4A9E8B, "賞与" to 0xFFD4A843)
