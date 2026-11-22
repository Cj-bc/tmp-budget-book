package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.InMemoryFiles
import jp.cjbc.kakeibo.InMemoryStorage
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private const val OLD_FUND_ID = "fund-food"

private fun openLedger(files: InMemoryFiles = InMemoryFiles()): Ledger =
    runSuspend { Ledger.open(files, InMemoryStorage()) { "Preset ${it.legacyId}" } }

private fun Ledger.importRaw(raw: String): ImportResult =
    runSuspend { importBackup(raw) { "Preset ${it.legacyId}" } }

/** A transaction line of an unknown version that refers to the fund [OLD_FUND_ID]. */
private const val UNREADABLE_TRANSACTION =
    """{"v":99,"id":"01ARZ3NDEKTSV4RRFFQ69G5F98","transaction_date":"2026-10-01","fund_id":"$OLD_FUND_ID"}"""

/** A recurring item that cannot be read (no other fields), referring to the fund [OLD_FUND_ID]. */
private const val UNREADABLE_RECURRING_ITEM = """{"id":"01ARZ3NDEKTSV4RRFFQ69G5F97","fund_id":"$OLD_FUND_ID","day_of_month":"x"}"""

/** An expense category that cannot be read (no other fields), whose default fund is [OLD_FUND_ID]. */
private const val UNREADABLE_CATEGORY = """{"id":"01ARZ3NDEKTSV4RRFFQ69G5F96","default_fund_id":"$OLD_FUND_ID"}"""

/**
 * A version 4 backup in which the preset food fund has the ID [OLD_FUND_ID] (not a ULID), holding
 * unreadable records that refer to it. Returns the backup and the name of that fund.
 */
private fun backupWithOldFundId(): Pair<String, String> {
    val source = openLedger()
    val fund = source.data.funds.single { it.name == "Preset food" }
    val renamed = source.exportBackup().replace(fund.id.value, OLD_FUND_ID)
    check(OLD_FUND_ID in renamed)
    val unreadable = Json.parseToJsonElement(
        """
        {
          "master": {
            "recurring_items": [$UNREADABLE_RECURRING_ITEM],
            "expense_categories": [$UNREADABLE_CATEGORY]
          },
          "transactions": {"transactions/2026-10.jsonl": [${Json.encodeToString(kotlinx.serialization.serializer<String>(), UNREADABLE_TRANSACTION)}]}
        }
        """,
    )
    val backup = Json.parseToJsonElement(renamed).jsonObject
    return JsonObject(backup + ("unreadable" to unreadable)).toString() to fund.name
}

class BackupUnreadableReferencesTest {
    private fun importedLedger(): Triple<Ledger, InMemoryFiles, FundId> {
        val (backup, fundName) = backupWithOldFundId()
        val files = InMemoryFiles()
        val ledger = openLedger(files)
        assertIs<ImportResult.Success>(ledger.importRaw(backup))
        val fund = ledger.data.funds.single { it.name == fundName }
        assertTrue(fund.id.value != OLD_FUND_ID, "the fund got a new ID")
        return Triple(ledger, files, fund.id)
    }

    @Test
    fun unreadableTransactionsFollowTheFundsGivenNewIds() {
        val (ledger, files, newId) = importedLedger()
        val transactions = files.files.filterKeys { it.startsWith("transactions/") }.values.joinToString("\n")
        assertTrue("\"v\":99" in transactions.replace(" ", ""), "the unreadable line is still saved:\n$transactions")
        assertTrue(OLD_FUND_ID !in transactions, "the saved transactions still refer to $OLD_FUND_ID:\n$transactions")
        assertTrue(newId.value in transactions, "the saved transactions should refer to ${newId.value}:\n$transactions")

        val reopened = openLedger(files)
        assertEquals(ledger.data, reopened.data)
        val again = files.files.filterKeys { it.startsWith("transactions/") }.values.joinToString("\n")
        assertTrue(OLD_FUND_ID !in again && newId.value in again, "still so after reopening:\n$again")
    }

    @Test
    fun unreadableMasterRecordsFollowTheFundsGivenNewIds() {
        val (_, files, newId) = importedLedger()
        val master = Json.parseToJsonElement(files.files.getValue("master.json")).jsonObject
        fun unreadableElement(list: String, id: String): JsonObject? =
            (master[list] as JsonArray).map { it.jsonObject }.singleOrNull { it["id"]?.toString() == "\"$id\"" }
        val item = unreadableElement("recurring_items", "01ARZ3NDEKTSV4RRFFQ69G5F97")
        val category = unreadableElement("expense_categories", "01ARZ3NDEKTSV4RRFFQ69G5F96")
        assertTrue(item != null && category != null, "the unreadable master records are still saved")
        assertEquals("\"${newId.value}\"", item["fund_id"].toString(), "recurring item refers to the new fund ID")
        assertEquals("\"${newId.value}\"", category["default_fund_id"].toString(), "category refers to the new fund ID")
        assertTrue(OLD_FUND_ID !in files.files.getValue("master.json"), "master.json still has $OLD_FUND_ID")
    }
}
