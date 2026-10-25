package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.model.MAX_RECURRING_DAY
import jp.cjbc.kakeibo.model.MIN_RECURRING_DAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Why a saved record cannot be shown, field by field, so that the user can fix the values that
// are wrong. The fields are those of the version 1 records in Records.kt.

/** What a field of a record holds. IDs are not for the user to edit. */
enum class FieldKind { ID, OPTIONAL_ID, TEXT, WHOLE_NUMBER, YES_NO, DATE, MONTH, OPTIONAL_MONTH }

/** What is wrong with the value of a field. */
enum class FieldIssue { MISSING, WRONG_TYPE, NOT_POSITIVE, NEGATIVE, DAY_OUT_OF_RANGE, ALLOCATIONS_DO_NOT_ADD_UP }

/** The field at [path] of a record, of [kind], is wrong for [issue]. See [setField] for paths. */
data class FieldProblem(val path: String, val kind: FieldKind, val issue: FieldIssue)

/** Why the app cannot show a record that refers to nothing missing. */
sealed interface RecordTrouble {
    /** Fields have the wrong values: [problems], fixed by editing the [editable] fields. */
    data class Fields(val problems: List<FieldProblem>, val editable: List<Pair<String, FieldKind>>) : RecordTrouble

    /** It refers to the records [on], which cannot be shown; fixing them shows it too. */
    data class Dependent(val on: List<RecordKey>) : RecordTrouble

    /** It is not a JSON object, or of a version this app does not know. */
    data object Unreadable : RecordTrouble

    /** Another record of its list has its ID. */
    data object DuplicateId : RecordTrouble

    /** It is wrong in a way the app cannot point at or fix, e.g. in its structure or its IDs. */
    data object Malformed : RecordTrouble
}

/**
 * Why [record], saved under [key] among [records] and one of the [hidden] ones, cannot be shown,
 * when it refers to no missing record (see [missingReferences]).
 */
fun diagnose(
    key: RecordKey,
    record: SavedRecord,
    records: Map<RecordKey, SavedRecord>,
    hidden: Map<RecordKey, SavedRecord>,
): RecordTrouble {
    val element = recordObject(record) ?: return RecordTrouble.Unreadable
    if (record is SavedRecord.TransactionLine && (element["v"] as? JsonPrimitive)?.intOrNull != TRANSACTION_VERSION) {
        return RecordTrouble.Unreadable
    }
    val id = (element["id"] as? JsonPrimitive)?.contentOrNull
    if (id != null && key.id != id && RecordKey(key.list, id) in records) return RecordTrouble.DuplicateId
    val problems = fieldProblems(key.list, element) ?: return RecordTrouble.Malformed
    if (problems.isEmpty()) {
        val on = references(key, record).map { RecordKey(it.kind.list, it.id) }.filter { it in hidden }
        return if (on.isEmpty()) RecordTrouble.Malformed else RecordTrouble.Dependent(on)
    }
    if (problems.any { it.kind == FieldKind.ID || it.kind == FieldKind.OPTIONAL_ID }) return RecordTrouble.Malformed
    val editable = problems.flatMap { problem ->
        if (problem.issue == FieldIssue.ALLOCATIONS_DO_NOT_ADD_UP) {
            val allocations = (element["allocations"] as JsonArray).indices.map { "allocations.$it.amount" to FieldKind.WHOLE_NUMBER }
            listOf("amount" to FieldKind.WHOLE_NUMBER) + allocations
        } else {
            listOf(problem.path to problem.kind)
        }
    }.distinct()
    return RecordTrouble.Fields(problems, editable)
}

/** The value of the field at [path] of [record] as the user edits it: empty if missing or null. */
fun fieldText(record: SavedRecord, path: String): String {
    var value: JsonElement? = recordObject(record)
    path.split('.').forEach { step ->
        value = when (val current = value) {
            is JsonObject -> current[step]
            is JsonArray -> step.toIntOrNull()?.let { current.getOrNull(it) }
            else -> null
        }
    }
    return when (val found = value) {
        null, JsonNull -> ""
        is JsonPrimitive -> found.content
        else -> found.toString()
    }
}

/** [text] as the value of a field of this kind, or `null` if it is not one. IDs are not edited. */
fun FieldKind.parse(text: String): JsonElement? {
    val trimmed = text.trim()
    return when (this) {
        FieldKind.ID, FieldKind.OPTIONAL_ID -> null
        FieldKind.TEXT -> JsonPrimitive(text)
        FieldKind.WHOLE_NUMBER -> trimmed.toLongOrNull()?.let(::JsonPrimitive)
        FieldKind.YES_NO -> trimmed.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        FieldKind.DATE -> trimmed.takeIf { isDate(it) }?.let(::JsonPrimitive)
        FieldKind.MONTH -> trimmed.takeIf { isMonth(it) }?.let(::JsonPrimitive)
        FieldKind.OPTIONAL_MONTH -> if (trimmed.isEmpty()) JsonNull else trimmed.takeIf { isMonth(it) }?.let(::JsonPrimitive)
    }
}

/**
 * [record] with the field at [path] set to [value]. A path names the keys of objects and the
 * indices of arrays, joined by dots: `allocations.1.amount` is the amount of the second allocation.
 * A record that is not a JSON object stays as it is.
 */
fun setField(record: SavedRecord, path: String, value: JsonElement): SavedRecord {
    fun set(element: JsonElement, steps: List<String>): JsonElement {
        val step = steps.first()
        val inner = { current: JsonElement? -> if (steps.size == 1) value else set(current ?: JsonObject(emptyMap()), steps.drop(1)) }
        return when (element) {
            is JsonObject -> JsonObject(element + (step to inner(element[step])))
            is JsonArray -> step.toIntOrNull()?.takeIf { it in element.indices }
                ?.let { index -> JsonArray(element.mapIndexed { i, item -> if (i == index) inner(item) else item }) }
                ?: element
            else -> element
        }
    }
    val element = recordObject(record) ?: return record
    val updated = set(element, path.split('.'))
    return when (record) {
        is SavedRecord.Master -> SavedRecord.Master(updated)
        is SavedRecord.TransactionLine -> record.copy(line = RECORD_JSON.encodeToString(JsonElement.serializer(), updated))
    }
}

private fun recordObject(record: SavedRecord): JsonObject? = when (record) {
    is SavedRecord.Master -> record.element as? JsonObject
    is SavedRecord.TransactionLine -> runCatching { RECORD_JSON.parseToJsonElement(record.line) as? JsonObject }.getOrNull()
}

private fun isDate(text: String) = runCatching { LocalDate.parse(text) }.isSuccess

private fun isMonth(text: String) = runCatching { YearMonth.parse(text) }.isSuccess

/** A field of a record: its [name], [kind], whether it holds an [int], and what else it must be. */
private class Spec(
    val name: String,
    val kind: FieldKind,
    val int: Boolean = false,
    val check: (JsonPrimitive) -> FieldIssue? = { null },
)

private val positive = { value: JsonPrimitive -> if (value.longOrNull!! <= 0) FieldIssue.NOT_POSITIVE else null }
private val notNegative = { value: JsonPrimitive -> if (value.longOrNull!! < 0) FieldIssue.NEGATIVE else null }
private val day = { value: JsonPrimitive -> if (value.intOrNull!! !in MIN_RECURRING_DAY..MAX_RECURRING_DAY) FieldIssue.DAY_OUT_OF_RANGE else null }

private val ID = Spec("id", FieldKind.ID)
private val NAME = Spec("name", FieldKind.TEXT)
private val SORT_ORDER = Spec("sort_order", FieldKind.WHOLE_NUMBER, int = true)
private val PINNED = Spec("pinned", FieldKind.YES_NO)
private val ARCHIVED = Spec("archived", FieldKind.YES_NO)

private val FUND = listOf(
    ID, NAME, Spec("color", FieldKind.WHOLE_NUMBER), Spec("opening_balance", FieldKind.WHOLE_NUMBER),
    Spec("show_in_entry", FieldKind.YES_NO), SORT_ORDER, ARCHIVED,
)
private val GOAL_TARGET = listOf(
    Spec("amount", FieldKind.WHOLE_NUMBER), Spec("start_month", FieldKind.MONTH), Spec("due_month", FieldKind.MONTH),
)
private val EXPENSE_CATEGORY = listOf(ID, NAME, PINNED, SORT_ORDER, ARCHIVED, Spec("default_fund_id", FieldKind.ID))
private val INCOME_CATEGORY = listOf(ID, NAME, PINNED, SORT_ORDER, ARCHIVED)
private val RULE = listOf(ID, Spec("income_category_id", FieldKind.ID), Spec("rest_fund_id", FieldKind.ID))
private val RULE_LINE_FUND = Spec("fund_id", FieldKind.ID)
private val RECURRING_ITEM = listOf(
    ID, NAME, Spec("amount", FieldKind.WHOLE_NUMBER, check = positive), Spec("category_id", FieldKind.ID),
    Spec("fund_id", FieldKind.ID), Spec("day_of_month", FieldKind.WHOLE_NUMBER, int = true, check = day),
    Spec("enabled", FieldKind.YES_NO), Spec("last_applied_month", FieldKind.OPTIONAL_MONTH),
)
private val TRANSACTION = listOf(
    ID, Spec("transaction_date", FieldKind.DATE), Spec("amount", FieldKind.WHOLE_NUMBER, check = positive),
    Spec("category_id", FieldKind.ID), Spec("memo", FieldKind.TEXT),
)
private val EXPENSE = TRANSACTION + listOf(Spec("fund_id", FieldKind.ID), Spec("recurring_item_id", FieldKind.OPTIONAL_ID))
private val INCOME = TRANSACTION + Spec("budget_month", FieldKind.MONTH)
private val ALLOCATION = listOf(Spec("fund_id", FieldKind.ID), Spec("amount", FieldKind.WHOLE_NUMBER, check = notNegative))

private fun FieldKind.accepts(value: JsonElement): Boolean {
    val primitive = value as? JsonPrimitive ?: return false
    val string = primitive.isString
    return when (this) {
        FieldKind.ID, FieldKind.TEXT -> string
        FieldKind.OPTIONAL_ID -> string || primitive is JsonNull
        FieldKind.WHOLE_NUMBER -> !string && primitive.longOrNull != null
        FieldKind.YES_NO -> !string && primitive.booleanOrNull != null
        FieldKind.DATE -> string && isDate(primitive.content)
        FieldKind.MONTH -> string && isMonth(primitive.content)
        FieldKind.OPTIONAL_MONTH -> primitive is JsonNull || (string && isMonth(primitive.content))
    }
}

/** The problems of the fields of [element] by [specs], their paths starting with [prefix]. */
private fun problemsOf(element: JsonObject, specs: List<Spec>, prefix: String = ""): List<FieldProblem> = specs.mapNotNull { spec ->
    val value = element[spec.name]
    val issue = when {
        value == null -> FieldIssue.MISSING
        !spec.kind.accepts(value) || (spec.int && (value as JsonPrimitive).intOrNull == null) -> FieldIssue.WRONG_TYPE
        else -> spec.check(value as JsonPrimitive)
    }
    issue?.let { FieldProblem(prefix + spec.name, spec.kind, it) }
}

/** The objects of the array [name] of [element], or `null` if it is not an array of objects. */
private fun objects(element: JsonObject, name: String): List<JsonObject>? =
    (element[name] as? JsonArray)?.map { it as? JsonObject ?: return null }

/** The problems of the fields of a record of [list], or `null` if its structure is wrong. */
private fun fieldProblems(list: String, element: JsonObject): List<FieldProblem>? = when (list) {
    FUNDS -> {
        val goal = element["goal"] as? JsonObject ?: return null
        val goalProblems = when ((goal["type"] as? JsonPrimitive)?.contentOrNull) {
            "none" -> emptyList()
            "target" -> problemsOf(goal, GOAL_TARGET, "goal.")
            else -> return null
        }
        problemsOf(element, FUND) + goalProblems
    }
    EXPENSE_CATEGORIES -> problemsOf(element, EXPENSE_CATEGORY)
    INCOME_CATEGORIES -> problemsOf(element, INCOME_CATEGORY)
    ALLOCATION_RULES -> {
        val lines = objects(element, "lines") ?: return null
        problemsOf(element, RULE) + lines.flatMapIndexed { index, line ->
            val specs = when ((line["type"] as? JsonPrimitive)?.contentOrNull) {
                "fixed" -> listOf(RULE_LINE_FUND, Spec("amount", FieldKind.WHOLE_NUMBER))
                "percent" -> listOf(RULE_LINE_FUND, Spec("percent", FieldKind.WHOLE_NUMBER, int = true))
                "ask" -> listOf(RULE_LINE_FUND)
                else -> return null
            }
            problemsOf(line, specs, "lines.$index.")
        }
    }
    RECURRING_ITEMS -> problemsOf(element, RECURRING_ITEM)
    TRANSACTIONS_LIST -> when ((element["type"] as? JsonPrimitive)?.contentOrNull) {
        "expense" -> problemsOf(element, EXPENSE)
        "income" -> {
            val allocations = objects(element, "allocations") ?: return null
            val problems = problemsOf(element, INCOME) +
                allocations.flatMapIndexed { index, allocation -> problemsOf(allocation, ALLOCATION, "allocations.$index.") }
            // The allocations add up to the amount, which is only worth checking when both are numbers.
            val amounts = allocations.map { (it["amount"] as? JsonPrimitive)?.longOrNull }
            val amount = (element["amount"] as? JsonPrimitive)?.longOrNull
            if (problems.isEmpty() && amount != null && amounts.all { it != null } && amounts.sumOf { it!! } != amount) {
                problems + FieldProblem("amount", FieldKind.WHOLE_NUMBER, FieldIssue.ALLOCATIONS_DO_NOT_ADD_UP)
            } else {
                problems
            }
        }
        else -> null
    }
    else -> null
}
