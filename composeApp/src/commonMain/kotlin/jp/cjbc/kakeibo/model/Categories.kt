package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.category_bonus
import jp.cjbc.kakeibo.resources.category_clothing
import jp.cjbc.kakeibo.resources.category_communication
import jp.cjbc.kakeibo.resources.category_daily_goods
import jp.cjbc.kakeibo.resources.category_education
import jp.cjbc.kakeibo.resources.category_entertainment
import jp.cjbc.kakeibo.resources.category_food
import jp.cjbc.kakeibo.resources.category_housing
import jp.cjbc.kakeibo.resources.category_medical
import jp.cjbc.kakeibo.resources.category_other
import jp.cjbc.kakeibo.resources.category_salary
import jp.cjbc.kakeibo.resources.category_side_income
import jp.cjbc.kakeibo.resources.category_transport
import jp.cjbc.kakeibo.resources.category_utilities
import org.jetbrains.compose.resources.StringResource

const val DEFAULT_CATEGORY_COLOR = 0xFFB0B0B0

/**
 * A built-in category. Transactions, budgets and recurring items store its [id]; any other
 * category string is a user-defined category shown as is.
 *
 * [legacyName] is the Japanese name that older versions stored instead of [id].
 */
enum class Category(
    val id: String,
    val label: StringResource,
    val color: Long,
    val legacyName: String,
) {
    FOOD("food", Res.string.category_food, 0xFFE85D4A, "食費"),
    TRANSPORT("transport", Res.string.category_transport, 0xFFE8914A, "交通費"),
    HOUSING("housing", Res.string.category_housing, 0xFFD4A843, "住居費"),
    UTILITIES("utilities", Res.string.category_utilities, 0xFF7BC67A, "光熱費"),
    MEDICAL("medical", Res.string.category_medical, 0xFF5DB8E8, "医療費"),
    ENTERTAINMENT("entertainment", Res.string.category_entertainment, 0xFF9B6BE8, "娯楽費"),
    CLOTHING("clothing", Res.string.category_clothing, 0xFFE86BAF, "衣服費"),
    DAILY_GOODS("daily_goods", Res.string.category_daily_goods, 0xFF6BB5E8, "日用品"),
    EDUCATION("education", Res.string.category_education, 0xFF4A9E8B, "教育費"),
    COMMUNICATION("communication", Res.string.category_communication, 0xFF8B8B8B, "通信費"),
    OTHER("other", Res.string.category_other, DEFAULT_CATEGORY_COLOR, "その他"),
    SALARY("salary", Res.string.category_salary, 0xFF2D6A4F, "給与"),
    SIDE_INCOME("side_income", Res.string.category_side_income, 0xFF4A9E8B, "副収入"),
    BONUS("bonus", Res.string.category_bonus, 0xFFD4A843, "賞与"),
    ;

    companion object {
        /** Returns the built-in category stored as [id], or `null` for a user-defined one. */
        fun fromId(id: String): Category? = entries.find { it.id == id }

        /** Converts a category stored by an older version into its current stored form. */
        fun migrate(stored: String): String = entries.find { it.legacyName == stored }?.id ?: stored
    }
}

val EXPENSE_CATEGORIES = listOf(
    Category.FOOD,
    Category.TRANSPORT,
    Category.HOUSING,
    Category.UTILITIES,
    Category.MEDICAL,
    Category.ENTERTAINMENT,
    Category.CLOTHING,
    Category.DAILY_GOODS,
    Category.EDUCATION,
    Category.COMMUNICATION,
    Category.OTHER,
)

val INCOME_CATEGORIES = listOf(
    Category.SALARY,
    Category.SIDE_INCOME,
    Category.BONUS,
    Category.OTHER,
)

/** Returns the color of the stored [category]. */
fun categoryColor(category: String): Long =
    Category.fromId(category)?.color ?: DEFAULT_CATEGORY_COLOR

/** Returns the suggested categories for transactions of the given [type]. */
fun categoriesFor(type: TransactionType): List<Category> =
    if (type == TransactionType.EXPENSE) EXPENSE_CATEGORIES else INCOME_CATEGORIES
