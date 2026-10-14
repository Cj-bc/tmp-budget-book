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

/** The color a fund migrated from a user-defined category gets. */
const val DEFAULT_FUND_COLOR = 0xFFB0B0B0

/**
 * A built-in category. Its [id] is stored, and the [label] is shown in the user's language.
 *
 * Categories no longer have colors; [legacyColor] only colors the funds created for them when
 * migrating data from version 1.
 */
enum class BuiltInCategory(
    val id: CategoryId,
    val label: StringResource,
    val legacyColor: Long,
) {
    FOOD(CategoryId("food"), Res.string.category_food, 0xFFE85D4A),
    TRANSPORT(CategoryId("transport"), Res.string.category_transport, 0xFFE8914A),
    HOUSING(CategoryId("housing"), Res.string.category_housing, 0xFFD4A843),
    UTILITIES(CategoryId("utilities"), Res.string.category_utilities, 0xFF7BC67A),
    MEDICAL(CategoryId("medical"), Res.string.category_medical, 0xFF5DB8E8),
    ENTERTAINMENT(CategoryId("entertainment"), Res.string.category_entertainment, 0xFF9B6BE8),
    CLOTHING(CategoryId("clothing"), Res.string.category_clothing, 0xFFE86BAF),
    DAILY_GOODS(CategoryId("daily_goods"), Res.string.category_daily_goods, 0xFF6BB5E8),
    EDUCATION(CategoryId("education"), Res.string.category_education, 0xFF4A9E8B),
    COMMUNICATION(CategoryId("communication"), Res.string.category_communication, 0xFF8B8B8B),
    OTHER(CategoryId("other"), Res.string.category_other, DEFAULT_FUND_COLOR),
    SALARY(CategoryId("salary"), Res.string.category_salary, 0xFF2D6A4F),
    SIDE_INCOME(CategoryId("side_income"), Res.string.category_side_income, 0xFF4A9E8B),
    BONUS(CategoryId("bonus"), Res.string.category_bonus, 0xFFD4A843),
    ;

    companion object {
        /** Returns the built-in category stored as [id], or `null` for a user-defined one. */
        fun fromId(id: CategoryId): BuiltInCategory? = entries.find { it.id == id }
    }
}

val BUILT_IN_EXPENSE_CATEGORIES = listOf(
    BuiltInCategory.FOOD,
    BuiltInCategory.TRANSPORT,
    BuiltInCategory.HOUSING,
    BuiltInCategory.UTILITIES,
    BuiltInCategory.MEDICAL,
    BuiltInCategory.ENTERTAINMENT,
    BuiltInCategory.CLOTHING,
    BuiltInCategory.DAILY_GOODS,
    BuiltInCategory.EDUCATION,
    BuiltInCategory.COMMUNICATION,
    BuiltInCategory.OTHER,
)

val BUILT_IN_INCOME_CATEGORIES = listOf(
    BuiltInCategory.SALARY,
    BuiltInCategory.SIDE_INCOME,
    BuiltInCategory.BONUS,
    BuiltInCategory.OTHER,
)
