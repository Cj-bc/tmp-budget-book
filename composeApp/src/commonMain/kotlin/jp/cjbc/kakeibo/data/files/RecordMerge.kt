package jp.cjbc.kakeibo.data.files

/**
 * A record that both sides changed differently since their common ancestor, or that one side
 * changed and the other deleted. A missing version means the record is not there.
 */
data class Conflict<K, V>(val key: K, val base: V?, val ours: V?, val theirs: V?)

/** The records merged without conflict, and the [conflicts] left for the user to choose. */
class Merge<K, V>(val merged: Map<K, V>, val conflicts: List<Conflict<K, V>>) {
    /** All the records, each conflict resolved by [choose]: the version to keep, or `null` for none. */
    fun resolve(choose: (Conflict<K, V>) -> V?): Map<K, V> =
        merged + conflicts.mapNotNull { conflict -> choose(conflict)?.let { conflict.key to it } }
}

/**
 * Merges [ours] and [theirs], records by key, from their common ancestor [base]. A record that
 * only one side added, changed or deleted takes that side's version, and one that both changed
 * the same way takes it too; any other change of both sides is a conflict.
 */
fun <K, V> mergeRecords(base: Map<K, V>, ours: Map<K, V>, theirs: Map<K, V>): Merge<K, V> {
    val merged = LinkedHashMap<K, V>()
    val conflicts = mutableListOf<Conflict<K, V>>()
    (ours.keys + theirs.keys + base.keys).forEach { key ->
        val ancestor = base[key]
        val mine = ours[key]
        val other = theirs[key]
        val result = when {
            mine == other || other == ancestor -> mine
            mine == ancestor -> other
            else -> {
                conflicts += Conflict(key, ancestor, mine, other)
                null
            }
        }
        if (result != null) merged[key] = result
    }
    return Merge(merged, conflicts)
}

/**
 * Merges the data files [ours] and [theirs], by path, from their common ancestor [base], record by
 * record. Lay out the resolved records with [layoutRecords]. Without a common ancestor, pass no
 * files as [base]: every record either side has is then added by that side.
 *
 * @throws UnreadableMasterException if a master.json is not JSON or of an unknown version.
 */
fun mergeFiles(
    base: Map<String, String>,
    ours: Map<String, String>,
    theirs: Map<String, String>,
): Merge<RecordKey, SavedRecord> = mergeRecords(readRecords(base), readRecords(ours), readRecords(theirs))
