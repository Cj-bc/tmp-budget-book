package jp.cjbc.kakeibo.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {
    @Test
    fun ulidsEncodeTheTimeFirstInCrockfordBase32() {
        val ulid = generateUlid(timeMillis = 1_469_918_176_385, random = Random(0))
        assertEquals(26, ulid.length)
        // The example time of the ULID specification.
        assertEquals("01ARYZ6S41", ulid.take(10))
        assertTrue(ulid.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
    }

    @Test
    fun ulidsSortByTimeAndDiffer() {
        assertTrue(generateUlid(timeMillis = 1_000) < generateUlid(timeMillis = 2_000))
        assertNotEquals(generateUlid(timeMillis = 1_000), generateUlid(timeMillis = 1_000))
    }

    @Test
    fun ulidsOfAKeyAreTheSameEveryTime() {
        val ulid = ulidOf(timeMillis = 1_469_918_176_385, key = "rent 2026-10")
        // Fixed, so that every version of the app makes the same ULID.
        assertEquals("01ARYZ6S41A67559Z5DF03J9AA", ulid)
        assertTrue(isUlid(ulid))
        assertNotEquals(ulid, ulidOf(timeMillis = 1_469_918_176_385, key = "rent 2026-11"))
        assertNotEquals(ulid.drop(10), ulidOf(timeMillis = 1_000, key = "rent 2026-10x").drop(10))
    }
}
