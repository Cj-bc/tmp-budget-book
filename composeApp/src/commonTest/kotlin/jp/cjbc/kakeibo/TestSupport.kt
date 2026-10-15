package jp.cjbc.kakeibo

import jp.cjbc.kakeibo.data.KeyValueStorage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Runs [block], which must not actually suspend, and returns its result. */
fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "the block suspended" }.getOrThrow()
}

class InMemoryStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }
}
