package jp.cjbc.kakeibo.util

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L

/** Generates a ULID: 26 characters encoding [timeMillis] in the first 10, and 80 random bits. */
@OptIn(ExperimentalTime::class)
fun generateUlid(
    timeMillis: Long = Clock.System.now().toEpochMilliseconds(),
    random: Random = Random.Default,
): String = ulid(timeMillis) { random.nextInt(32) }

/**
 * A ULID of [timeMillis] whose other 80 bits are a hash of [key] instead of random: the same
 * arguments give the same ULID on any device, and with any version of the app.
 */
fun ulidOf(timeMillis: Long, key: String): String {
    val high = fnv1a(key, FNV_OFFSET_BASIS)
    val low = fnv1a(key, high)
    // 40 bits from each hash.
    return ulid(timeMillis) { i -> ((if (i < 8) high else low) ushr (5 * (i % 8))).toInt() and 31 }
}

/** Whether [value] is a ULID as [generateUlid] makes it: 26 characters, the first at most `7`. */
fun isUlid(value: String): Boolean =
    value.length == 26 && value[0] in '0'..'7' && value.all { it in CROCKFORD_BASE32 }

/** A ULID of [timeMillis] whose [i]th character after the time is the digit [digit] gives. */
private inline fun ulid(timeMillis: Long, digit: (i: Int) -> Int): String {
    val chars = CharArray(26)
    var time = timeMillis
    for (i in 9 downTo 0) {
        chars[i] = CROCKFORD_BASE32[(time and 31).toInt()]
        time = time shr 5
    }
    for (i in 10 until chars.size) chars[i] = CROCKFORD_BASE32[digit(i - 10)]
    return chars.concatToString()
}

/** The 64-bit FNV-1a hash of the characters of [text], starting from [basis]. */
private fun fnv1a(text: String, basis: Long): Long =
    text.fold(basis) { hash, char -> (hash xor char.code.toLong()) * FNV_PRIME }
