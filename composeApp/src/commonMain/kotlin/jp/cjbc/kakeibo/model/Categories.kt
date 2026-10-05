package jp.cjbc.kakeibo.model

val EXPENSE_CATEGORIES = listOf(
    "食費",
    "交通費",
    "住居費",
    "光熱費",
    "医療費",
    "娯楽費",
    "衣服費",
    "日用品",
    "教育費",
    "通信費",
    "その他",
)

val INCOME_CATEGORIES = listOf("給与", "副収入", "賞与", "その他")

const val DEFAULT_CATEGORY_COLOR = 0xFFB0B0B0

val CATEGORY_COLORS = mapOf(
    "食費" to 0xFFE85D4A,
    "交通費" to 0xFFE8914A,
    "住居費" to 0xFFD4A843,
    "光熱費" to 0xFF7BC67A,
    "医療費" to 0xFF5DB8E8,
    "娯楽費" to 0xFF9B6BE8,
    "衣服費" to 0xFFE86BAF,
    "日用品" to 0xFF6BB5E8,
    "教育費" to 0xFF4A9E8B,
    "通信費" to 0xFF8B8B8B,
    "その他" to DEFAULT_CATEGORY_COLOR,
    "給与" to 0xFF2D6A4F,
    "副収入" to 0xFF4A9E8B,
    "賞与" to 0xFFD4A843,
)

/** Returns the suggested categories for transactions of the given [type]. */
fun categoriesFor(type: TransactionType): List<String> =
    if (type == TransactionType.EXPENSE) EXPENSE_CATEGORIES else INCOME_CATEGORIES
