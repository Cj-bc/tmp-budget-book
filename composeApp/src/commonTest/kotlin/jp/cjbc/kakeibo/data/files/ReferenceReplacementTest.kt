package jp.cjbc.kakeibo.data.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

private const val OLD = "01ARZ3NDEKTSV4RRFFQ69G5F01"
private const val NEW = "01ARZ3NDEKTSV4RRFFQ69G5F02"
private const val OCTOBER = "transactions/2026-10.jsonl"

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

private fun master(list: String, id: String, text: String) = RecordKey(list, id) to SavedRecord.Master(json(text))

private fun line(id: String, text: String) = RecordKey(TRANSACTIONS_LIST, id) to SavedRecord.TransactionLine(OCTOBER, text)

/** The JSON of [record], to compare regardless of the order and spacing of its fields. */
private fun SavedRecord.json(): JsonElement = when (this) {
    is SavedRecord.Master -> element
    is SavedRecord.TransactionLine -> Json.parseToJsonElement(line)
}

/**
 * The references are replaced only in the fields that hold them: text such as a memo or a name
 * is kept even if it is the ID replaced (#44).
 */
class ReferenceReplacementTest {
    @Test
    fun replacesTheReferencesOfAnExpenseButNotItsMemo() {
        val records = mapOf(
            line(
                "t1",
                """{"v":1,"type":"expense","id":"t1","transaction_date":"2026-10-01","amount":500,"category_id":"$OLD",""" +
                    """"memo":"$OLD","fund_id":"$OLD","recurring_item_id":"$OLD","note":"$OLD"}""",
            ),
        )

        val replaced = replaceReferences(records, mapOf(OLD to NEW)).values.single().json().jsonObject

        assertEquals(
            json(
                """{"v":1,"type":"expense","id":"t1","transaction_date":"2026-10-01","amount":500,"category_id":"$NEW",""" +
                    """"memo":"$OLD","fund_id":"$NEW","recurring_item_id":"$NEW","note":"$OLD"}""",
            ),
            replaced,
        )
    }

    @Test
    fun replacesTheReferencesOfAnIncomeButNotItsMemo() {
        val records = mapOf(
            line(
                "t1",
                """{"v":1,"type":"income","id":"t1","transaction_date":"2026-10-01","amount":500,"category_id":"$OLD",""" +
                    """"memo":"$OLD","budget_month":"2026-10","allocations":[{"fund_id":"$OLD","amount":500,"note":"$OLD"}]}""",
            ),
        )

        val replaced = replaceReferences(records, mapOf(OLD to NEW)).values.single().json()

        assertEquals(
            json(
                """{"v":1,"type":"income","id":"t1","transaction_date":"2026-10-01","amount":500,"category_id":"$NEW",""" +
                    """"memo":"$OLD","budget_month":"2026-10","allocations":[{"fund_id":"$NEW","amount":500,"note":"$OLD"}]}""",
            ),
            replaced,
        )
    }

    @Test
    fun replacesTheReferencesOfTheMasterRecordsButNotTheirNames() {
        val fund = master(FUNDS, "f1", """{"id":"f1","name":"$OLD","color":"$OLD","opening_balance":0}""")
        val expenseCategory = master(EXPENSE_CATEGORIES, "c1", """{"id":"c1","name":"$OLD","default_fund_id":"$OLD","icon":"$OLD"}""")
        val incomeCategory = master(INCOME_CATEGORIES, "c2", """{"id":"c2","name":"$OLD"}""")
        val rule = master(
            ALLOCATION_RULES,
            "r1",
            """{"id":"r1","income_category_id":"$OLD","lines":[{"type":"ask","fund_id":"$OLD","label":"$OLD"}],"rest_fund_id":"$OLD"}""",
        )
        val item = master(RECURRING_ITEMS, "i1", """{"id":"i1","name":"$OLD","category_id":"$OLD","fund_id":"$OLD"}""")
        val records = mapOf(fund, expenseCategory, incomeCategory, rule, item)

        val replaced = replaceReferences(records, mapOf(OLD to NEW)).mapValues { it.value.json() }

        assertEquals(
            mapOf(
                fund.first to json("""{"id":"f1","name":"$OLD","color":"$OLD","opening_balance":0}"""),
                expenseCategory.first to json("""{"id":"c1","name":"$OLD","default_fund_id":"$NEW","icon":"$OLD"}"""),
                incomeCategory.first to json("""{"id":"c2","name":"$OLD"}"""),
                rule.first to json(
                    """{"id":"r1","income_category_id":"$NEW","lines":[{"type":"ask","fund_id":"$NEW","label":"$OLD"}],"rest_fund_id":"$NEW"}""",
                ),
                item.first to json("""{"id":"i1","name":"$OLD","category_id":"$NEW","fund_id":"$NEW"}"""),
            ),
            replaced,
        )
    }

    @Test
    fun keepsTheRecordsThatReferToNothingReplacedAsTheyAre() {
        val text = """{"v":1,"type":"expense","id":"t1","memo":"$OLD"}"""
        val records = mapOf(line("t1", text))

        assertEquals(records, replaceReferences(records, mapOf(OLD to NEW)))
    }

    @Test
    fun replacesTheReferencesOfUnreadableRecordsButNotTheirText() {
        val unreadable = UnreadableRecords(
            master = mapOf(EXPENSE_CATEGORIES to listOf(json("""{"id":"c1","name":"$OLD","default_fund_id":"$OLD"}"""))),
            transactions = mapOf(
                OCTOBER to listOf("""{"v":2,"type":"expense","id":"t1","memo":"$OLD","fund_id":"$OLD","category_id":"$OLD"}"""),
            ),
        )

        val replaced = unreadable.withReferencesReplaced(mapOf(OLD to NEW))

        assertEquals(
            mapOf(EXPENSE_CATEGORIES to listOf(json("""{"id":"c1","name":"$OLD","default_fund_id":"$NEW"}"""))),
            replaced.master,
        )
        assertEquals(
            json("""{"v":2,"type":"expense","id":"t1","memo":"$OLD","fund_id":"$NEW","category_id":"$NEW"}"""),
            json(replaced.transactions.getValue(OCTOBER).single()),
        )
    }
}
