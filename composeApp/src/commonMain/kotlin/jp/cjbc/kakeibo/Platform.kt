package jp.cjbc.kakeibo

/** Returns the value stored for [key] in the platform's persistent storage, or `null` if absent. */
expect fun readStorage(key: String): String?

/** Stores [value] for [key] in the platform's persistent storage. */
expect fun writeStorage(key: String, value: String)

/** Returns today's date in ISO-8601 format (`YYYY-MM-DD`). */
expect fun currentDate(): String
