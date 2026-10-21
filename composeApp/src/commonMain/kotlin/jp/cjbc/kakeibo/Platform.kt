package jp.cjbc.kakeibo

/** Returns the value stored for [key] in the platform's persistent storage, or `null` if absent. */
expect fun readStorage(key: String): String?

/** Stores [value] for [key] in the platform's persistent storage. */
expect fun writeStorage(key: String, value: String)

/**
 * Returns the contents of the file at [path] in the app's own data directory, or `null` if there
 * is none. [path] is relative, with `/` between directories. Every write made before is seen.
 */
expect suspend fun readFile(path: String): String?

/** Returns the names of the files directly in [directory] of the app's data directory. */
expect suspend fun listFiles(directory: String): List<String>

/**
 * Replaces the contents of the file at [path] with [content], creating its directories. It may
 * finish later, but writes and deletes happen in the order they are made.
 */
expect fun writeFile(path: String, content: String)

/** Deletes the file at [path] if there is one, in order with [writeFile]. */
expect fun deleteFile(path: String)

/**
 * Records the files as the writes and deletes made before left them as one commit by the author
 * [authorName] <[authorEmail]>, if anything changed. Does nothing where the files are not under
 * version control.
 */
expect fun commitFiles(message: String, authorName: String, authorEmail: String)

/** Returns today's date in ISO-8601 format (`YYYY-MM-DD`). */
expect fun currentDate(): String
