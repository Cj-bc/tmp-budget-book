package jp.cjbc.kakeibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.UnreadableRecords
import jp.cjbc.kakeibo.data.files.decodeMaster
import jp.cjbc.kakeibo.data.files.decodeTransactions
import jp.cjbc.kakeibo.data.files.encodeLedger
import jp.cjbc.kakeibo.data.v1.V1Backup
import jp.cjbc.kakeibo.data.v1.V1Budget
import jp.cjbc.kakeibo.data.v1.V1RecurringItem
import jp.cjbc.kakeibo.data.v1.V1Settings
import jp.cjbc.kakeibo.data.v1.V1Transaction
import jp.cjbc.kakeibo.data.v1.migrateFromV1
import jp.cjbc.kakeibo.data.v2.migrateFromV2
import jp.cjbc.kakeibo.model.BACKUP_VERSION
import jp.cjbc.kakeibo.model.Backup
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryPreset
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.model.problems
import jp.cjbc.kakeibo.model.withPaletteColors
import jp.cjbc.kakeibo.model.today
import jp.cjbc.kakeibo.util.generateUlid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.onDay
import kotlinx.datetime.yearMonth
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Version 3 stored the whole ledger as one JSON in the key-value storage. */
private const val V3_STORAGE_KEY = "kakeibo:v3:ledger"

/** Version 2 stored the ledger without category names. */
private const val V2_STORAGE_KEY = "kakeibo:v2:ledger"

/** Version 1 stored each list under its own key, named after the backup's property. */
private const val V1_STORAGE_KEY_PREFIX = "kakeibo:v1:"

private val JSON = Json {
    // Write every field, so backups always record their version and export date.
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Names a preset category in the user's language, for data saved by older versions. */
typealias PresetNamer = suspend (CategoryPreset) -> String

/** The outcome of [Ledger.importBackup]. */
sealed interface ImportResult {
    /** The data was replaced. [log] lists what a version 1 backup lost in the migration. */
    data class Success(val log: List<String>) : ImportResult

    /** [raw] is not a backup this app can read, or its data is inconsistent. Nothing changed. */
    data object Failure : ImportResult
}

/**
 * Household account book data persisted in [files] (see `data/files/LedgerFiles.kt`). Open one
 * with [Ledger.open].
 *
 * [data] is snapshot state, so composables reading it recompose when it changes.
 */
class Ledger private constructor(
    private val files: FileStorage,
    /** Who the commits are by, read for each commit, since the settings may change it. */
    private val author: () -> CommitAuthor,
    initial: LedgerData,
    /** Saved records this app cannot read, written back as they were. */
    private val unreadable: UnreadableRecords,
    /** The contents of the files as last saved or read, by path. */
    private var saved: Map<String, String>,
) {
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
        replace(value, describeChanges(data, value))
    }

    /**
     * Replaces the data with [value], saves it, and commits it with [message].
     *
     * @throws IllegalArgumentException if [value] is inconsistent.
     */
    private fun replace(value: LedgerData, message: String) {
        val problems = value.problems()
        require(problems.isEmpty()) { problems.joinToString("; ") }
        data = value.inSavedOrder()
        save()
        files.commit(message, author())
    }

    /** Writes the files that changed since [saved], and deletes the ones no longer needed. */
    private fun save() {
        val contents = encodeLedger(data, unreadable)
        contents.forEach { (path, content) -> if (saved[path] != content) files.write(path, content) }
        (saved.keys - contents.keys).forEach(files::delete)
        saved = contents
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
     * Replaces all data with the backup in [raw], migrating a backup of an older version.
     * [presetName] names the presets in it. IDs that are not ULIDs are replaced with new ones.
     */
    suspend fun importBackup(raw: String, presetName: PresetNamer): ImportResult {
        val imported = runCatching { decodeBackup(raw, presetName) }.getOrNull()
            ?: return ImportResult.Failure
        val data = imported.data.withUlids()
        if (data.problems().isNotEmpty()) return ImportResult.Failure
        replace(data, "Restore a backup")
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
                        id = TransactionId(generateUlid()),
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
         * Loads the ledger saved in [files]. Without them, the data that older versions saved in
         * [legacy] is migrated and saved in the files, with new ULIDs; without any saved data, the
         * ledger starts with the preset categories, each paid from a fund of its own. [presetName]
         * names the presets.
         *
         * Records that cannot be read are skipped with a warning, and kept in the files. If
         * master.json itself cannot be read, it is kept as `master.json.unreadable`, and the
         * ledger starts afresh.
         *
         * Each change is committed by [author]. Changes to the files that were not committed, say
         * because the app stopped in between, are committed when the ledger is opened.
         */
        suspend fun open(
            files: FileStorage = PlatformFileStorage,
            legacy: KeyValueStorage = PlatformStorage,
            author: () -> CommitAuthor = { readCommitAuthor(legacy) },
            presetName: PresetNamer,
        ): Ledger {
            files.commit("Record changes to the files that were not committed", author())
            // master.json is written last, so without it the files are not complete.
            val masterContent = files.read(MASTER_FILE) ?: run {
                val migrated = readLegacy(legacy, presetName).withUlids()
                return Ledger(files, author, LedgerData(), UnreadableRecords(), saved = emptyMap())
                    .apply { replace(migrated, "Start the ledger") }
            }

            val transactionFiles = files.readTransactionFiles()
            val master = runCatching { decodeMaster(masterContent) }.getOrElse { error ->
                println("Ledger: ${error.message}; kept it as $MASTER_FILE.unreadable")
                files.write("$MASTER_FILE.unreadable", masterContent)
                null
            }
            val masterData = master?.value ?: freshData(presetName)
            val transactions = decodeTransactions(masterData, transactionFiles)
            (master?.warnings.orEmpty() + transactions.warnings).forEach { println("Ledger: $it") }
            return Ledger(
                files,
                author,
                masterData.copy(transactions = transactions.value).inSavedOrder(),
                UnreadableRecords(
                    master = master?.unreadable?.master.orEmpty(),
                    transactions = transactions.unreadable.transactions,
                ),
                saved = transactionFiles + (MASTER_FILE to masterContent),
            ).apply { if (master == null) replace(data, "Start a new ledger, keeping the unreadable $MASTER_FILE aside") }
        }
    }
}

/**
 * This data in the order it reads from the files, so that reading them gives equal data: records
 * of master.json by ID, and the transactions latest first, by date and then by ID. A ULID begins
 * with the time it was made, so the transactions of a day are the last added first.
 */
private fun LedgerData.inSavedOrder() = LedgerData(
    funds = funds.sortedBy { it.id.value },
    // master.json lists the expense categories before the income ones.
    categories = categories.sortedWith(compareBy({ it is Category.Income }, { it.id.value })),
    transactions = transactions.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id.value }),
    allocationRules = allocationRules.sortedBy { it.id.value },
    recurringItems = recurringItems.sortedBy { it.id.value },
)

/** A new ledger: the preset categories, each paid from a fund of its own. */
private suspend fun freshData(presetName: PresetNamer): LedgerData =
    migrateFromV1(V1Backup(version = 1), presetName).data.withUlids()

/**
 * Reads the ledger that older versions saved in [storage], converting it to the current format.
 * Without any, returns the data of a new ledger.
 */
private suspend fun readLegacy(storage: KeyValueStorage, presetName: PresetNamer): LedgerData {
    // Funds migrated before the palette existed keep old category colors.
    readSaved(storage, V3_STORAGE_KEY) { it }?.let { return it.withPaletteColors() }
    readSaved(storage, V2_STORAGE_KEY) { migrateFromV2(it, presetName) }?.let { return it.withPaletteColors() }
    val migration = migrateFromV1(readV1Storage(storage), presetName)
    migration.log.forEach { println("Migration: $it") }
    return migration.data
}

/**
 * Reads the ledger saved under [key], converting its JSON to the current format with [migrate].
 * Returns `null` if nothing is saved there, or if it cannot be read: that is kept under another
 * key instead of being overwritten.
 */
private suspend inline fun readSaved(
    storage: KeyValueStorage,
    key: String,
    migrate: (JsonObject) -> JsonObject,
): LedgerData? {
    val raw = storage.read(key) ?: return null
    return runCatching {
        JSON.decodeFromJsonElement(LedgerData.serializer(), migrate(JSON.parseToJsonElement(raw).jsonObject))
    }.onFailure {
        val unreadableKey = "$key:unreadable"
        println("Ledger: saved data is unreadable; kept it as $unreadableKey")
        storage.write(unreadableKey, raw)
    }.getOrNull()
}

private class DecodedBackup(val data: LedgerData, val log: List<String>)

private suspend fun decodeBackup(raw: String, presetName: PresetNamer): DecodedBackup {
    val json = JSON.parseToJsonElement(raw).jsonObject
    return when (json.getValue("version").jsonPrimitive.int) {
        1 -> {
            val migration = migrateFromV1(JSON.decodeFromJsonElement(V1Backup.serializer(), json), presetName)
            DecodedBackup(migration.data, migration.log)
        }
        2 -> {
            val data = migrateFromV2(json.getValue("data").jsonObject, presetName)
            DecodedBackup(JSON.decodeFromJsonElement(LedgerData.serializer(), data), emptyList())
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
