package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.currentDate
import jp.cjbc.kakeibo.model.Backup
import jp.cjbc.kakeibo.model.Budget
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.Saving
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.readStorage
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.generateId
import jp.cjbc.kakeibo.writeStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val STORAGE_KEY_PREFIX = "kakeibo:v1:"
private const val TRANSACTIONS_KEY = "transactions"
private const val BUDGETS_KEY = "budgets"
private const val SAVINGS_KEY = "savings"
private const val RECURRING_ITEMS_KEY = "recurringItems"

private val JSON = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Household account book data persisted in the platform storage. */
class Ledger {
    var transactions = migrateTransactions(load(TRANSACTIONS_KEY) { emptyList() })
        private set
    var budgets = migrateBudgets(load(BUDGETS_KEY) { emptyList() })
        private set
    var savings = load(SAVINGS_KEY) { emptyList<Saving>() }
        private set
    var recurringItems = migrateRecurringItems(load(RECURRING_ITEMS_KEY) { emptyList() })
        private set

    fun updateTransactions(value: List<Transaction>) {
        transactions = value
        save(TRANSACTIONS_KEY, value)
    }

    fun updateBudgets(value: List<Budget>) {
        budgets = value
        save(BUDGETS_KEY, value)
    }

    fun updateSavings(value: List<Saving>) {
        savings = value
        save(SAVINGS_KEY, value)
    }

    fun updateRecurringItems(value: List<RecurringItem>) {
        recurringItems = value
        save(RECURRING_ITEMS_KEY, value)
    }

    fun exportBackup(): String = JSON.encodeToString(
        Backup(
            transactions = transactions,
            budgets = budgets,
            savings = savings,
            recurringItems = recurringItems,
        ),
    )

    /** Replaces all data with the backup in [raw]. Returns `false` if [raw] cannot be parsed. */
    fun importBackup(raw: String): Boolean = runCatching {
        val backup = JSON.decodeFromString<Backup>(raw)
        updateTransactions(migrateTransactions(backup.transactions))
        updateBudgets(migrateBudgets(backup.budgets))
        updateSavings(backup.savings)
        updateRecurringItems(migrateRecurringItems(backup.recurringItems))
    }.isSuccess

    /** Records this month's transactions for active recurring items whose day has come. */
    fun applyRecurringItems() {
        val month = currentYearMonth()
        val day = currentDate().takeLast(2).toInt()
        recurringItems
            .filter { it.active && it.lastApplied != month && day >= it.dayOfMonth }
            .forEach { item ->
                val transaction = Transaction(
                    id = generateId(),
                    date = "$month-${item.dayOfMonth.toString().padStart(2, '0')}",
                    amount = item.amount,
                    type = item.type,
                    category = item.category,
                    description = "${item.description}（自動）",
                    isRecurring = true,
                )
                updateTransactions(listOf(transaction) + transactions)
                updateRecurringItems(
                    recurringItems.map {
                        if (it.id == item.id) it.copy(lastApplied = month) else it
                    },
                )
            }
    }

    private fun migrateTransactions(value: List<Transaction>): List<Transaction> =
        value.map { it.copy(category = Category.migrate(it.category)) }

    private fun migrateBudgets(value: List<Budget>): List<Budget> =
        value.map { it.copy(category = Category.migrate(it.category)) }

    private fun migrateRecurringItems(value: List<RecurringItem>): List<RecurringItem> =
        value.map { it.copy(category = Category.migrate(it.category)) }

    private inline fun <reified T> load(key: String, fallback: () -> T): T =
        readStorage(STORAGE_KEY_PREFIX + key)
            ?.let { runCatching { JSON.decodeFromString<T>(it) }.getOrNull() }
            ?: fallback()

    private inline fun <reified T> save(key: String, value: T) {
        writeStorage(STORAGE_KEY_PREFIX + key, JSON.encodeToString(value))
    }
}
