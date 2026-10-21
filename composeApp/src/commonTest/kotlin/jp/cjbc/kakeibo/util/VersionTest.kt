package jp.cjbc.kakeibo.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionTest {
    private val hash = "a968c67d0e5f1b2c3d4e5f60718293a4b5c6d7e8"

    @Test
    fun shortensHashes() {
        assertEquals("a968c67", shortCommit(hash))
        assertEquals("a968c67-dirty", shortCommit("$hash-dirty"))
        assertEquals("unknown", shortCommit("unknown"))
    }

    @Test
    fun linksToTheCommit() {
        assertEquals("$REPOSITORY_URL/commit/$hash", commitUrl(hash))
        assertEquals("$REPOSITORY_URL/commit/$hash", commitUrl("$hash-dirty"))
        assertNull(commitUrl("unknown"))
    }

    @Test
    fun formatsTheBuildTimeToTheMinute() {
        assertEquals("2026-10-21 12:34 UTC", formatBuildTime("2026-10-21T12:34:00Z"))
        assertEquals("2026-10-21 12:34 UTC", formatBuildTime("2026-10-21T12:34Z"))
        assertEquals("yesterday", formatBuildTime("yesterday"))
    }
}
