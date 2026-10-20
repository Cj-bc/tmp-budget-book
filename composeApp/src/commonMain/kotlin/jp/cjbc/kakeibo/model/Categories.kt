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

/** The color a fund migrated from a category the user added gets. */
const val DEFAULT_FUND_COLOR = 0xFFB0B0B0

/**
 * A category a new ledger starts with, for convenience. It is created with the [label] in the
 * user's language as its name, and from then on is an ordinary category: nothing treats it
 * specially.
 *
 * Older versions stored a preset by its [legacyId] instead of a name, which is how migrations
 * recognize presets. Categories no longer have colors; [legacyColor] only colors the funds created
 * for them when migrating data from version 1.
 */
enum class CategoryPreset(
    val legacyId: String,
    val label: StringResource,
    val legacyColor: Long,
) {
    FOOD("food", Res.string.category_food, 0xFFE85D4A),
    TRANSPORT("transport", Res.string.category_transport, 0xFFE8914A),
    HOUSING("housing", Res.string.category_housing, 0xFFD4A843),
    UTILITIES("utilities", Res.string.category_utilities, 0xFF7BC67A),
    MEDICAL("medical", Res.string.category_medical, 0xFF5DB8E8),
    ENTERTAINMENT("entertainment", Res.string.category_entertainment, 0xFF9B6BE8),
    CLOTHING("clothing", Res.string.category_clothing, 0xFFE86BAF),
    DAILY_GOODS("daily_goods", Res.string.category_daily_goods, 0xFF6BB5E8),
    EDUCATION("education", Res.string.category_education, 0xFF4A9E8B),
    COMMUNICATION("communication", Res.string.category_communication, 0xFF8B8B8B),
    OTHER("other", Res.string.category_other, DEFAULT_FUND_COLOR),
    SALARY("salary", Res.string.category_salary, 0xFF2D6A4F),
    SIDE_INCOME("side_income", Res.string.category_side_income, 0xFF4A9E8B),
    BONUS("bonus", Res.string.category_bonus, 0xFFD4A843),
    ;

    companion object {
        /** Returns the preset older versions stored as [id], or `null` for a category the user added. */
        fun fromLegacyId(id: String): CategoryPreset? = entries.find { it.legacyId == id }
    }
}

val PRESET_EXPENSE_CATEGORIES = listOf(
    CategoryPreset.FOOD,
    CategoryPreset.TRANSPORT,
    CategoryPreset.HOUSING,
    CategoryPreset.UTILITIES,
    CategoryPreset.MEDICAL,
    CategoryPreset.ENTERTAINMENT,
    CategoryPreset.CLOTHING,
    CategoryPreset.DAILY_GOODS,
    CategoryPreset.EDUCATION,
    CategoryPreset.COMMUNICATION,
    CategoryPreset.OTHER,
)

val PRESET_INCOME_CATEGORIES = listOf(
    CategoryPreset.SALARY,
    CategoryPreset.SIDE_INCOME,
    CategoryPreset.BONUS,
    CategoryPreset.OTHER,
)
