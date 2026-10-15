package jp.cjbc.kakeibo.data.v1

import jp.cjbc.kakeibo.model.BuiltInCategory
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
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

/** The ID of the fund created for the expense category [categoryId]. */
fun migratedFundId(categoryId: CategoryId): FundId = FundId("fund-${categoryId.value}")

/**
 * Converts version 1 data.
 *
 * Every expense category gets a fund of the same name, which becomes its default fund and pays
 * for all its past expenses and recurring items. Budgets themselves, savings, incomes and
 * recurring incomes are dropped. [fundName] names the fund of a category.
 */
suspend fun migrateFromV1(
    backup: V1Backup,
    fundName: suspend (CategoryId) -> String,
): MigrationResult {
    val log = mutableListOf<String>()
    val expenses = backup.transactions.filter { it.type == V1TransactionType.EXPENSE }
    val recurringExpenses = backup.recurringItems.filter { it.type == V1TransactionType.EXPENSE }

    // Categories removed in the settings are kept, archived, while something still refers to them.
    val activeExpenseIds = backup.settings.expenseCategories.map(::CategoryId).distinct()
    val expenseIds = (
        activeExpenseIds +
            (backup.budgets.map { it.category } + expenses.map { it.category } +
                recurringExpenses.map { it.category }).map(::CategoryId)
        ).distinct()
    val usage = expenses.groupingBy { CategoryId(it.category) }.eachCount()
    val pinnedIds = activeExpenseIds
        .sortedByDescending { usage[it] ?: 0 }
        .take(PINNED_CATEGORY_COUNT)
        .toSet()

    val funds = expenseIds.mapIndexed { index, id ->
        Fund(
            id = migratedFundId(id),
            name = fundName(id),
            color = FundColor(BuiltInCategory.fromId(id)?.legacyColor ?: DEFAULT_FUND_COLOR),
            openingBalance = 0,
            goal = FundGoal.None,
            showInEntry = defaultShowInEntry(FundGoal.None),
            sortOrder = index,
            archived = false,
        )
    }
    val expenseCategories = expenseIds.mapIndexed { index, id ->
        Category.Expense(
            id = id,
            pinned = id in pinnedIds,
            sortOrder = index,
            archived = id !in activeExpenseIds,
            defaultFundId = migratedFundId(id),
        )
    }
    val incomeCategories = backup.settings.incomeCategories.map(::CategoryId).distinct()
        .mapIndexed { index, id ->
            Category.Income(id = id, pinned = false, sortOrder = index, archived = false)
        }

    val recurringItems = recurringExpenses.mapNotNull { item ->
        val categoryId = CategoryId(item.category)
        val amount = abs(item.amount).roundToLong()
        if (amount == 0L) {
            log += "Dropped recurring expense ${item.id} (${item.description}): its amount is 0"
            return@mapNotNull null
        }
        RecurringItem(
            id = RecurringItemId(item.id),
            name = item.description,
            amount = amount,
            categoryId = categoryId,
            fundId = migratedFundId(categoryId),
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
                val categoryId = CategoryId(transaction.category)
                Transaction.Expense(
                    id = TransactionId(transaction.id),
                    date = date,
                    amount = abs(transaction.amount).roundToLong(),
                    categoryId = categoryId,
                    memo = transaction.description,
                    fundId = migratedFundId(categoryId),
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
