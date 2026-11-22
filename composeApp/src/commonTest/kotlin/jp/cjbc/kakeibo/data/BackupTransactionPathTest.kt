package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private fun openLedger(files: InMemoryFiles = InMemoryFiles()): Ledger =
    runSuspend { Ledger.open(files, InMemoryStorage()) { "Preset ${it.legacyId}" } }

private fun Ledger.importRaw(raw: String): ImportResult =
    runSuspend { importBackup(raw) { "Preset ${it.legacyId}" } }

private const val UNREADABLE_LINE = """{"v":99,"id":"01ARZ3NDEKTSV4RRFFQ69G5F98"}"""

/** Backup of an empty ledger with [path] holding one unreadable line, built by hand. */
private fun backupWithUnreadable(path: String): String {
    val backup = Json.parseToJsonElement(openLedger().exportBackup()).jsonObject
    val unreadable = JsonObject(
        mapOf("transactions" to JsonObject(mapOf(path to JsonArray(listOf(JsonPrimitive(UNREADABLE_LINE)))))),
    )
    return JsonObject(backup + ("unreadable" to unreadable)).toString()
}

class BackupTransactionPathTest {
    private fun roundTrip(path: String) {
        val files = InMemoryFiles()
        openLedger(files)
        files.files[path] = UNREADABLE_LINE + "\n"
        val ledger = openLedger(files)
        val backup = ledger.exportBackup()
        assertTrue(UNREADABLE_LINE.replace("\"", "\\\"") in backup, "export keeps the line of $path")

        val otherFiles = InMemoryFiles()
        val other = openLedger(otherFiles)
        val result = other.importRaw(backup)
        assertIs<ImportResult.Success>(result, "a backup the app exported must restore ($path)")
        assertTrue(otherFiles.files[path].orEmpty().contains(UNREADABLE_LINE), "line restored to $path")
    }

    @Test
    fun backupsKeepUnreadableLinesOfFilesNamedWithASpace() = roundTrip("transactions/2025-01 copy.jsonl")

    @Test
    fun backupsKeepUnreadableLinesOfFilesWithNonAsciiNames() = roundTrip("transactions/家計.jsonl")

    @Test
    fun separatorsAndDotSegmentsInTransactionPathsAreStillRejected() {
        val ledger = openLedger()
        val before = ledger.data
        listOf(
            "transactions/a/b.jsonl",
            "transactions/a\b.jsonl",
            "transactions/a\\b.jsonl",
            "transactions/..\\x.jsonl",
            "transactions/../x.jsonl",
            "../x.jsonl",
            "transactions/..jsonl",
            "transactions/./x.jsonl",
            "transactions/x.jsonl/../y.jsonl",
        ).forEach { path ->
            assertEquals(ImportResult.Failure, ledger.importRaw(backupWithUnreadable(path)), path)
        }
        assertEquals(before, ledger.data)
    }
}
