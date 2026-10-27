package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.SavedRecord
import jp.cjbc.kakeibo.data.files.readRecords
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.AndTreeFilter
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.filter.TreeFilter

/**
 * The names that the records of master.json with the IDs [ids], such as funds and categories, last
 * had in the history from [starts], by ID. A record no commit has is left out.
 *
 * As `git log -S` does, it looks only at the commits that changed master.json, newest first, and
 * stops once every ID is found. Each version of the file is read once, and parsed only if it holds
 * one of the IDs, so the time grows with the changes of master.json since a record was deleted.
 */
fun findNamesInHistory(repository: Repository, starts: List<AnyObjectId>, ids: Set<String>): Map<String, String> {
    if (ids.isEmpty() || starts.isEmpty()) return emptyMap()
    val found = mutableMapOf<String, String>()
    val seen = mutableSetOf<ObjectId>()
    RevWalk(repository).use { walk ->
        walk.sort(RevSort.COMMIT_TIME_DESC)
        // Only the commits whose master.json differs from their parents'.
        walk.treeFilter = AndTreeFilter.create(PathFilter.create(MASTER_FILE), TreeFilter.ANY_DIFF)
        starts.forEach { walk.markStart(walk.parseCommit(it)) }
        for (commit in walk) {
            val blob = TreeWalk.forPath(repository, MASTER_FILE, commit.tree)?.use { it.getObjectId(0) } ?: continue
            if (!seen.add(blob)) continue
            val content = repository.open(blob).bytes.decodeToString()
            val wanted = ids - found.keys
            if (wanted.none { it in content }) continue
            val records = runCatching { readRecords(mapOf(MASTER_FILE to content)) }.getOrNull() ?: continue
            records.forEach { (key, record) ->
                if (key.id !in wanted) return@forEach
                val element = (record as? SavedRecord.Master)?.element as? JsonObject
                val name = (element?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
                found[key.id] = name
            }
            if (found.keys.containsAll(ids)) break
        }
    }
    return found
}
