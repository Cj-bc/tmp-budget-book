package jp.cjbc.kakeibo.ui.component

import androidx.compose.runtime.Composable
import jp.cjbc.kakeibo.model.Category
import org.jetbrains.compose.resources.stringResource

/** Converts between stored categories and the localized names shown in category fields. */
internal class CategoryNames(private val labels: Map<Category, String>) {
    /** Returns the name shown for the stored [category]. */
    fun display(category: String): String =
        Category.fromId(category)?.let { labels.getValue(it) } ?: category

    fun display(category: Category): String = labels.getValue(category)

    /** Returns the stored form of a category typed as [input]. */
    fun parse(input: String): String {
        val name = input.trim()
        val category = labels.entries.find { it.value.equals(name, ignoreCase = true) }?.key
            ?: Category.fromId(name)
        return category?.id ?: name
    }
}

@Composable
internal fun categoryNames(): CategoryNames =
    CategoryNames(Category.entries.associateWith { stringResource(it.label) })
