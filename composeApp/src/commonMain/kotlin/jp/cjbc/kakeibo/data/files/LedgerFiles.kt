@file:OptIn(ExperimentalSerializationApi::class)

package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.problems
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The ledger is saved as master.json, which holds everything but the transactions, and a JSON
// Lines file of transactions for each month, by the month of their transaction date.

const val MASTER_FILE = "master.json"
const val TRANSACTIONS_DIRECTORY = "transactions"

/** The version of master.json this app reads and writes. */
private const val MASTER_VERSION = 1

/** The version of the transaction records this app reads and writes. */
private const val TRANSACTION_VERSION = 1

private const val FUNDS = "funds"
private const val EXPENSE_CATEGORIES = "expense_categories"
private const val INCOME_CATEGORIES = "income_categories"
private const val ALLOCATION_RULES = "allocation_rules"
private const val RECURRING_ITEMS = "recurring_items"

private val MASTER_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private val RECORD_JSON = Json { ignoreUnknownKeys = true }

/** The path of the file holding the transactions of [month]. */
fun transactionsFile(month: YearMonth): String = "$TRANSACTIONS_DIRECTORY/$month.jsonl"

/**
 * Saved records this app cannot read: of an unknown version, broken, or inconsistent with the
 * rest. They are written back as they were, so that saving does not lose them.
 */
data class UnreadableRecords(
    /** Elements of the lists in master.json, by the list's key. */
    val master: Map<String, List<JsonElement>> = emptyMap(),
    /** Lines of the transaction files, by the file's path. */
    val transactions: Map<String, List<String>> = emptyMap(),
)

/** What [decodeMaster] or [decodeTransactions] read, skipped, and warns about. */
class Decoded<T>(val value: T, val unreadable: UnreadableRecords, val warnings: List<String>)

/** master.json cannot be read at all: it is not JSON, or of a version this app does not know. */
class UnreadableMasterException(message: String) : Exception(message)

/**
 * The contents of the files that hold [data] and the [unreadable] records, by path. master.json
 * comes last, so that writing them in order writes it last.
 */
fun encodeLedger(data: LedgerData, unreadable: UnreadableRecords): Map<String, String> {
    val lines = data.transactions
        .groupBy { transactionsFile(it.date.yearMonth) }
        .mapValues { (_, transactions) -> transactions.map(::encodeTransaction) }
    val files = (lines.keys + unreadable.transactions.keys).sorted().associateWith { path ->
        val all = lines[path].orEmpty() + unreadable.transactions[path].orEmpty()
        // Lines with no date or ID, which cannot be sorted, keep their order at the end.
        all.sortedWith(compareBy(nullsLast()) { line -> lineSortKey(line) })
            .joinToString("") { "$it\n" }
    }
    return files + (MASTER_FILE to encodeMaster(data, unreadable.master))
}

private fun encodeMaster(data: LedgerData, unreadable: Map<String, List<JsonElement>>): String {
    fun <R> list(key: String, serializer: KSerializer<R>, records: List<R>) = JsonArray(
        (records.map { MASTER_JSON.encodeToJsonElement(serializer, it) } + unreadable[key].orEmpty())
            .sortedWith(compareBy(nullsLast()) { it.recordId() }),
    )
    val master = buildJsonObject {
        put("version", JsonPrimitive(MASTER_VERSION))
        put(FUNDS, list(FUNDS, FundRecord.serializer(), data.funds.map(FundRecord::of)))
        put(
            EXPENSE_CATEGORIES,
            list(
                EXPENSE_CATEGORIES,
                ExpenseCategoryRecord.serializer(),
                data.categories.filterIsInstance<Category.Expense>().map(ExpenseCategoryRecord::of),
            ),
        )
        put(
            INCOME_CATEGORIES,
            list(
                INCOME_CATEGORIES,
                IncomeCategoryRecord.serializer(),
                data.categories.filterIsInstance<Category.Income>().map(IncomeCategoryRecord::of),
            ),
        )
        put(
            ALLOCATION_RULES,
            list(ALLOCATION_RULES, AllocationRuleRecord.serializer(), data.allocationRules.map(AllocationRuleRecord::of)),
        )
        put(
            RECURRING_ITEMS,
            list(RECURRING_ITEMS, RecurringItemRecord.serializer(), data.recurringItems.map(RecurringItemRecord::of)),
        )
    }
    return MASTER_JSON.encodeToString(JsonElement.serializer(), master) + "\n"
}

private fun encodeTransaction(transaction: Transaction): String {
    val record = RECORD_JSON.encodeToJsonElement(TransactionRecord.serializer(), TransactionRecord.of(transaction))
    // The version comes first, then the type.
    val versioned = JsonObject(mapOf("v" to JsonPrimitive(TRANSACTION_VERSION)) + record.jsonObject)
    return RECORD_JSON.encodeToString(JsonElement.serializer(), versioned)
}

/** The order of transaction lines: by transaction date, then by ID. */
private fun lineSortKey(line: String): String? {
    val record = runCatching { RECORD_JSON.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
    val date = record.string("transaction_date") ?: return null
    val id = record.recordId() ?: return null
    return "$date $id"
}

/**
 * Reads master.json: everything but the transactions. A record that cannot be read, or that refers
 * to what does not exist, is skipped with a warning.
 *
 * @throws UnreadableMasterException if [content] is not JSON or of an unknown version.
 */
fun decodeMaster(content: String): Decoded<LedgerData> {
    val master = runCatching { RECORD_JSON.parseToJsonElement(content) as JsonObject }.getOrNull()
        ?: throw UnreadableMasterException("$MASTER_FILE is not a JSON object")
    val version = (master["version"] as? JsonPrimitive)?.intOrNull
    if (version != MASTER_VERSION) {
        throw UnreadableMasterException("$MASTER_FILE has the unknown version $version")
    }
    val warnings = mutableListOf<String>()
    val unreadable = mutableMapOf<String, List<JsonElement>>()

    /** Decodes the list [key], skipping a record that fails or for which [problem] says why. */
    fun <R, T> decodeList(
        key: String,
        serializer: KSerializer<R>,
        toModel: (R) -> T,
        problem: (T) -> String?,
    ): List<T> {
        val accepted = mutableListOf<T>()
        val skipped = mutableListOf<JsonElement>()
        (master[key] as? JsonArray).orEmpty().forEach { element ->
            val reason = runCatching { toModel(RECORD_JSON.decodeFromJsonElement(serializer, element)) }
                .fold(
                    onSuccess = { record -> problem(record).also { if (it == null) accepted += record } },
                    onFailure = { it.message ?: "unreadable" },
                )
            if (reason != null) {
                skipped += element
                warnings += skipWarning(MASTER_FILE, element.recordId(), reason)
            }
        }
        if (skipped.isNotEmpty()) unreadable[key] = skipped
        return accepted
    }

    val fundIds = mutableSetOf<String>()
    val funds = decodeList(FUNDS, FundRecord.serializer(), FundRecord::toModel) {
        if (fundIds.add(it.id.value)) null else "duplicate ID"
    }
    // Category IDs are unique across both kinds.
    val categoryIds = mutableSetOf<String>()
    fun categoryProblem(category: Category): String? =
        if (!categoryIds.add(category.id.value)) "duplicate ID"
        else LedgerData(funds = funds, categories = listOf(category)).problemOrNull()
    val categories =
        decodeList(EXPENSE_CATEGORIES, ExpenseCategoryRecord.serializer(), ExpenseCategoryRecord::toModel, ::categoryProblem) +
            decodeList(INCOME_CATEGORIES, IncomeCategoryRecord.serializer(), IncomeCategoryRecord::toModel, ::categoryProblem)
    val ruleIds = mutableSetOf<String>()
    val rules = decodeList(ALLOCATION_RULES, AllocationRuleRecord.serializer(), AllocationRuleRecord::toModel) {
        if (!ruleIds.add(it.id.value)) "duplicate ID"
        else LedgerData(funds = funds, categories = categories, allocationRules = listOf(it)).problemOrNull()
    }
    val itemIds = mutableSetOf<String>()
    val items = decodeList(RECURRING_ITEMS, RecurringItemRecord.serializer(), RecurringItemRecord::toModel) {
        if (!itemIds.add(it.id.value)) "duplicate ID"
        else LedgerData(funds = funds, categories = categories, recurringItems = listOf(it)).problemOrNull()
    }
    return Decoded(
        LedgerData(funds = funds, categories = categories, allocationRules = rules, recurringItems = items),
        UnreadableRecords(master = unreadable),
        warnings,
    )
}

/**
 * Reads the transaction [files], by path, against the [master] data they refer to. A line that
 * cannot be read, or that refers to what does not exist, is skipped with a warning.
 */
fun decodeTransactions(master: LedgerData, files: Map<String, String>): Decoded<List<Transaction>> {
    val warnings = mutableListOf<String>()
    val unreadable = mutableMapOf<String, List<String>>()
    val ids = mutableSetOf<String>()
    val transactions = files.keys.sorted().flatMap { path ->
        val skipped = mutableListOf<String>()
        val read = files.getValue(path).lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val record = runCatching { RECORD_JSON.parseToJsonElement(line) as JsonObject }.getOrNull()
            val version = (record?.get("v") as? JsonPrimitive)?.intOrNull
            val transaction = if (record != null && version == TRANSACTION_VERSION) {
                runCatching { RECORD_JSON.decodeFromJsonElement(TransactionRecord.serializer(), record).toModel() }
                    .getOrNull()
            } else {
                null
            }
            val reason = when {
                record == null -> "not a JSON object"
                version != TRANSACTION_VERSION -> "unknown version $version"
                transaction == null -> "unreadable"
                transaction.id.value in ids -> "duplicate ID"
                else -> master.copy(transactions = listOf(transaction)).problemOrNull()
            }
            if (reason == null) {
                ids += transaction!!.id.value
                transaction
            } else {
                skipped += line
                warnings += skipWarning(path, record?.recordId(), reason)
                null
            }
        }.toList()
        if (skipped.isNotEmpty()) unreadable[path] = skipped
        read
    }
    return Decoded(transactions, UnreadableRecords(transactions = unreadable), warnings)
}

private fun LedgerData.problemOrNull(): String? = problems().takeIf { it.isNotEmpty() }?.joinToString("; ")

private fun skipWarning(file: String, id: String?, reason: String) =
    "$file: skipped ${id ?: "a record without an ID"}: $reason"

private fun JsonElement.recordId(): String? = (this as? JsonObject)?.string("id")

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
