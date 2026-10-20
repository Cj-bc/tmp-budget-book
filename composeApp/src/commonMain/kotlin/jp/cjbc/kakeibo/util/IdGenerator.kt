package jp.cjbc.kakeibo.util

import jp.cjbc.kakeibo.currentDate
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Generates an ID such as `20250101-123456` from today's date and a random number. */
fun generateId(): String = "${currentDate().replace("-", "")}-${Random.nextInt(100000, 999999)}"

private const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/** Generates a ULID: 26 characters encoding [timeMillis] in the first 10, and 80 random bits. */
@OptIn(ExperimentalTime::class)
fun generateUlid(
    timeMillis: Long = Clock.System.now().toEpochMilliseconds(),
    random: Random = Random.Default,
): String {
    val chars = CharArray(26)
    var time = timeMillis
    for (i in 9 downTo 0) {
        chars[i] = CROCKFORD_BASE32[(time and 31).toInt()]
        time = time shr 5
    }
    for (i in 10 until chars.size) chars[i] = CROCKFORD_BASE32[random.nextInt(32)]
    return chars.concatToString()
}
