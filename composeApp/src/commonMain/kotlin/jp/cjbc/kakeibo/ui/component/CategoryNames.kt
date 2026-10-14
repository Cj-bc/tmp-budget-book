package jp.cjbc.kakeibo.ui.component

import androidx.compose.runtime.Composable
import jp.cjbc.kakeibo.model.BuiltInCategory
import jp.cjbc.kakeibo.model.CategoryId
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Converts between stored category IDs and the localized names shown on screens. */
internal class CategoryNames(private val labels: Map<BuiltInCategory, String>) {
    /** Returns the name shown for the category stored as [id]. */
    fun display(id: CategoryId): String = BuiltInCategory.fromId(id)?.let(labels::getValue) ?: id.value

    /**
     * Returns the ID to store for a category typed as [input]: a built-in category's ID when
     * [input] is its name in either language, or the trimmed input itself.
     */
    fun parse(input: String): CategoryId {
        val name = input.trim()
        val builtIn = labels.entries.find { it.value.equals(name, ignoreCase = true) }?.key
            ?: BuiltInCategory.fromId(CategoryId(name))
        return builtIn?.id ?: CategoryId(name)
    }
}

@Composable
internal fun categoryNames(): CategoryNames =
    CategoryNames(BuiltInCategory.entries.associateWith { stringResource(it.label) })

/** Names the fund made for the category [id] when migrating data: the category's own name. */
internal suspend fun migratedFundName(id: CategoryId): String =
    BuiltInCategory.fromId(id)?.let { getString(it.label) } ?: id.value
