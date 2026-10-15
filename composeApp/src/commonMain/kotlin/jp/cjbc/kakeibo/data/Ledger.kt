package jp.cjbc.kakeibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.cjbc.kakeibo.data.v1.V1Backup
import jp.cjbc.kakeibo.data.v1.V1Budget
import jp.cjbc.kakeibo.data.v1.V1RecurringItem
import jp.cjbc.kakeibo.data.v1.V1Settings
import jp.cjbc.kakeibo.data.v1.V1Transaction
import jp.cjbc.kakeibo.data.v1.migrateFromV1
import jp.cjbc.kakeibo.model.BACKUP_VERSION
import jp.cjbc.kakeibo.model.Backup
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.problems
import jp.cjbc.kakeibo.model.withPaletteColors
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.util.generateId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.onDay
import kotlinx.datetime.yearMonth
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val STORAGE_KEY = "kakeibo:v2:ledger"
private const val UNREADABLE_STORAGE_KEY = "kakeibo:v2:ledger:unreadable"

/** Version 1 stored each list under its own key, named after the backup's property. */
private const val V1_STORAGE_KEY_PREFIX = "kakeibo:v1:"

private val JSON = Json {
    // Write every field, so backups always record their version and export date.
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Names the fund created for an expense category when migrating version 1 data. */
typealias FundNamer = suspend (CategoryId) -> String

/** The outcome of [Ledger.importBackup]. */
sealed interface ImportResult {
    /** The data was replaced. [log] lists what a version 1 backup lost in the migration. */
    data class Success(val log: List<String>) : ImportResult

    /** [raw] is not a backup this app can read, or its data is inconsistent. Nothing changed. */
    data object Failure : ImportResult
}

/**
 * Household account book data persisted in [storage]. Open one with [Ledger.open].
 *
 * [data] is snapshot state, so composables reading it recompose when it changes.
 */
class Ledger private constructor(private val storage: KeyValueStorage, initial: LedgerData) {
    var data by mutableStateOf(initial)
        private set

    /**
     * Replaces the data with what [transform] returns, and saves it.
     *
     * @throws IllegalArgumentException if the new data is inconsistent, e.g. an income whose
     *   allocations do not add up to its amount. Nothing changes then.
     */
    fun update(transform: (LedgerData) -> LedgerData) {
        val value = transform(data)
        val problems = value.problems()
        require(problems.isEmpty()) { problems.joinToString("; ") }
        data = value
        storage.write(STORAGE_KEY, JSON.encodeToString(LedgerData.serializer(), value))
    }

    fun addTransaction(transaction: Transaction) = update {
        it.copy(transactions = listOf(transaction) + it.transactions)
    }

    /** Removes the transaction [id] and returns it, so that it can be restored, or `null`. */
    fun deleteTransaction(id: TransactionId): Transaction? {
        val removed = data.transactions.find { it.id == id } ?: return null
        update { it.copy(transactions = it.transactions - removed) }
        return removed
    }

    fun exportBackup(): String = JSON.encodeToString(
        Backup.serializer(),
        Backup(version = BACKUP_VERSION, data = data),
    )

    /**
     * Replaces all data with the backup in [raw], migrating a version 1 backup. [fundName] names
     * the funds the migration creates.
     */
    suspend fun importBackup(raw: String, fundName: FundNamer): ImportResult {
        val imported = runCatching { decodeBackup(raw, fundName) }.getOrNull()
            ?: return ImportResult.Failure
        if (imported.data.problems().isNotEmpty()) return ImportResult.Failure
        update { imported.data }
        return ImportResult.Success(imported.log)
    }

    /**
     * Records this month's expense for each enabled recurring item whose day has come by
     * [today], once per month.
     */
    fun applyRecurringItems(today: LocalDate = today()) {
        val month = today.yearMonth
        val due = data.recurringItems.filter {
            it.enabled && it.lastAppliedMonth != month && today.day >= it.dayOfMonth
        }
        if (due.isEmpty()) return
        update { current ->
            current.copy(
                transactions = due.map { item ->
                    Transaction.Expense(
                        id = TransactionId(generateId()),
                        date = month.onDay(item.dayOfMonth),
                        amount = item.amount,
                        categoryId = item.categoryId,
                        memo = item.name,
                        fundId = item.fundId,
                        recurringItemId = item.id,
                    )
                } + current.transactions,
                recurringItems = current.recurringItems.map { item ->
                    if (item in due) item.copy(lastAppliedMonth = month) else item
                },
            )
        }
    }

    companion object {
        /**
         * Loads the ledger saved in [storage]. Data saved by version 1 is migrated and saved in
         * the current format; without any saved data, the ledger starts with the default
         * categories, each paid from a fund of its own. [fundName] names those funds.
         */
        suspend fun open(storage: KeyValueStorage = PlatformStorage, fundName: FundNamer): Ledger {
            storage.read(STORAGE_KEY)?.let { raw ->
                runCatching { JSON.decodeFromString(LedgerData.serializer(), raw) }
                    .onSuccess { saved ->
                        // Funds migrated before the palette existed keep old category colors.
                        val recolored = saved.withPaletteColors()
                        return Ledger(storage, saved).apply { if (recolored != saved) update { recolored } }
                    }
                // Keep what cannot be read instead of overwriting it with a fresh ledger.
                println("Ledger: saved data is unreadable; kept it as $UNREADABLE_STORAGE_KEY")
                storage.write(UNREADABLE_STORAGE_KEY, raw)
            }

            val migration = migrateFromV1(readV1Storage(storage), fundName)
            migration.log.forEach { println("Migration: $it") }
            return Ledger(storage, LedgerData()).apply { update { migration.data } }
        }
    }
}

private class DecodedBackup(val data: LedgerData, val log: List<String>)

private suspend fun decodeBackup(raw: String, fundName: FundNamer): DecodedBackup {
    val json = JSON.parseToJsonElement(raw).jsonObject
    return when (json.getValue("version").jsonPrimitive.int) {
        1 -> {
            val migration = migrateFromV1(JSON.decodeFromJsonElement(V1Backup.serializer(), json), fundName)
            DecodedBackup(migration.data, migration.log)
        }
        BACKUP_VERSION ->
            DecodedBackup(JSON.decodeFromJsonElement(Backup.serializer(), json).data, emptyList())
        else -> error("unknown backup version")
    }
}

/**
 * Reads what version 1 saved, as a version 1 backup. A list that is missing or cannot be parsed
 * reads as its default, as version 1 itself did.
 */
private fun readV1Storage(storage: KeyValueStorage): V1Backup {
    val defaults = V1Backup(version = 1)
    fun <T> read(key: String, serializer: KSerializer<T>, default: T): T =
        storage.read(V1_STORAGE_KEY_PREFIX + key)
            ?.let { raw ->
                runCatching { JSON.decodeFromString(serializer, raw) }
                    .onFailure { println("Migration: ignored unreadable version 1 $key") }
                    .getOrNull()
            }
            ?: default
    return V1Backup(
        version = 1,
        transactions = read("transactions", ListSerializer(V1Transaction.serializer()), defaults.transactions),
        budgets = read("budgets", ListSerializer(V1Budget.serializer()), defaults.budgets),
        savings = read("savings", ListSerializer(JsonElement.serializer()), defaults.savings),
        recurringItems = read(
            "recurringItems",
            ListSerializer(V1RecurringItem.serializer()),
            defaults.recurringItems,
        ),
        settings = read("settings", V1Settings.serializer(), defaults.settings),
    )
}
