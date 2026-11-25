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
import kotlinx.serialization.Serializable
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
internal const val TRANSACTION_VERSION = 1

internal const val FUNDS = "funds"
internal const val EXPENSE_CATEGORIES = "expense_categories"
internal const val INCOME_CATEGORIES = "income_categories"
internal const val ALLOCATION_RULES = "allocation_rules"
internal const val RECURRING_ITEMS = "recurring_items"

private val MASTER_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal val RECORD_JSON = Json { ignoreUnknownKeys = true }

/** The path of the file holding the transactions of [month]. */
fun transactionsFile(month: YearMonth): String = "$TRANSACTIONS_DIRECTORY/$month.jsonl"

/**
 * Saved records this app cannot read: of an unknown version, broken, or inconsistent with the
 * rest. They are written back as they were, so that saving does not lose them. Backups hold them
 * too, as they are here.
 */
@Serializable
data class UnreadableRecords(
    /** Elements of the lists in master.json, by the list's key. */
    val master: Map<String, List<JsonElement>> = emptyMap(),
    /** Lines of the transaction files, by the file's path. */
    val transactions: Map<String, List<String>> = emptyMap(),
) {
    /**
     * Why these records cannot be saved as they are, or `null` if they can: a list that
     * master.json does not have, a path that is not a transactions file, or a line that is blank
     * or would be split into several. Records from a backup may be anything.
     */
    fun problem(): String? {
        master.keys.firstOrNull { it !in MASTER_LISTS }?.let { return "$MASTER_FILE has no list $it" }
        transactions.keys.firstOrNull { !isTransactionsFile(it) }?.let { return "$it is not a transactions file" }
        transactions.forEach { (path, lines) ->
            if (lines.any { it.isBlank() || '\n' in it || '\r' in it }) return "$path has a line that is blank or has a line break"
        }
        return null
    }

    /** These records with their references replaced as [replaceReferences] does. */
    fun withReferencesReplaced(replacements: Map<String, String>): UnreadableRecords {
        if (replacements.isEmpty()) return this
        return UnreadableRecords(
            master = master.mapValues { (list, elements) -> elements.map { replaceReferences(list, it, replacements) } },
            transactions = transactions.mapValues { (_, lines) -> lines.map { replaceReferences(TRANSACTIONS_LIST, it, replacements) } },
        )
    }
}

/**
 * Whether [path] could be a transactions file the app reads: a file in [TRANSACTIONS_DIRECTORY]
 * whose name ends with `.jsonl`, and that does not leave the directory. The name has no path
 * separator or control character, and is not `.` or `..` before `.jsonl`.
 */
private fun isTransactionsFile(path: String): Boolean {
    val name = path.removePrefix("$TRANSACTIONS_DIRECTORY/").takeIf { it != path } ?: return false
    val stem = name.removeSuffix(".jsonl").takeIf { it != name } ?: return false
    return stem != "." && stem != ".." && name.none { it == '/' || it == '\\' || it.isISOControl() }
}

/** What [decodeMaster] or [decodeTransactions] read, skipped, and warns about. */
class Decoded<T>(val value: T, val unreadable: UnreadableRecords, val warnings: List<String>)

/** master.json cannot be read at all: it is not JSON, or of a version this app does not know. */
class UnreadableMasterException(message: String, val reason: UnfixableReason) : Exception(message)

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
 * etc.), or `null` if it cannot be read, or has a value of the wrong form that the decoder would
 * misread, such as the text "1,200" for an amount.
 */
fun decodeRecord(key: RecordKey, record: SavedRecord): Any? {
    val problem = recordProblem(key, record)
    if (problem is RecordProblem.InvalidFields && problem.fields.any { it.problem == FieldProblem.WRONG_FORM }) return null
    return decodeLeniently(key, record)
}

private fun decodeLeniently(key: RecordKey, record: SavedRecord): Any? = runCatching {
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
 * [records] with every reference to a fund, category or recurring item whose ID is a key of
 * [replacements] made to the ID it maps to instead. IDs are unique across kinds of records.
 *
 * Only the fields that hold references are replaced, by the list of the record; the other fields,
 * such as a memo or a name, are kept even if they are an ID replaced.
 */
fun replaceReferences(
    records: Map<RecordKey, SavedRecord>,
    replacements: Map<String, String>,
): Map<RecordKey, SavedRecord> {
    if (replacements.isEmpty()) return records
    return records.mapValues { (key, record) ->
        when (record) {
            is SavedRecord.Master -> replaceReferences(key.list, record.element, replacements)
                .let { if (it == record.element) record else SavedRecord.Master(it) }
            is SavedRecord.TransactionLine -> record.copy(line = replaceReferences(key.list, record.line, replacements))
        }
    }
}

/**
 * The fields of a record that refer to other records by ID: [fields] of the record itself, and
 * for each list of objects in it, by its name, the [nested] fields of its elements.
 */
private class ReferenceFields(val fields: Set<String>, val nested: Map<String, Set<String>> = emptyMap())

/** The [ReferenceFields] of the records of each list. Funds and income categories refer to nothing. */
private val REFERENCE_FIELDS = mapOf(
    TRANSACTIONS_LIST to ReferenceFields(
        fields = setOf("category_id", "fund_id", "recurring_item_id"),
        nested = mapOf("allocations" to setOf("fund_id")),
    ),
    EXPENSE_CATEGORIES to ReferenceFields(setOf("default_fund_id")),
    ALLOCATION_RULES to ReferenceFields(
        fields = setOf("income_category_id", "rest_fund_id"),
        nested = mapOf("lines" to setOf("fund_id")),
    ),
    RECURRING_ITEMS to ReferenceFields(setOf("category_id", "fund_id")),
)

/** [element], a record of [list], with its references replaced as [replaceReferences] does. */
private fun replaceReferences(list: String, element: JsonElement, replacements: Map<String, String>): JsonElement {
    val references = REFERENCE_FIELDS[list] ?: return element
    val replaced = replaceFields(element, references.fields, replacements)
    if (replaced !is JsonObject) return replaced
    return JsonObject(
        replaced.mapValues { (name, value) ->
            val fields = references.nested[name]
            if (fields != null && value is JsonArray) JsonArray(value.map { replaceFields(it, fields, replacements) }) else value
        },
    )
}

/** [element] with each of its string [fields] that is a key of [replacements] made to the value it maps to. */
private fun replaceFields(element: JsonElement, fields: Set<String>, replacements: Map<String, String>): JsonElement {
    if (element !is JsonObject) return element
    return JsonObject(
        element.mapValues { (name, value) ->
            val replacement = (value as? JsonPrimitive)?.takeIf { it.isString && name in fields }?.let { replacements[it.content] }
            replacement?.let(::JsonPrimitive) ?: value
        },
    )
}

/**
 * The JSON [line], a record of [list], with its references replaced as [replaceReferences] does,
 * or [line] itself if none is, or it is not JSON.
 */
private fun replaceReferences(list: String, line: String, replacements: Map<String, String>): String {
    val parsed = runCatching { RECORD_JSON.parseToJsonElement(line) }.getOrNull()
    val replaced = parsed?.let { replaceReferences(list, it, replacements) }
    return if (replaced == null || replaced == parsed) line else RECORD_JSON.encodeToString(JsonElement.serializer(), replaced)
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
                    onSuccess = { record ->
                        (fieldsProblem(key, element.recordId(), SavedRecord.Master(element)) ?: problem(record))
                            .also { if (it == null) accepted += record }
                    },
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
 * Reads the ledger saved in [files], by path, which must have master.json, as [decodeMaster] and
 * [decodeTransactions] do.
 *
 * @throws UnreadableMasterException if master.json is not JSON or of an unknown version.
 */
fun decodeLedger(files: Map<String, String>): Decoded<LedgerData> {
    val master = decodeMaster(files.getValue(MASTER_FILE))
    val transactions = decodeTransactions(master.value, files - MASTER_FILE)
    return Decoded(
        master.value.copy(transactions = transactions.value),
        UnreadableRecords(master = master.unreadable.master, transactions = transactions.unreadable.transactions),
        master.warnings + transactions.warnings,
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
                else -> fieldsProblem(TRANSACTIONS_LIST, transaction.id.value, SavedRecord.TransactionLine(path, line))
                    ?: master.copy(transactions = listOf(transaction)).problemOrNull()
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
        ?: throw UnreadableMasterException("$MASTER_FILE is not a JSON object", UnfixableReason.NOT_JSON)
    val version = (master["version"] as? JsonPrimitive)?.intOrNull
    if (version != MASTER_VERSION) {
        throw UnreadableMasterException("$MASTER_FILE has the unknown version $version", UnfixableReason.UNKNOWN_VERSION)
    }
    return master
}

/**
 * Why the app does not take the values of [record], of the list [list] with the ID [id], though
 * it can decode them; see [recordProblem]. `null` if it takes them.
 */
private fun fieldsProblem(list: String, id: String?, record: SavedRecord): String? =
    (recordProblem(RecordKey(list, id.orEmpty()), record) as? RecordProblem.InvalidFields)
        ?.fields?.joinToString("; ") { "${it.path.segments.joinToString(".")}: ${it.problem}" }

private fun LedgerData.problemOrNull(): String? = problems().takeIf { it.isNotEmpty() }?.joinToString("; ")

private fun skipWarning(file: String, id: String?, reason: String) =
    "$file: skipped ${id ?: "a record without an ID"}: $reason"

private fun JsonElement.recordId(): String? = (this as? JsonObject)?.string("id")

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
