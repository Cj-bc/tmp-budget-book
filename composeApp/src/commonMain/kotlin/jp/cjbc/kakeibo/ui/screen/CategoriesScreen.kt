package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.categoryNamed
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.settings_category_exists
import jp.cjbc.kakeibo.resources.settings_category_fund
import jp.cjbc.kakeibo.resources.settings_category_name_error
import jp.cjbc.kakeibo.resources.settings_expense_categories
import jp.cjbc.kakeibo.resources.settings_income_categories
import jp.cjbc.kakeibo.resources.settings_new_category_label
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.Route
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundChip
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.theme.AppSpacing
import jp.cjbc.kakeibo.ui.theme.AppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The expense or income categories, opened from the settings: add one, and pin, reorder, change
 * the default fund of and archive the existing ones, as in the category sheet's edit mode.
 */
@Composable
internal fun CategoriesScreen(ledger: Ledger, navigator: AppNavigator, route: Route.Categories) {
    val title = if (route.isExpense) Res.string.settings_expense_categories else Res.string.settings_income_categories
    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(title), navigator::back, stringResource(Res.string.back))
        Column(
            Modifier.weight(1f).padding(horizontal = AppSpacing.Screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NewCategoryCard(ledger, route.isExpense)
            SectionCard(Modifier.weight(1f, fill = false), padding = 0.dp) {
                CategoryEditList(ledger, route.isExpense, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun NewCategoryCard(ledger: Ledger, isExpense: Boolean) {
    val data = ledger.data
    var name by remember { mutableStateOf("") }
    var fundId by remember { mutableStateOf<FundId?>(null) }
    var nameError by remember { mutableStateOf<StringResource?>(null) }
    var fundError by remember { mutableStateOf(false) }

    SectionCard {
        LabeledTextField(
            name,
            stringResource(Res.string.settings_new_category_label),
            {
                name = it
                nameError = null
            },
            Modifier.fillMaxWidth(),
            error = nameError?.let { stringResource(it) },
        )
        if (isExpense && name.isNotBlank()) {
            Text(
                stringResource(Res.string.settings_category_fund),
                style = AppTheme.type.sectionHeader,
                color = AppTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.activeFunds.forEach { fund ->
                    FundChip(fund, fund.id == fundId, {
                        fundId = fund.id
                        fundError = false
                    })
                }
            }
            if (fundError) FieldError(stringResource(Res.string.entry_fund_error))
        }
        if (name.isNotBlank()) {
            ActionButton(
                stringResource(Res.string.add_confirm),
                {
                    val existing = data.categoryNamed(name, isExpense)
                    nameError = when {
                        name.isBlank() -> Res.string.settings_category_name_error
                        existing != null && !existing.archived -> Res.string.settings_category_exists
                        else -> null
                    }
                    fundError = isExpense && fundId == null && existing == null
                    if (nameError != null || fundError) return@ActionButton
                    createCategory(ledger, isExpense, name.trim(), fundId)
                    name = ""
                    fundId = null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}
