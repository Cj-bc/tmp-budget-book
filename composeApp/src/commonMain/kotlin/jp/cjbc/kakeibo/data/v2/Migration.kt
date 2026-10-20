package jp.cjbc.kakeibo.data.v2

import jp.cjbc.kakeibo.model.CategoryPreset
import jp.cjbc.kakeibo.util.generateUlid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts version 2 data, given as the JSON of its [jp.cjbc.kakeibo.model.LedgerData], to the
 * current format.
 *
 * Version 2 categories had no names: a preset was shown by its label and any other category by its
 * ID. Each category is given that name, with [presetName] naming the presets. Their IDs were only
 * unique among the categories of the same kind, so each category also gets a new ID, and
 * everything referring to it is changed to that ID.
 */
suspend fun migrateFromV2(data: JsonObject, presetName: suspend (CategoryPreset) -> String): JsonObject {
    val categories = data["categories"]?.jsonArray.orEmpty().map { it.jsonObject }
    // The new ID of each category by its kind ("expense" or "income") and old ID.
    val newIds = categories.associate { (it.string("type") to it.string("id")) to JsonPrimitive(generateUlid()) }
    // A reference to a category that does not exist is kept, and fails validation.
    fun JsonObject.withNewId(key: String, kind: String): JsonObject =
        JsonObject(this + (key to (newIds[kind to string(key)] ?: getValue(key))))

    val named = categories.map { category ->
        val id = category.string("id")
        val name = CategoryPreset.fromLegacyId(id)?.let { presetName(it) } ?: id
        JsonObject(category.withNewId("id", category.string("type")) + ("name" to JsonPrimitive(name)))
    }
    fun list(key: String, migrate: (JsonObject) -> JsonObject): JsonArray =
        JsonArray(data[key]?.jsonArray.orEmpty().map { migrate(it.jsonObject) })
    return JsonObject(
        data + mapOf(
            "categories" to JsonArray(named),
            "transactions" to list("transactions") { it.withNewId("categoryId", it.string("type")) },
            "recurringItems" to list("recurringItems") { it.withNewId("categoryId", "expense") },
            "allocationRules" to list("allocationRules") { it.withNewId("incomeCategoryId", "income") },
        ),
    )
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
