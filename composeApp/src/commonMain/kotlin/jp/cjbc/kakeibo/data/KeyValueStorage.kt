package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.readStorage
import jp.cjbc.kakeibo.writeStorage

/** A string key-value store that [Ledger] persists its data in. */
interface KeyValueStorage {
    fun read(key: String): String?

    fun write(key: String, value: String)
}

/** The platform's persistent storage. */
object PlatformStorage : KeyValueStorage {
    override fun read(key: String): String? = readStorage(key)

    override fun write(key: String, value: String) = writeStorage(key, value)
}
