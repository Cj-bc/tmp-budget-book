package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Settings
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.model.categoryColor
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.settings_categories
import jp.cjbc.kakeibo.resources.settings_categories_empty
import jp.cjbc.kakeibo.resources.settings_expense_categories
import jp.cjbc.kakeibo.resources.settings_income_categories
import jp.cjbc.kakeibo.resources.settings_new_category_label
import jp.cjbc.kakeibo.resources.settings_reset_categories
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import org.jetbrains.compose.resources.stringResource

/** Lists the app settings. Each setting group is a section of this screen. */
@Composable
internal fun SettingsScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    ScreenColumn {
        SectionHeader(stringResource(Res.string.settings_categories))
        TransactionType.entries.forEach { type ->
            CategoryListCard(ledger, type, onLedgerChange)
        }
    }
}

/** Edits the categories offered for transactions of the given [type]. */
@Composable
private fun CategoryListCard(ledger: Ledger, type: TransactionType, onLedgerChange: () -> Unit) {
    val categoryNames = categoryNames()
    val categories = ledger.settings.categoriesFor(type)
    var newCategory by remember { mutableStateOf("") }
    val updateCategories: (List<String>) -> Unit = {
        ledger.updateSettings(ledger.settings.withCategories(type, it))
        onLedgerChange()
    }

    SectionCard {
        Text(
            stringResource(
                if (type == TransactionType.EXPENSE) {
                    Res.string.settings_expense_categories
                } else {
                    Res.string.settings_income_categories
                },
            ),
            color = MUTED_COLOR,
            fontSize = 12.sp,
        )
        if (categories.isEmpty()) {
            EmptyMessage(stringResource(Res.string.settings_categories_empty))
        }
        categories.forEachIndexed { index, category ->
            CategoryRow(
                name = categoryNames.display(category),
                color = categoryColor(category),
                onMoveUp = if (index > 0) {
                    { updateCategories(categories.swapped(index, index - 1)) }
                } else {
                    null
                },
                onMoveDown = if (index < categories.lastIndex) {
                    { updateCategories(categories.swapped(index, index + 1)) }
                } else {
                    null
                },
                onDelete = { updateCategories(categories - category) },
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledTextField(
                newCategory,
                stringResource(Res.string.settings_new_category_label),
                { newCategory = it },
                Modifier.weight(1f),
            )
            ActionButton(stringResource(Res.string.add_confirm), {
                val category = categoryNames.parse(newCategory)
                if (category.isNotEmpty()) {
                    if (category !in categories) updateCategories(categories + category)
                    newCategory = ""
                }
            })
        }
        ActionButton(
            stringResource(Res.string.settings_reset_categories),
            { updateCategories(Settings().categoriesFor(type)) },
            isPrimary = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryRow(
    name: String,
    color: Long,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Color(color), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), fontSize = 13.sp)
        RowIcon("↑", onMoveUp)
        RowIcon("↓", onMoveDown)
        RowIcon("×", onDelete)
    }
}

/** A tappable symbol at the end of a row, dimmed when [onClick] is `null`. */
@Composable
private fun RowIcon(symbol: String, onClick: (() -> Unit)?) {
    Text(
        symbol,
        color = if (onClick == null) MUTED_COLOR.copy(alpha = 0.3f) else ACCENT_COLOR,
        fontSize = 18.sp,
        modifier = Modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 8.dp),
    )
}

private fun <T> List<T>.swapped(i: Int, j: Int): List<T> =
    toMutableList().also {
        it[i] = this[j]
        it[j] = this[i]
    }
