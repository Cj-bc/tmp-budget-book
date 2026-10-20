package jp.cjbc.kakeibo.data.v1

import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.CategoryPreset
import jp.cjbc.kakeibo.model.DEFAULT_FUND_COLOR
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundColor
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.defaultShowInEntry
import jp.cjbc.kakeibo.model.withPaletteColors
import jp.cjbc.kakeibo.util.generateUlid
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** The number of expense categories pinned to the entry screen's category grid. */
const val PINNED_CATEGORY_COUNT = 7

/** Suffixes version 1 appended to the memo of a transaction recorded by a recurring item. */
private val AUTO_MEMO_SUFFIXES = listOf("", "（自動）", " (auto)")

/** Migrated data and what was dropped on the way, for the log. */
class MigrationResult(val data: LedgerData, val log: List<String>)

/** The ID of the fund created for the expense category that version 1 stored as [category]. */
fun migratedFundId(category: String): FundId = FundId("fund-$category")

/**
 * Converts version 1 data.
 *
 * Every expense category gets a fund of the same name, which becomes its default fund and pays
 * for all its past expenses and recurring items. Budgets themselves, savings, incomes and
 * recurring incomes are dropped. Categories get new IDs. [presetName] names the categories, and
 * their funds, that version 1 stored as a preset's ID; the others are named as they were stored.
 */
suspend fun migrateFromV1(
    backup: V1Backup,
    presetName: suspend (CategoryPreset) -> String,
): MigrationResult {
    val log = mutableListOf<String>()
    val expenses = backup.transactions.filter { it.type == V1TransactionType.EXPENSE }
    val recurringExpenses = backup.recurringItems.filter { it.type == V1TransactionType.EXPENSE }

    // Categories removed in the settings are kept, archived, while something still refers to them.
    // Version 1 identified categories by these strings; the same one may be both kinds.
    val activeExpenses = backup.settings.expenseCategories.distinct()
    val expenseNames = (
        activeExpenses + backup.budgets.map { it.category } + expenses.map { it.category } +
            recurringExpenses.map { it.category }
        ).distinct()
    val usage = expenses.groupingBy { it.category }.eachCount()
    val pinned = activeExpenses
        .sortedByDescending { usage[it] ?: 0 }
        .take(PINNED_CATEGORY_COUNT)
        .toSet()

    val incomeNames = backup.settings.incomeCategories.distinct()
    val names = (expenseNames + incomeNames).distinct().associateWith { category ->
        CategoryPreset.fromLegacyId(category)?.let { presetName(it) } ?: category
    }
    val expenseIds = expenseNames.associateWith { CategoryId(generateUlid()) }

    val funds = expenseNames.mapIndexed { index, category ->
        Fund(
            id = migratedFundId(category),
            name = names.getValue(category),
            color = FundColor(CategoryPreset.fromLegacyId(category)?.legacyColor ?: DEFAULT_FUND_COLOR),
            openingBalance = 0,
            goal = FundGoal.None,
            showInEntry = defaultShowInEntry(FundGoal.None),
            sortOrder = index,
            archived = false,
        )
    }
    val expenseCategories = expenseNames.mapIndexed { index, category ->
        Category.Expense(
            id = expenseIds.getValue(category),
            name = names.getValue(category),
            pinned = category in pinned,
            sortOrder = index,
            archived = category !in activeExpenses,
            defaultFundId = migratedFundId(category),
        )
    }
    val incomeCategories = incomeNames.mapIndexed { index, category ->
        Category.Income(
            id = CategoryId(generateUlid()),
            name = names.getValue(category),
            pinned = false,
            sortOrder = index,
            archived = false,
        )
    }

    val recurringItems = recurringExpenses.mapNotNull { item ->
        val amount = abs(item.amount).roundToLong()
        if (amount == 0L) {
            log += "Dropped recurring expense ${item.id} (${item.description}): its amount is 0"
            return@mapNotNull null
        }
        RecurringItem(
            id = RecurringItemId(item.id),
            name = item.description,
            amount = amount,
            categoryId = expenseIds.getValue(item.category),
            fundId = migratedFundId(item.category),
            dayOfMonth = item.dayOfMonth.coerceIn(MIN_RECURRING_DAY, MAX_RECURRING_DAY),
            enabled = item.active,
            lastAppliedMonth = item.lastApplied?.let(YearMonth.Formats.ISO::parseOrNull),
        )
    }
    backup.recurringItems.filter { it.type == V1TransactionType.INCOME }.forEach {
        log += "Dropped recurring income ${it.id} (${it.description}): recurring incomes are gone"
    }

    val transactions = backup.transactions.mapNotNull { transaction ->
        val date = LocalDate.Formats.ISO.parseOrNull(transaction.date)
        when {
            transaction.type == V1TransactionType.INCOME -> {
                log += "Dropped income ${transaction.id} (${transaction.date}): " +
                    "incomes are not migrated"
                null
            }
            date == null -> {
                log += "Dropped expense ${transaction.id}: invalid date ${transaction.date}"
                null
            }
            abs(transaction.amount).roundToLong() == 0L -> {
                log += "Dropped expense ${transaction.id} (${transaction.date}): its amount is 0"
                null
            }
            else -> {
                Transaction.Expense(
                    id = TransactionId(transaction.id),
                    date = date,
                    amount = abs(transaction.amount).roundToLong(),
                    categoryId = expenseIds.getValue(transaction.category),
                    memo = transaction.description,
                    fundId = migratedFundId(transaction.category),
                    recurringItemId = if (transaction.isRecurring) {
                        recurringSourceOf(transaction, recurringExpenses)
                            ?: null.also {
                                log += "Expense ${transaction.id} was recorded by a recurring " +
                                    "item that no longer exists"
                            }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (backup.savings.isNotEmpty()) {
        log += "Dropped ${backup.savings.size} saving goal(s): savings are replaced by funds"
    }

    return MigrationResult(
        // Funds start with their category's old color, moved onto the palette.
        LedgerData(
            funds = funds,
            categories = expenseCategories + incomeCategories,
            transactions = transactions,
            allocationRules = emptyList(),
            recurringItems = recurringItems,
        ).withPaletteColors(),
        log,
    )
}

/** Finds the recurring item that recorded [transaction], from its category and memo. */
private fun recurringSourceOf(
    transaction: V1Transaction,
    items: List<V1RecurringItem>,
): RecurringItemId? = items
    .firstOrNull { item ->
        item.category == transaction.category &&
            AUTO_MEMO_SUFFIXES.any { transaction.description == item.description + it }
    }
    ?.let { RecurringItemId(it.id) }
