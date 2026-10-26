package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// Why the records that the app cannot show are hidden, and fixing them: a value of the wrong
// form, or a reference to a fund or category that is not there. Records the app cannot read at all
// can only be fixed in the repository.

/** A value in a record: the keys of the objects, and the indices of the lists, leading to it. */
data class FieldPath(val segments: List<String>) {
    operator fun plus(segment: String) = FieldPath(segments + segment)

    operator fun plus(index: Int) = FieldPath(segments + index.toString())

    companion object {
        /** The record itself. */
        val ROOT = FieldPath(emptyList())
    }
}

/** What a field of a record holds, as far as checking and editing its value goes. */
enum class FieldType(private val serializer: KSerializer<*>) {
    TEXT(String.serializer()),

    /** Text, or `null` for none. */
    OPTIONAL_TEXT(String.serializer().nullable),

    /** A whole number such as an amount. */
    NUMBER(Long.serializer()),

    /** A smaller whole number such as a day of the month. */
    INTEGER(Int.serializer()),
    BOOLEAN(Boolean.serializer()),
    DATE(LocalDate.serializer()),
    MONTH(YearMonth.serializer()),

    /** A month, or `null` for none. */
    OPTIONAL_MONTH(YearMonth.serializer().nullable),

    /** The ID of a fund. */
    FUND(String.serializer()),

    /** The ID of an expense category. */
    EXPENSE_CATEGORY(String.serializer()),

    /** The ID of an income category. */
    INCOME_CATEGORY(String.serializer()),
    ;

    /** Whether the app reads [value] as a value of this type, as it reads the record. */
    fun accepts(value: JsonElement): Boolean = runCatching { RECORD_JSON.decodeFromJsonElement(serializer, value) }.isSuccess

    /**
     * [input], as the user typed it, as a value of this type. What cannot be one stays text,
     * which [accepts] refuses unless this type is text.
     */
    fun parse(input: String): JsonElement = when (this) {
        TEXT, FUND, EXPENSE_CATEGORY, INCOME_CATEGORY -> JsonPrimitive(input)
        NUMBER -> input.trim().toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(input)
        INTEGER -> input.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(input)
        BOOLEAN -> input.toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(input)
        DATE, MONTH -> JsonPrimitive(input.trim())
        OPTIONAL_TEXT -> if (input.isEmpty()) JsonNull else JsonPrimitive(input)
        OPTIONAL_MONTH -> if (input.isBlank()) JsonNull else JsonPrimitive(input.trim())
    }

    /** [value] as the user edits it: what [parse] reads back as it. */
    fun input(value: JsonElement?): String = when (value) {
        null, JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }
}

/** Why the app does not take the value of a field. */
enum class FieldProblem {
    MISSING,

    /** Not of the field's [FieldType]. */
    WRONG_FORM,

    /** An amount that is 0 or less. */
    NOT_POSITIVE,

    /** An amount that is less than 0. */
    NEGATIVE,

    /** A day of the month out of [MIN_RECURRING_DAY]..[MAX_RECURRING_DAY]. */
    OUT_OF_RANGE,

    /** One of an income's amount and the amounts it allocates, which do not add up to it. */
    ALLOCATIONS_DO_NOT_ADD_UP,
}

/** A field of a record, at [path], of [type], whose value the app does not take because of [problem]. */
data class InvalidField(val path: FieldPath, val type: FieldType, val problem: FieldProblem)

/** Why the app cannot read a record at all, so that it can only be fixed in the files themselves. */
enum class UnfixableReason {
    /** Not a JSON object: e.g. a broken line of a transactions file. */
    NOT_JSON,

    /** Of a version this app does not know, perhaps saved by a newer one. */
    UNKNOWN_VERSION,

    /** Of a type that this app does not know, or with a list or object that is not one. */
    UNKNOWN_SHAPE,
    NO_ID,

    /** Another record of its list has the same ID. */
    DUPLICATE_ID,
}

/** What is wrong with a record by itself: the fields to fix, or why it cannot be fixed. */
sealed interface RecordProblem {
    data class Unfixable(val reason: UnfixableReason) : RecordProblem

    data class InvalidFields(val fields: List<InvalidField>) : RecordProblem
}

/**
 * What is wrong with [record], saved under [key], by itself; `null` if nothing is, though it may
 * still be hidden by what it refers to.
 */
fun recordProblem(key: RecordKey, record: SavedRecord): RecordProblem? {
    val json = record.json() ?: return RecordProblem.Unfixable(UnfixableReason.NOT_JSON)
    if (record is SavedRecord.TransactionLine && (json["v"] as? JsonPrimitive)?.intOrNull != TRANSACTION_VERSION) {
        return RecordProblem.Unfixable(UnfixableReason.UNKNOWN_VERSION)
    }
    val id = (json["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: return RecordProblem.Unfixable(UnfixableReason.NO_ID)
    // A record whose ID another record took first is saved under its content.
    if (key.id != id) return RecordProblem.Unfixable(UnfixableReason.DUPLICATE_ID)
    val checker = FieldChecker()
    when (key.list) {
        TRANSACTIONS_LIST -> checker.transaction(json)
        FUNDS -> checker.fund(json)
        EXPENSE_CATEGORIES -> checker.category(json, isExpense = true)
        INCOME_CATEGORIES -> checker.category(json, isExpense = false)
        ALLOCATION_RULES -> checker.rule(json)
        RECURRING_ITEMS -> checker.recurringItem(json)
        else -> checker.unknownShape = true
    }
    return when {
        checker.unknownShape -> RecordProblem.Unfixable(UnfixableReason.UNKNOWN_SHAPE)
        checker.invalid.isEmpty() -> null
        else -> RecordProblem.InvalidFields(checker.invalid.values.toList())
    }
}

/** Checks the fields of a record against what [decodeMaster] and [decodeTransactions] take. */
private class FieldChecker {
    /** The fields found wrong, by path; the first problem found for each. */
    val invalid = LinkedHashMap<FieldPath, InvalidField>()

    /** Whether the record is not of a shape the app knows, so that its fields cannot be told. */
    var unknownShape = false

    fun invalid(path: FieldPath, type: FieldType, problem: FieldProblem) {
        invalid.getOrPut(path) { InvalidField(path, type, problem) }
    }

    /** The value of the field [name] of [json], at [path], if it is of [type]; otherwise `null`, noting why. */
    fun value(json: JsonObject, path: FieldPath, name: String, type: FieldType): JsonElement? {
        val value = json[name]
        val problem = when {
            value == null -> FieldProblem.MISSING
            !type.accepts(value) -> FieldProblem.WRONG_FORM
            else -> return value
        }
        invalid(path + name, type, problem)
        return null
    }

    fun number(json: JsonObject, path: FieldPath, name: String): Long? =
        value(json, path, name, FieldType.NUMBER)?.let { RECORD_JSON.decodeFromJsonElement(Long.serializer(), it) }

    fun integer(json: JsonObject, path: FieldPath, name: String): Int? =
        value(json, path, name, FieldType.INTEGER)?.let { RECORD_JSON.decodeFromJsonElement(Int.serializer(), it) }

    /** The value of [name], which must be positive. */
    fun amount(json: JsonObject, path: FieldPath, name: String): Long? =
        number(json, path, name)?.also { if (it <= 0) invalid(path + name, FieldType.NUMBER, FieldProblem.NOT_POSITIVE) }

    /** The objects of the list [name], each with its path, or `null` if it is not a list of objects. */
    fun objects(json: JsonObject, path: FieldPath, name: String): List<Pair<FieldPath, JsonObject>>? {
        val list = json[name] as? JsonArray
        val objects = list?.mapIndexedNotNull { index, element -> (element as? JsonObject)?.let { path + name + index to it } }
        if (objects == null || objects.size != list.size) unknownShape = true
        return objects
    }

    fun type(json: JsonObject): String? = (json["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun transaction(json: JsonObject) {
        val root = FieldPath.ROOT
        value(json, root, "transaction_date", FieldType.DATE)
        value(json, root, "memo", FieldType.TEXT)
        when (type(json)) {
            "expense" -> {
                amount(json, root, "amount")
                value(json, root, "category_id", FieldType.EXPENSE_CATEGORY)
                value(json, root, "fund_id", FieldType.FUND)
                value(json, root, "recurring_item_id", FieldType.OPTIONAL_TEXT)
            }
            "income" -> {
                val amount = amount(json, root, "amount")
                value(json, root, "category_id", FieldType.INCOME_CATEGORY)
                value(json, root, "budget_month", FieldType.MONTH)
                val allocations = objects(json, root, "allocations") ?: return
                val allocated = allocations.map { (path, allocation) ->
                    value(allocation, path, "fund_id", FieldType.FUND)
                    number(allocation, path, "amount")?.also {
                        if (it < 0) invalid(path + "amount", FieldType.NUMBER, FieldProblem.NEGATIVE)
                    }
                }
                val amountPaths = listOf(root + "amount") + allocations.map { it.first + "amount" }
                if (amount != null && allocated.none { it == null } && amountPaths.none { it in invalid } &&
                    allocated.sumOf { it!! } != amount
                ) {
                    amountPaths.forEach { invalid(it, FieldType.NUMBER, FieldProblem.ALLOCATIONS_DO_NOT_ADD_UP) }
                }
            }
            else -> unknownShape = true
        }
    }

    fun fund(json: JsonObject) {
        val root = FieldPath.ROOT
        value(json, root, "name", FieldType.TEXT)
        value(json, root, "color", FieldType.NUMBER)
        value(json, root, "opening_balance", FieldType.NUMBER)
        val goal = json["goal"] as? JsonObject
        when (goal?.let(::type)) {
            "none" -> {}
            "target" -> {
                val path = root + "goal"
                value(goal, path, "amount", FieldType.NUMBER)
                value(goal, path, "start_month", FieldType.MONTH)
                value(goal, path, "due_month", FieldType.MONTH)
            }
            else -> unknownShape = true
        }
        value(json, root, "show_in_entry", FieldType.BOOLEAN)
        value(json, root, "sort_order", FieldType.INTEGER)
        value(json, root, "archived", FieldType.BOOLEAN)
    }

    fun category(json: JsonObject, isExpense: Boolean) {
        val root = FieldPath.ROOT
        value(json, root, "name", FieldType.TEXT)
        value(json, root, "pinned", FieldType.BOOLEAN)
        value(json, root, "sort_order", FieldType.INTEGER)
        value(json, root, "archived", FieldType.BOOLEAN)
        if (isExpense) value(json, root, "default_fund_id", FieldType.FUND)
    }

    fun rule(json: JsonObject) {
        val root = FieldPath.ROOT
        value(json, root, "income_category_id", FieldType.INCOME_CATEGORY)
        objects(json, root, "lines")?.forEach { (path, line) ->
            value(line, path, "fund_id", FieldType.FUND)
            when (type(line)) {
                "fixed" -> value(line, path, "amount", FieldType.NUMBER)
                "percent" -> value(line, path, "percent", FieldType.INTEGER)
                "ask" -> {}
                else -> unknownShape = true
            }
        }
        value(json, root, "rest_fund_id", FieldType.FUND)
    }

    fun recurringItem(json: JsonObject) {
        val root = FieldPath.ROOT
        value(json, root, "name", FieldType.TEXT)
        amount(json, root, "amount")
        value(json, root, "category_id", FieldType.EXPENSE_CATEGORY)
        value(json, root, "fund_id", FieldType.FUND)
        integer(json, root, "day_of_month")?.also {
            if (it !in MIN_RECURRING_DAY..MAX_RECURRING_DAY) invalid(root + "day_of_month", FieldType.INTEGER, FieldProblem.OUT_OF_RANGE)
        }
        value(json, root, "enabled", FieldType.BOOLEAN)
        value(json, root, "last_applied_month", FieldType.OPTIONAL_MONTH)
    }
}

/** The record as a JSON object, or `null` if it is not one. */
private fun SavedRecord.json(): JsonObject? = when (this) {
    is SavedRecord.Master -> element as? JsonObject
    is SavedRecord.TransactionLine -> runCatching { RECORD_JSON.parseToJsonElement(line) as? JsonObject }.getOrNull()
}

/**
 * This record with the value at each path of [values] replaced, adding the last key if it is
 * missing. A path that leads through what is not there changes nothing.
 */
fun SavedRecord.withValues(values: Map<FieldPath, JsonElement>): SavedRecord {
    if (values.isEmpty()) return this
    fun set(element: JsonElement?, segments: List<String>, value: JsonElement): JsonElement? {
        if (segments.isEmpty()) return value
        val segment = segments.first()
        return when (element) {
            is JsonObject -> set(element[segment], segments.drop(1), value)
                ?.let { JsonObject(element + (segment to it)) } ?: element
            is JsonArray -> {
                val index = segment.toIntOrNull()?.takeIf { it in element.indices } ?: return element
                set(element[index], segments.drop(1), value)?.let { JsonArray(element.toMutableList().apply { set(index, it) }) }
            }
            else -> null
        }
    }
    val json = json() ?: return this
    val edited = values.entries.fold(json as JsonElement) { current, (path, value) -> set(current, path.segments, value) ?: current }
    return when (this) {
        is SavedRecord.Master -> SavedRecord.Master(edited)
        is SavedRecord.TransactionLine -> copy(line = RECORD_JSON.encodeToString(JsonElement.serializer(), edited))
    }
}

/**
 * A record that the user edits as text, by the path of each field: [inputs] maps it to the field's
 * type and what the user typed.
 */
class RecordEdit(key: RecordKey, original: SavedRecord, inputs: Map<FieldPath, Pair<FieldType, String>>) {
    /** The problem of each field of the record with every edit made, by path. */
    val problems: Map<FieldPath, InvalidField>

    /** The record with the edits that leave their field without a problem; the others wait. */
    val record: SavedRecord

    init {
        val values = inputs.mapValues { (_, input) -> input.first.parse(input.second) }
        problems = (recordProblem(key, original.withValues(values)) as? RecordProblem.InvalidFields)
            ?.fields?.associateBy { it.path }.orEmpty()
        record = original.withValues(values.filterKeys { it !in problems })
    }
}

/**
 * What hides the records among [records] that [concerns]: the [missing] funds and categories that
 * they refer to, the [invalid] fields of each, the ones that are [unfixable] here, and the [others],
 * hidden only by records that are themselves hidden for other reasons.
 */
class RecordsCheck(
    val records: Map<RecordKey, SavedRecord>,
    concerns: (RecordKey, SavedRecord) -> Boolean = { _, _ -> true },
) {
    /** The records concerned that the app cannot show. */
    val hidden: Map<RecordKey, SavedRecord> = unreadableRecords(records).filter { (key, record) -> concerns(key, record) }

    val missing: Map<Reference, List<RecordKey>> = missingReferences(hidden, records)

    private val problems: Map<RecordKey, RecordProblem> =
        hidden.mapNotNull { (key, record) -> recordProblem(key, record)?.let { key to it } }.toMap()

    val invalid: Map<RecordKey, List<InvalidField>> =
        problems.mapNotNull { (key, problem) -> (problem as? RecordProblem.InvalidFields)?.let { key to it.fields } }.toMap()

    val unfixable: Map<RecordKey, UnfixableReason> =
        problems.mapNotNull { (key, problem) -> (problem as? RecordProblem.Unfixable)?.let { key to it.reason } }.toMap()

    val others: Set<RecordKey> = hidden.keys - problems.keys - missing.values.flatten().toSet()

    /**
     * The funds or categories of [kind] that hidden records can refer to instead, by ID, with their
     * names, in their order: those shown and not archived.
     */
    fun candidates(kind: ReferenceKind): List<Pair<String, String>> = replacementCandidates(records, kind, hidden.keys)
}

/**
 * The funds or categories of [kind] among [records], but not [excluded] nor archived, by ID, with
 * their names, in their order.
 */
fun replacementCandidates(
    records: Map<RecordKey, SavedRecord>,
    kind: ReferenceKind,
    excluded: Set<RecordKey>,
): List<Pair<String, String>> = records
    .filterKeys { it.list == kind.list && it !in excluded }
    .mapNotNull { (key, record) ->
        when (val decoded = decodeRecord(key, record)) {
            is FundRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
            is ExpenseCategoryRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
            is IncomeCategoryRecord -> decoded.takeUnless { it.archived }?.let { Triple(it.sortOrder, it.id, it.name) }
            else -> null
        }
    }
    .sortedBy { it.first }
    .map { it.second to it.third }
