package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.data.files.RecordKey
import jp.cjbc.kakeibo.data.files.RecordTrouble
import jp.cjbc.kakeibo.data.files.Reference
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.diagnose
import jp.cjbc.kakeibo.data.files.missingReferences
import jp.cjbc.kakeibo.data.files.replaceReferences
import jp.cjbc.kakeibo.data.files.setField
import jp.cjbc.kakeibo.data.files.unreadableRecords
import kotlinx.serialization.json.JsonElement

/**
 * The saved [records] that the app cannot show, and why: the funds and categories that are
 * [missing], each with the records it hides, and the [troubles] of the others.
 */
class DataCheck(val records: Map<RecordKey, SavedRecord>) {
    val hidden: Map<RecordKey, SavedRecord> = unreadableRecords(records)

    val missing: Map<Reference, List<RecordKey>> = missingReferences(hidden, records)

    val troubles: Map<RecordKey, RecordTrouble> = missing.values.flatten().toSet().let { byMissing ->
        (hidden - byMissing).mapValues { (key, record) -> diagnose(key, record, records, hidden) }
    }

    /** Whether every record can be shown. */
    val isClean: Boolean get() = hidden.isEmpty()

    /**
     * The [records] with the references to the missing funds and categories that [replacements]
     * maps made to others, and the fields of the records that [edits] maps set by path.
     */
    fun fixed(
        replacements: Map<String, String>,
        edits: Map<RecordKey, Map<String, JsonElement>>,
    ): Map<RecordKey, SavedRecord> = replaceReferences(records, replacements).mapValues { (key, record) ->
        edits[key].orEmpty().entries.fold(record) { edited, (path, value) -> setField(edited, path, value) }
    }
}
