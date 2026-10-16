package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.ImportResult
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.defaultShowInEntry
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.model.nextPaletteColor
import jp.cjbc.kakeibo.model.nextSortOrder
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.add_confirm
import jp.cjbc.kakeibo.resources.back
import jp.cjbc.kakeibo.resources.backup_description
import jp.cjbc.kakeibo.resources.backup_export
import jp.cjbc.kakeibo.resources.backup_exported
import jp.cjbc.kakeibo.resources.backup_import
import jp.cjbc.kakeibo.resources.backup_import_failure
import jp.cjbc.kakeibo.resources.backup_import_success
import jp.cjbc.kakeibo.resources.backup_label
import jp.cjbc.kakeibo.resources.backup_title
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.settings_archive
import jp.cjbc.kakeibo.resources.settings_archived
import jp.cjbc.kakeibo.resources.settings_categories
import jp.cjbc.kakeibo.resources.settings_category_exists
import jp.cjbc.kakeibo.resources.settings_category_fund
import jp.cjbc.kakeibo.resources.settings_category_name_error
import jp.cjbc.kakeibo.resources.settings_data
import jp.cjbc.kakeibo.resources.settings_expense_categories
import jp.cjbc.kakeibo.resources.settings_income_categories
import jp.cjbc.kakeibo.resources.settings_new_category_label
import jp.cjbc.kakeibo.resources.settings_new_fund
import jp.cjbc.kakeibo.resources.settings_pinned
import jp.cjbc.kakeibo.resources.settings_restore
import jp.cjbc.kakeibo.resources.settings_title
import jp.cjbc.kakeibo.ui.AppNavigator
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AppTopBar
import jp.cjbc.kakeibo.ui.component.ChoiceChips
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundLabel
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.component.migratedFundName
import jp.cjbc.kakeibo.ui.theme.AppTheme
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Lists the app settings. Each setting group is a section of this screen. */
@Composable
internal fun SettingsScreen(ledger: Ledger, navigator: AppNavigator) {
    Column {
        AppTopBar(stringResource(Res.string.settings_title), navigator::back, stringResource(Res.string.back))
        ScreenColumn {
            SectionHeader(stringResource(Res.string.settings_categories))
            ExpenseCategoriesCard(ledger)
            IncomeCategoriesCard(ledger)
            SectionHeader(stringResource(Res.string.settings_data))
            BackupCard(ledger)
        }
    }
}

/** Replaces the category [old] with [new] in [data]. */
private fun LedgerData.replaceCategory(old: Category, new: Category): LedgerData =
    copy(categories = categories.map { if (it == old) new else it })

private fun Category.withArchived(archived: Boolean): Category = when (this) {
    is Category.Expense -> copy(archived = archived)
    is Category.Income -> copy(archived = archived)
}

/** The fund a new expense category is paid from: an existing one, or a new one named after it. */
private sealed interface FundChoice {
    data class Existing(val fund: Fund) : FundChoice

    data object New : FundChoice
}

@Composable
private fun ExpenseCategoriesCard(ledger: Ledger) {
    val data = ledger.data
    val categoryNames = categoryNames()
    val categories = data.categories.filterIsInstance<Category.Expense>().sortedBy { it.sortOrder }
    var editing by remember { mutableStateOf<Category.Expense?>(null) }

    SectionCard {
        Text(stringResource(Res.string.settings_expense_categories), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        categories.filterNot { it.archived }.forEach { category ->
            CategoryRow(
                name = categoryNames.display(category.id),
                detail = data.fund(category.defaultFundId)?.let {
                    stringResource(Res.string.entry_paid_from, it.name)
                },
                onClick = { editing = if (editing?.id == category.id) null else category },
            )
            if (editing?.id == category.id) {
                Column(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                    Text(stringResource(Res.string.settings_category_fund), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
                    ChoiceChips(data.activeFunds, data.fund(category.defaultFundId), { fund ->
                        ledger.update { it.replaceCategory(category, category.copy(defaultFundId = fund.id)) }
                        editing = category.copy(defaultFundId = fund.id)
                    }) { FundLabel(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.settings_pinned), Modifier.weight(1f), fontSize = 13.sp)
                        Switch(category.pinned, { pinned ->
                            ledger.update { it.replaceCategory(category, category.copy(pinned = pinned)) }
                            editing = category.copy(pinned = pinned)
                        })
                    }
                    ActionButton(stringResource(Res.string.settings_archive), {
                        ledger.update { it.replaceCategory(category, category.copy(archived = true)) }
                        editing = null
                    }, isPrimary = false)
                }
            }
        }
        ArchivedCategories(ledger, categories)
        NewCategoryForm(ledger, isExpense = true)
    }
}

@Composable
private fun IncomeCategoriesCard(ledger: Ledger) {
    val categoryNames = categoryNames()
    val categories = ledger.data.categories.filterIsInstance<Category.Income>().sortedBy { it.sortOrder }

    SectionCard {
        Text(stringResource(Res.string.settings_income_categories), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        categories.filterNot { it.archived }.forEach { category ->
            CategoryRow(
                name = categoryNames.display(category.id),
                action = Res.string.settings_archive,
                onAction = { ledger.update { it.replaceCategory(category, category.copy(archived = true)) } },
            )
        }
        ArchivedCategories(ledger, categories)
        NewCategoryForm(ledger, isExpense = false)
    }
}

@Composable
private fun ArchivedCategories(ledger: Ledger, categories: List<Category>) {
    val archived = categories.filter { it.archived }
    if (archived.isEmpty()) return
    val categoryNames = categoryNames()
    Text(stringResource(Res.string.settings_archived), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
    archived.forEach { category ->
        CategoryRow(
            name = categoryNames.display(category.id),
            action = Res.string.settings_restore,
            onAction = { ledger.update { it.replaceCategory(category, category.withArchived(false)) } },
        )
    }
}

@Composable
private fun NewCategoryForm(ledger: Ledger, isExpense: Boolean) {
    val data = ledger.data
    val categoryNames = categoryNames()
    var name by remember { mutableStateOf("") }
    var fundChoice by remember { mutableStateOf<FundChoice?>(null) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val fundChoices = data.activeFunds.map(FundChoice::Existing) + FundChoice.New

    Spacer(Modifier.height(8.dp))
    LabeledTextField(
        name,
        stringResource(Res.string.settings_new_category_label),
        { name = it },
        Modifier.fillMaxWidth(),
    )
    if (isExpense) {
        Text(stringResource(Res.string.settings_category_fund), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        ChoiceChips(fundChoices, fundChoice, { fundChoice = it }) { choice ->
            when (choice) {
                is FundChoice.Existing -> FundLabel(choice.fund)
                FundChoice.New -> Text(stringResource(Res.string.settings_new_fund, name.trim()))
            }
        }
    }
    error?.let { FieldError(stringResource(it)) }
    ActionButton(stringResource(Res.string.add_confirm), {
        val id = categoryNames.parse(name)
        val sameKind = data.categories.filter { (it is Category.Expense) == isExpense }
        error = when {
            id.value.isEmpty() -> Res.string.settings_category_name_error
            sameKind.any { it.id == id } -> Res.string.settings_category_exists
            isExpense && fundChoice == null -> Res.string.entry_fund_error
            else -> null
        }
        if (error != null) return@ActionButton
        val sortOrder = nextSortOrder(sameKind.map { it.sortOrder })
        ledger.update { current ->
            if (!isExpense) {
                current.copy(categories = current.categories + Category.Income(id, false, sortOrder, false))
            } else {
                val newFund = (fundChoice as? FundChoice.New)?.let {
                    Fund(
                        id = FundId(generateId()),
                        name = categoryNames.display(id),
                        color = nextPaletteColor(current.funds),
                        openingBalance = 0,
                        goal = FundGoal.None,
                        showInEntry = defaultShowInEntry(FundGoal.None),
                        sortOrder = nextSortOrder(current.funds.map { it.sortOrder }),
                        archived = false,
                    )
                }
                val fundId = newFund?.id ?: (fundChoice as FundChoice.Existing).fund.id
                current.copy(
                    funds = current.funds + listOfNotNull(newFund),
                    categories = current.categories + Category.Expense(id, false, sortOrder, false, fundId),
                )
            }
        }
        name = ""
        fundChoice = null
    })
}

@Composable
private fun CategoryRow(
    name: String,
    detail: String? = null,
    action: StringResource? = null,
    onAction: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp)
            detail?.let { Text(it, color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp) }
        }
        action?.let {
            Text(
                stringResource(it),
                color = AppTheme.colors.onSurfaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** The interim backup through a text field, until backups are saved to files. */
@Composable
private fun BackupCard(ledger: Ledger) {
    val scope = rememberCoroutineScope()
    var backupText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<StringResource?>(null) }

    SectionCard {
        Text(stringResource(Res.string.backup_title), fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.backup_description), color = AppTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(Res.string.backup_export), {
                backupText = ledger.exportBackup()
                message = Res.string.backup_exported
            })
            ActionButton(
                stringResource(Res.string.backup_import),
                {
                    scope.launch {
                        val result = ledger.importBackup(backupText, ::migratedFundName)
                        if (result is ImportResult.Success) result.log.forEach { println("Import: $it") }
                        message = when (result) {
                            is ImportResult.Success -> Res.string.backup_import_success
                            ImportResult.Failure -> Res.string.backup_import_failure
                        }
                    }
                },
                isPrimary = false,
            )
        }
        Spacer(Modifier.width(8.dp))
        LabeledTextField(
            backupText,
            stringResource(Res.string.backup_label),
            { backupText = it },
            Modifier.fillMaxWidth().height(160.dp),
        )
        message?.let {
            Text(
                stringResource(it),
                color = if (it == Res.string.backup_import_failure) AppTheme.colors.negative else AppTheme.colors.positive,
                fontSize = 12.sp,
            )
        }
    }
}
