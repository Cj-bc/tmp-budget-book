package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LocalDate
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.activeExpenseCategories
import jp.cjbc.kakeibo.model.activeFunds
import jp.cjbc.kakeibo.model.activeIncomeCategories
import jp.cjbc.kakeibo.model.defaultBudgetMonth
import jp.cjbc.kakeibo.model.expenseCategory
import jp.cjbc.kakeibo.model.fund
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.amount
import jp.cjbc.kakeibo.resources.date_label
import jp.cjbc.kakeibo.resources.deleted_message
import jp.cjbc.kakeibo.resources.entry_amount_error
import jp.cjbc.kakeibo.resources.entry_auto_badge
import jp.cjbc.kakeibo.resources.entry_budget_month
import jp.cjbc.kakeibo.resources.entry_category
import jp.cjbc.kakeibo.resources.entry_date_error
import jp.cjbc.kakeibo.resources.entry_delete
import jp.cjbc.kakeibo.resources.entry_empty
import jp.cjbc.kakeibo.resources.entry_fund_default
import jp.cjbc.kakeibo.resources.entry_fund_error
import jp.cjbc.kakeibo.resources.entry_fund_expense
import jp.cjbc.kakeibo.resources.entry_fund_income
import jp.cjbc.kakeibo.resources.entry_memo
import jp.cjbc.kakeibo.resources.entry_paid_from
import jp.cjbc.kakeibo.resources.entry_recent
import jp.cjbc.kakeibo.resources.entry_record
import jp.cjbc.kakeibo.resources.entry_title
import jp.cjbc.kakeibo.resources.expense
import jp.cjbc.kakeibo.resources.income
import jp.cjbc.kakeibo.resources.undo
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.ChoiceChips
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.FieldError
import jp.cjbc.kakeibo.ui.component.FundLabel
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.util.formatAmount
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.generateId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val RECENT_TRANSACTION_COUNT = 20

private enum class EntryKind(val label: StringResource) {
    EXPENSE(Res.string.expense),
    INCOME(Res.string.income),
}

/**
 * The interim entry screen until the keypad design lands: records an expense paid from a fund, or
 * an income that goes wholly to one fund, and lists recent records.
 */
@Composable
internal fun EntryScreen(ledger: Ledger, snackbarHostState: SnackbarHostState) {
    val data = ledger.data
    val categoryNames = categoryNames()
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(EntryKind.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.today().toString()) }
    var memo by remember { mutableStateOf("") }
    var categoryId by remember(kind) { mutableStateOf<CategoryId?>(null) }
    var fundId by remember(kind) { mutableStateOf<FundId?>(null) }
    var showErrors by remember { mutableStateOf(false) }

    val categories = when (kind) {
        EntryKind.EXPENSE -> data.activeExpenseCategories.sortedByDescending { it.pinned }
        EntryKind.INCOME -> data.activeIncomeCategories
    }
    val selectedCategoryId = categoryId ?: categories.firstOrNull()?.id
    val defaultFundId = selectedCategoryId?.let { data.expenseCategory(it) }?.defaultFundId
        ?.takeIf { kind == EntryKind.EXPENSE }
    val selectedFundId = fundId ?: defaultFundId
    val parsedAmount = amount.toLongOrNull()?.takeIf { it > 0 }
    val parsedDate = LocalDate.parseOrNull(date.trim())

    ScreenColumn {
        SectionHeader(stringResource(Res.string.entry_title))
        SectionCard {
            ChoiceChips(EntryKind.entries, kind, { kind = it }) { Text(stringResource(it.label)) }
            LabeledTextField(
                amount,
                stringResource(Res.string.amount),
                { amount = it.filter(Char::isDigit) },
                Modifier.fillMaxWidth(),
            )
            if (showErrors && parsedAmount == null) FieldError(stringResource(Res.string.entry_amount_error))
            LabeledTextField(date, stringResource(Res.string.date_label), { date = it }, Modifier.fillMaxWidth())
            if (showErrors && parsedDate == null) FieldError(stringResource(Res.string.entry_date_error))
            if (kind == EntryKind.INCOME && parsedDate != null) {
                Text(
                    stringResource(Res.string.entry_budget_month, formatYearMonth(defaultBudgetMonth(parsedDate))),
                    color = MUTED_COLOR,
                    fontSize = 12.sp,
                )
            }
            Text(stringResource(Res.string.entry_category), color = MUTED_COLOR, fontSize = 12.sp)
            ChoiceChips(categories.map { it.id }, selectedCategoryId, {
                categoryId = it
                fundId = null
            }) { Text(categoryNames.display(it)) }
            Text(
                stringResource(
                    if (kind == EntryKind.EXPENSE) Res.string.entry_fund_expense else Res.string.entry_fund_income,
                ),
                color = MUTED_COLOR,
                fontSize = 12.sp,
            )
            val defaultBadge = stringResource(Res.string.entry_fund_default)
            ChoiceChips(data.activeFunds, data.activeFunds.find { it.id == selectedFundId }, { fundId = it.id }) {
                FundLabel(it, if (it.id == defaultFundId) defaultBadge else null)
            }
            if (showErrors && selectedFundId == null) FieldError(stringResource(Res.string.entry_fund_error))
            LabeledTextField(memo, stringResource(Res.string.entry_memo), { memo = it }, Modifier.fillMaxWidth())
            ActionButton(
                label = stringResource(Res.string.entry_record),
                onClick = {
                    if (parsedAmount == null || parsedDate == null || selectedCategoryId == null ||
                        selectedFundId == null
                    ) {
                        showErrors = true
                        return@ActionButton
                    }
                    val id = TransactionId(generateId())
                    ledger.addTransaction(
                        when (kind) {
                            EntryKind.EXPENSE -> Transaction.Expense(
                                id = id,
                                date = parsedDate,
                                amount = parsedAmount,
                                categoryId = selectedCategoryId,
                                memo = memo.trim(),
                                fundId = selectedFundId,
                                recurringItemId = null,
                            )
                            EntryKind.INCOME -> Transaction.Income(
                                id = id,
                                date = parsedDate,
                                amount = parsedAmount,
                                categoryId = selectedCategoryId,
                                memo = memo.trim(),
                                budgetMonth = defaultBudgetMonth(parsedDate),
                                allocations = listOf(Allocation(selectedFundId, parsedAmount)),
                            )
                        },
                    )
                    amount = ""
                    memo = ""
                    showErrors = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionHeader(stringResource(Res.string.entry_recent))
        SectionCard {
            if (data.transactions.isEmpty()) {
                EmptyMessage(stringResource(Res.string.entry_empty))
            } else {
                data.transactions
                    .sortedByDescending { it.date }
                    .take(RECENT_TRANSACTION_COUNT)
                    .forEach { transaction ->
                        TransactionRow(ledger, transaction) {
                            val removed = ledger.deleteTransaction(transaction.id) ?: return@TransactionRow
                            scope.launch {
                                val label = transaction.memo.ifBlank { categoryNames.display(transaction.categoryId) }
                                val result = snackbarHostState.showSnackbar(
                                    getString(
                                        Res.string.deleted_message,
                                        "$label ${formatAmount(signedAmount(transaction), signed = true)}",
                                    ),
                                    actionLabel = getString(Res.string.undo),
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) ledger.addTransaction(removed)
                            }
                        }
                    }
            }
        }
    }
}

/** The amount of [transaction] as it changes the user's money: negative for expenses. */
private fun signedAmount(transaction: Transaction): Long = when (transaction) {
    is Transaction.Expense -> -transaction.amount
    is Transaction.Income -> transaction.amount
}

@Composable
private fun TransactionRow(ledger: Ledger, transaction: Transaction, onDelete: () -> Unit) {
    val category = categoryNames().display(transaction.categoryId)
    val fundIds = when (transaction) {
        is Transaction.Expense -> listOf(transaction.fundId)
        is Transaction.Income -> transaction.allocations.map { it.fundId }
    }
    val funds = fundIds.mapNotNull { ledger.data.fund(it) }
    val details = buildList {
        add(transaction.date.toString())
        add(category)
        if (transaction is Transaction.Expense) {
            funds.firstOrNull()?.let { add(stringResource(Res.string.entry_paid_from, it.name)) }
            if (transaction.recurringItemId != null) add(stringResource(Res.string.entry_auto_badge))
        } else {
            add(funds.joinToString("・") { it.name })
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).background(
                funds.firstOrNull()?.let { Color(it.color.argb) } ?: MUTED_COLOR,
                RoundedCornerShape(2.dp),
            ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.memo.ifBlank { category },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(details.joinToString(" · "), color = MUTED_COLOR, fontSize = 11.sp)
        }
        AmountText(signedAmount(transaction), signed = true)
        Text(
            stringResource(Res.string.entry_delete),
            color = MUTED_COLOR,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}
