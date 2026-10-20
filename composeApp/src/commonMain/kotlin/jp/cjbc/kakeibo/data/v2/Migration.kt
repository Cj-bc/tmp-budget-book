package jp.cjbc.kakeibo.data.v2

import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.CategoryPreset
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
 * ID. Each category is given that name, with [presetName] naming the presets.
 */
suspend fun migrateFromV2(data: JsonObject, presetName: suspend (CategoryPreset) -> String): JsonObject {
    val categories = data["categories"]?.jsonArray ?: return data
    val named = categories.map { element ->
        val category = element.jsonObject
        val id = category.getValue("id").jsonPrimitive.content
        val name = CategoryPreset.fromId(CategoryId(id))?.let { presetName(it) } ?: id
        JsonObject(category + ("name" to JsonPrimitive(name)))
    }
    return JsonObject(data + ("categories" to JsonArray(named)))
}
