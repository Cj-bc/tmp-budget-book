package jp.cjbc.kakeibo.util

/** The repository the app is built from. */
const val REPOSITORY_URL = "https://github.com/Cj-bc/tmp-budget-book"

private const val DIRTY_SUFFIX = "-dirty"

/** Shortens a commit hash as git does, e.g. `a968c67`, keeping a `-dirty` suffix. */
fun shortCommit(commit: String): String {
    val hash = commit.removeSuffix(DIRTY_SUFFIX)
    return if (isHash(hash)) hash.take(7) + commit.removePrefix(hash) else commit
}

/**
 * The page of [commit] in [REPOSITORY_URL], or `null` if it is not a commit hash. A dirty commit
 * links to the commit its changes were made on.
 */
fun commitUrl(commit: String): String? =
    commit.removeSuffix(DIRTY_SUFFIX).takeIf(::isHash)?.let { "$REPOSITORY_URL/commit/$it" }

/** Formats an ISO-8601 instant such as `2026-10-21T12:34:00Z` as `2026-10-21 12:34 UTC`. */
fun formatBuildTime(instant: String): String =
    Regex("""(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(:\d{2}(\.\d+)?)?Z""").matchEntire(instant)
        ?.let { "${it.groupValues[1]} ${it.groupValues[2]} UTC" }
        ?: instant

private fun isHash(text: String) = text.length == 40 && text.all { it in '0'..'9' || it in 'a'..'f' }
