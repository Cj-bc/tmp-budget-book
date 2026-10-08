package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.model.Budget
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.Contribution
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.Saving
import jp.cjbc.kakeibo.model.EXPENSE_CATEGORIES
import jp.cjbc.kakeibo.model.INCOME_CATEGORIES
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class InMemoryStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }
}

/** Every key a [Transaction] is exported with. */
private val TRANSACTION_KEYS =
    setOf("id", "date", "amount", "type", "category", "description", "isRecurring")

/** The (type, category) pairs the app offers by default: all expense and all income categories. */
private val DEFAULT_CATEGORY_PAIRS: List<Pair<TransactionType, Category>> =
    EXPENSE_CATEGORIES.map { TransactionType.EXPENSE to it } +
        INCOME_CATEGORIES.map { TransactionType.INCOME to it }

private fun exportedTransactions(ledger: Ledger): JsonArray =
    Json.parseToJsonElement(ledger.exportBackup()).jsonObject.getValue("transactions").jsonArray

/** Builds a backup JSON by hand, so the tests do not depend on how the app encodes it. */
private fun backupJson(transactions: List<Transaction>, budgets: List<Budget> = emptyList()): String {
    // JSON primitives escape arbitrary user text while explicit keys keep fixtures independent
    // of the model serializers, which are the behavior these tests exercise.
    return buildJsonObject {
        put("version", 1)
        put("exportedAt", "2025-01-31")
        put("transactions", buildJsonArray {
            transactions.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("date", it.date)
                    put("amount", it.amount)
                    put("type", it.type.name)
                    put("category", it.category)
                    put("description", it.description)
                    put("isRecurring", it.isRecurring)
                })
            }
        })
        put("budgets", buildJsonArray {
            budgets.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("category", it.category)
                    put("monthlyAmount", it.monthlyAmount)
                })
            }
        })
    }.toString()
}

class LedgerJsonTest {
    // --- export -------------------------------------------------------------------------------

    @Test
    fun exportsEveryDefaultCategoryWithItsOwnAmountUnderTheExpectedKeys() {
        val transactions = DEFAULT_CATEGORY_PAIRS.mapIndexed { index, (type, category) ->
            Transaction(
                id = "id-$index",
                date = "2025-01-15",
                amount = 1000.0 + index,
                type = type,
                category = category.id,
                description = "${type.name} ${category.id}",
                isRecurring = true,
            )
        }
        val ledger = Ledger(InMemoryStorage())
        ledger.updateTransactions(transactions)

        val exported = exportedTransactions(ledger)

        assertEquals(DEFAULT_CATEGORY_PAIRS.size, exported.size)
        DEFAULT_CATEGORY_PAIRS.forEachIndexed { index, (type, category) ->
            val obj = exported[index].jsonObject
            assertEquals(TRANSACTION_KEYS, obj.keys, "keys of ${type.name}/${category.id}")
            assertEquals("id-$index", obj.getValue("id").jsonPrimitive.content)
            assertEquals("2025-01-15", obj.getValue("date").jsonPrimitive.content)
            assertEquals(1000.0 + index, obj.getValue("amount").jsonPrimitive.double)
            assertEquals(type.name, obj.getValue("type").jsonPrimitive.content)
            assertEquals(category.id, obj.getValue("category").jsonPrimitive.content)
            assertEquals("${type.name} ${category.id}", obj.getValue("description").jsonPrimitive.content)
            assertEquals(true, obj.getValue("isRecurring").jsonPrimitive.content.toBoolean())
        }
    }

    @Test
    fun exportsCategoriesAsLanguageIndependentIds() {
        val expected = setOf(
            "food", "transport", "housing", "utilities", "medical", "entertainment", "clothing",
            "daily_goods", "education", "communication", "other", "salary", "side_income", "bonus",
        )
        assertEquals(expected, Category.entries.map { it.id }.toSet())
        assertEquals(expected, DEFAULT_CATEGORY_PAIRS.map { it.second.id }.toSet())
    }

    @Test
    fun exportsTheBudgetsOfEveryExpenseCategory() {
        val ledger = Ledger(InMemoryStorage())
        ledger.updateBudgets(
            EXPENSE_CATEGORIES.mapIndexed { index, category ->
                Budget(id = "budget-$index", category = category.id, monthlyAmount = 10000.0 + index)
            },
        )

        val budgets = Json.parseToJsonElement(ledger.exportBackup()).jsonObject.getValue("budgets").jsonArray

        assertEquals(EXPENSE_CATEGORIES.size, budgets.size)
        EXPENSE_CATEGORIES.forEachIndexed { index, category ->
            val obj = budgets[index].jsonObject
            assertEquals(setOf("id", "category", "monthlyAmount"), obj.keys)
            assertEquals(category.id, obj.getValue("category").jsonPrimitive.content)
            assertEquals(10000.0 + index, obj.getValue("monthlyAmount").jsonPrimitive.double)
        }
    }

    @Test
    fun exportsIdenticalTransactionsAsSeparateEntries() {
        val ledger = Ledger(InMemoryStorage())
        ledger.updateBudgets(listOf(Budget("budget-food", Category.FOOD.id, 30000.0)))
        ledger.updateTransactions(
            List(3) { index ->
                Transaction(
                    id = "id-$index",
                    date = "2025-01-15",
                    amount = 980.0,
                    type = TransactionType.EXPENSE,
                    category = Category.FOOD.id,
                    description = "lunch",
                )
            },
        )

        val exported = exportedTransactions(ledger)

        assertEquals(3, exported.size)
        assertEquals(listOf("id-0", "id-1", "id-2"), exported.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        exported.forEach {
            val obj = it.jsonObject
            assertEquals("food", obj.getValue("category").jsonPrimitive.content)
            assertEquals(980.0, obj.getValue("amount").jsonPrimitive.double)
        }
    }

    // --- import -------------------------------------------------------------------------------

    @Test
    fun preservesEscapedTextThroughImportAndExport() {
        val transaction = Transaction("escaped", "2025-01-15", 980.0, TransactionType.EXPENSE,
            Category.FOOD.id, "Lunch at \"Cafe\"\\Bakery\nSecond line\t日本語")
        val ledger = Ledger(InMemoryStorage())

        assertTrue(ledger.importBackup(backupJson(listOf(transaction))))
        assertEquals(listOf(transaction), ledger.transactions)
        assertEquals(transaction.description,
            exportedTransactions(ledger).single().jsonObject.getValue("description").jsonPrimitive.content)

        val target = Ledger(InMemoryStorage())
        assertTrue(target.importBackup(ledger.exportBackup()))
        assertEquals(listOf(transaction), target.transactions)
    }

    @Test
    fun importsEveryDefaultCategoryFromJson() {
        val transactions = DEFAULT_CATEGORY_PAIRS.mapIndexed { index, (type, category) ->
            Transaction("id-$index", "2025-01-15", 1000.0 + index, type, category.id, "memo $index")
        }
        val ledger = Ledger(InMemoryStorage())

        assertTrue(ledger.importBackup(backupJson(transactions)))

        assertEquals(transactions, ledger.transactions)
    }

    @Test
    fun importsIdenticalTransactionsAsSeparateTransactions() {
        val budget = Budget("budget-food", Category.FOOD.id, 30000.0)
        val transactions = List(3) { index ->
            Transaction("id-$index", "2025-01-15", 980.0, TransactionType.EXPENSE, Category.FOOD.id, "lunch")
        }
        val ledger = Ledger(InMemoryStorage())

        assertTrue(ledger.importBackup(backupJson(transactions, listOf(budget))))

        assertEquals(3, ledger.transactions.size)
        assertEquals(3, ledger.transactions.map { it.id }.toSet().size)
        assertEquals(transactions, ledger.transactions)
        assertEquals(listOf(budget), ledger.budgets)
    }

    @Test
    fun importedIdenticalTransactionsArePersistedSeparately() {
        val storage = InMemoryStorage()
        val transactions = List(3) { index ->
            Transaction("id-$index", "2025-01-15", 980.0, TransactionType.EXPENSE, Category.FOOD.id)
        }
        Ledger(storage).importBackup(backupJson(transactions))

        val reopened = Ledger(storage)

        assertEquals(transactions, reopened.transactions)
    }

    @Test
    fun importedTransactionCanBeRemovedWithoutAffectingItsTwins() {
        val ledger = Ledger(InMemoryStorage())
        val transactions = List(3) { index ->
            Transaction("id-$index", "2025-01-15", 980.0, TransactionType.EXPENSE, Category.FOOD.id)
        }
        ledger.importBackup(backupJson(transactions))

        ledger.updateTransactions(ledger.transactions.filter { it.id != "id-1" })

        assertEquals(listOf("id-0", "id-2"), ledger.transactions.map { it.id })
    }

    // --- round trip ---------------------------------------------------------------------------

    @Test
    fun exportedBackupCanBeImportedIntoAnotherLedger() {
        val source = Ledger(InMemoryStorage())
        source.updateSavings(
            listOf(Saving("saving-1", "Trip", 2000.0, "2025-01", "2025-12",
                listOf(Contribution("contribution-1", "2025-01-15", 1500.0)))),
        )
        source.updateRecurringItems(
            listOf(RecurringItem("recurring-1", "Rent", 50000.0,
                TransactionType.EXPENSE, Category.HOUSING.id, 15, active = false, lastApplied = "2025-01")),
        )
        source.updateBudgets(EXPENSE_CATEGORIES.mapIndexed { i, c -> Budget("budget-$i", c.id, 5000.0 + i) })
        source.updateTransactions(
            DEFAULT_CATEGORY_PAIRS.mapIndexed { i, (type, category) ->
                Transaction("id-$i", "2025-01-15", 1000.0 + i, type, category.id, "memo $i", isRecurring = i % 2 == 0)
            } + List(3) { i ->
                Transaction("twin-$i", "2025-01-20", 500.0, TransactionType.EXPENSE, Category.FOOD.id, "same")
            },
        )

        val storage = InMemoryStorage()
        val target = Ledger(storage)
        assertTrue(target.importBackup(source.exportBackup()))

        assertEquals(source.transactions, target.transactions)
        assertEquals(source.budgets, target.budgets)
        assertEquals(source.savings, target.savings)
        assertEquals(source.recurringItems, target.recurringItems)

        val reopened = Ledger(storage)
        assertEquals(source.transactions, reopened.transactions)
        assertEquals(source.budgets, reopened.budgets)
        assertEquals(source.savings, reopened.savings)
        assertEquals(source.recurringItems, reopened.recurringItems)
    }

    @Test
    fun importReplacesExistingTransactions() {
        val ledger = Ledger(InMemoryStorage())
        ledger.updateTransactions(
            listOf(Transaction("old", "2024-12-01", 1.0, TransactionType.EXPENSE, Category.FOOD.id)),
        )
        val imported = Transaction("new", "2025-01-01", 2.0, TransactionType.INCOME, Category.SALARY.id)

        ledger.importBackup(backupJson(listOf(imported)))

        assertEquals(listOf(imported), ledger.transactions)
    }

    @Test
    fun invalidJsonIsRejectedAndKeepsExistingData() {
        val ledger = Ledger(InMemoryStorage())
        val existing = Transaction("old", "2024-12-01", 1.0, TransactionType.EXPENSE, Category.FOOD.id)
        ledger.updateTransactions(listOf(existing))

        assertFalse(ledger.importBackup("{ not json"))
        assertFalse(ledger.importBackup("""{"transactions":[{"id":"x"}]}"""))

        assertEquals(listOf(existing), ledger.transactions)
    }

    @Test
    fun unknownKeysInTheBackupAreIgnored() {
        val raw = """
            {"futureField":true,"transactions":[
              {"id":"a","date":"2025-01-01","amount":1.0,"type":"EXPENSE","category":"food","extra":"x"}
            ]}
        """.trimIndent()
        val ledger = Ledger(InMemoryStorage())

        assertTrue(ledger.importBackup(raw))

        assertEquals(1, ledger.transactions.size)
        assertEquals("a", ledger.transactions.single().id)
    }
}
