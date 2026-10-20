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
}
