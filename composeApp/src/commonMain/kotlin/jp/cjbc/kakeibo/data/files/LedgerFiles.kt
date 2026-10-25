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

/** The key of the transactions in a [RecordKey]; the other keys are the lists of master.json. */
const val TRANSACTIONS_LIST = "transactions"

private val MASTER_LISTS = listOf(FUNDS, EXPENSE_CATEGORIES, INCOME_CATEGORIES, ALLOCATION_RULES, RECURRING_ITEMS)

/**
 * Identifies a saved record: the list it is in, and its ID. A record without an ID, or whose ID
 * another record of the list already has, is identified by its whole content instead.
 */
data class RecordKey(val list: String, val id: String)

/** A record as it is saved. */
sealed interface SavedRecord {
    /** An element of a list of master.json. */
    data class Master(val element: JsonElement) : SavedRecord

    /** A line of the transactions [file]. */
    data class TransactionLine(val file: String, val line: String) : SavedRecord
}

/**
 * [record], saved under [key], as the record class of its list ([FundRecord], [TransactionRecord],
 * etc.), or `null` if it cannot be read.
 */
fun decodeRecord(key: RecordKey, record: SavedRecord): Any? = runCatching {
    when (record) {
        is SavedRecord.TransactionLine -> {
            val line = RECORD_JSON.parseToJsonElement(record.line).jsonObject
            if ((line["v"] as? JsonPrimitive)?.intOrNull != TRANSACTION_VERSION) return null
            RECORD_JSON.decodeFromJsonElement(TransactionRecord.serializer(), line)
        }
        is SavedRecord.Master -> {
            val serializer: KSerializer<*> = when (key.list) {
                FUNDS -> FundRecord.serializer()
                EXPENSE_CATEGORIES -> ExpenseCategoryRecord.serializer()
                INCOME_CATEGORIES -> IncomeCategoryRecord.serializer()
                ALLOCATION_RULES -> AllocationRuleRecord.serializer()
                RECURRING_ITEMS -> RecurringItemRecord.serializer()
                else -> return null
            }
            RECORD_JSON.decodeFromJsonElement(serializer, record.element)
        }
    }
}.getOrNull()

/** The kinds of records that other records refer to by ID. */
enum class ReferenceKind(internal val list: String) {
    FUND(FUNDS),
    EXPENSE_CATEGORY(EXPENSE_CATEGORIES),
    INCOME_CATEGORY(INCOME_CATEGORIES),
}

/** A reference from one record to another, of [kind], by its [id]. */
data class Reference(val kind: ReferenceKind, val id: String)

/** The funds and categories that [record], saved under [key], refers to, as far as it can be read. */
private fun references(key: RecordKey, record: SavedRecord): List<Reference> {
    fun fund(id: String) = Reference(ReferenceKind.FUND, id)
    return when (val decoded = decodeRecord(key, record)) {
        is TransactionRecord.Expense -> listOf(fund(decoded.fundId), Reference(ReferenceKind.EXPENSE_CATEGORY, decoded.categoryId))
        is TransactionRecord.Income ->
            decoded.allocations.map { fund(it.fundId) } + Reference(ReferenceKind.INCOME_CATEGORY, decoded.categoryId)
        is ExpenseCategoryRecord -> listOf(fund(decoded.defaultFundId))
        is AllocationRuleRecord -> decoded.lines.map {
            when (it) {
                is RuleLineRecord.Fixed -> fund(it.fundId)
                is RuleLineRecord.Percent -> fund(it.fundId)
                is RuleLineRecord.Ask -> fund(it.fundId)
            }
        } + fund(decoded.restFundId)
        is RecurringItemRecord -> listOf(fund(decoded.fundId), Reference(ReferenceKind.EXPENSE_CATEGORY, decoded.categoryId))
        else -> emptyList()
    }
}

/**
 * The funds and categories that the [hidden] records among [records] refer to, but that [records]
 * do not have, each with the hidden records that it hides: those that refer to it, and those that
 * refer to a record it hides, such as the expenses of a category whose default fund is missing.
 */
fun missingReferences(
    hidden: Map<RecordKey, SavedRecord>,
    records: Map<RecordKey, SavedRecord>,
): Map<Reference, List<RecordKey>> {
    val referenced = hidden.mapValues { (key, record) -> references(key, record).distinct().map { RecordKey(it.kind.list, it.id) } }
    val missing = hidden.mapValues { (key, _) ->
        referenced.getValue(key).filter { it !in records }.toMutableSet()
    }
    // A record hidden by another inherits what hides that one; the sets only grow, so this ends.
    do {
        var changed = false
        referenced.forEach { (key, targets) ->
            targets.forEach { target -> missing[target]?.let { if (missing.getValue(key).addAll(it)) changed = true } }
        }
    } while (changed)
    val kinds = ReferenceKind.entries.associateBy { it.list }
    return hidden.keys
        .flatMap { key -> missing.getValue(key).map { Reference(kinds.getValue(it.list), it.id) to key } }
        .groupBy({ it.first }, { it.second })
}

/**
 * [records] with every reference to a fund or category whose ID is a key of [replacements] made to
 * the ID it maps to instead. IDs are unique across funds and categories.
 */
fun replaceReferences(
    records: Map<RecordKey, SavedRecord>,
    replacements: Map<String, String>,
): Map<RecordKey, SavedRecord> {
    if (replacements.isEmpty()) return records
    fun replace(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (name, value) ->
                val replacement = (value as? JsonPrimitive)?.takeIf { it.isString && name != "id" }?.let { replacements[it.content] }
                replacement?.let(::JsonPrimitive) ?: replace(value)
            },
        )
        is JsonArray -> JsonArray(element.map(::replace))
        else -> element
    }
    return records.mapValues { (_, record) ->
        when (record) {
            is SavedRecord.Master -> replace(record.element).let { if (it == record.element) record else SavedRecord.Master(it) }
            is SavedRecord.TransactionLine -> {
                val line = runCatching { RECORD_JSON.parseToJsonElement(record.line) }.getOrNull()
                val replaced = line?.let(::replace)
                if (replaced == null || replaced == line) record
                else record.copy(line = RECORD_JSON.encodeToString(JsonElement.serializer(), replaced))
            }
        }
    }
}

/**
 * The contents of the files that hold [data] and the [unreadable] records, by path. master.json
 * comes last, so that writing them in order writes it last.
 */
fun encodeLedger(data: LedgerData, unreadable: UnreadableRecords): Map<String, String> {
    val records = SavedRecords()
    fun <R> addMaster(list: String, serializer: KSerializer<R>, values: List<R>) =
        values.forEach { records.addMaster(list, MASTER_JSON.encodeToJsonElement(serializer, it)) }
    addMaster(FUNDS, FundRecord.serializer(), data.funds.map(FundRecord::of))
    addMaster(
        EXPENSE_CATEGORIES,
        ExpenseCategoryRecord.serializer(),
        data.categories.filterIsInstance<Category.Expense>().map(ExpenseCategoryRecord::of),
    )
    addMaster(
        INCOME_CATEGORIES,
        IncomeCategoryRecord.serializer(),
        data.categories.filterIsInstance<Category.Income>().map(IncomeCategoryRecord::of),
    )
    addMaster(ALLOCATION_RULES, AllocationRuleRecord.serializer(), data.allocationRules.map(AllocationRuleRecord::of))
    addMaster(RECURRING_ITEMS, RecurringItemRecord.serializer(), data.recurringItems.map(RecurringItemRecord::of))
    data.transactions.forEach { records.addTransaction(transactionsFile(it.date.yearMonth), encodeTransaction(it)) }
    unreadable.master.forEach { (list, elements) -> elements.forEach { records.addMaster(list, it) } }
    unreadable.transactions.forEach { (file, lines) -> lines.forEach { records.addTransaction(file, it) } }
    return layoutRecords(records.records)
}

/**
 * The records saved in [files], by path, as they are: neither decoded nor checked. Missing
 * master.json reads as having no records.
 *
 * @throws UnreadableMasterException if master.json is not JSON or of an unknown version.
 */
fun readRecords(files: Map<String, String>): Map<RecordKey, SavedRecord> {
    val records = SavedRecords()
    files[MASTER_FILE]?.let { content ->
        val master = parseMaster(content)
        MASTER_LISTS.forEach { list -> (master[list] as? JsonArray).orEmpty().forEach { records.addMaster(list, it) } }
    }
    files.keys.filter { it.startsWith("$TRANSACTIONS_DIRECTORY/") && it.endsWith(".jsonl") }.sorted().forEach { path ->
        files.getValue(path).lineSequence().filter { it.isNotBlank() }.forEach { records.addTransaction(path, it) }
    }
    return records.records
}

/**
 * The contents of the files that hold [records], by path: master.json with each list sorted by
 * ID, and each transactions file with its lines sorted by transaction date and ID. Records that
 * cannot be sorted keep their order at the end. master.json comes last.
 */
fun layoutRecords(records: Map<RecordKey, SavedRecord>): Map<String, String> {
    val lines = records.values.filterIsInstance<SavedRecord.TransactionLine>().groupBy { it.file }
    val files = lines.keys.sorted().associateWith { file ->
        lines.getValue(file).map { it.line }
            .sortedWith(compareBy(nullsLast()) { line -> lineSortKey(line) })
            .joinToString("") { "$it\n" }
    }
    val master = buildJsonObject {
        put("version", JsonPrimitive(MASTER_VERSION))
        MASTER_LISTS.forEach { list ->
            val elements = records.filterKeys { it.list == list }.values.map { (it as SavedRecord.Master).element }
            put(list, JsonArray(elements.sortedWith(compareBy(nullsLast()) { it.recordId() })))
        }
    }
    return files + (MASTER_FILE to MASTER_JSON.encodeToString(JsonElement.serializer(), master) + "\n")
}

/** Records in the order added, each under the first [RecordKey] that is free for it. */
private class SavedRecords {
    val records = LinkedHashMap<RecordKey, SavedRecord>()

    fun addMaster(list: String, element: JsonElement) =
        add(list, element.recordId(), element.toString(), SavedRecord.Master(element))

    fun addTransaction(file: String, line: String) {
        val id = runCatching { RECORD_JSON.parseToJsonElement(line) }.getOrNull()?.recordId()
        add(TRANSACTIONS_LIST, id, line, SavedRecord.TransactionLine(file, line))
    }

    // Records with the same content and no ID are kept once.
    private fun add(list: String, id: String?, content: String, record: SavedRecord) {
        val key = id?.let { RecordKey(list, it) }?.takeIf { it !in records } ?: RecordKey(list, "content:$content")
        records[key] = record
    }
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
    val master = parseMaster(content)
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

/** @throws UnreadableMasterException if [content] is not JSON or of an unknown version. */
private fun parseMaster(content: String): JsonObject {
    val master = runCatching { RECORD_JSON.parseToJsonElement(content) as JsonObject }.getOrNull()
        ?: throw UnreadableMasterException("$MASTER_FILE is not a JSON object")
    val version = (master["version"] as? JsonPrimitive)?.intOrNull
    if (version != MASTER_VERSION) {
        throw UnreadableMasterException("$MASTER_FILE has the unknown version $version")
    }
    return master
}

private fun LedgerData.problemOrNull(): String? = problems().takeIf { it.isNotEmpty() }?.joinToString("; ")

private fun skipWarning(file: String, id: String?, reason: String) =
    "$file: skipped ${id ?: "a record without an ID"}: $reason"

private fun JsonElement.recordId(): String? = (this as? JsonObject)?.string("id")

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
