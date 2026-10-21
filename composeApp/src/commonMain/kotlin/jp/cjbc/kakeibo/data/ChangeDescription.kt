package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.categoryName

/**
 * A commit message for the change from [before] to [after]: a line for each record added, updated
 * or deleted. With more than one, the first line sums them up and the rest list them.
 */
fun describeChanges(before: LedgerData, after: LedgerData): String {
    val changes = Changes(before, after).apply {
        add({ transactions }, { it.id }) { verb, transaction, data ->
            val kind = if (transaction is Transaction.Income) "income" else "expense"
            "$verb $kind of ${transaction.amount} yen on ${transaction.date} (${data.categoryName(transaction.categoryId)})"
        }
        add({ recurringItems }, { it.id }) { verb, item, _ -> "$verb recurring item ${item.name}" }
        add({ funds }, { it.id }) { verb, fund, _ -> "$verb fund ${fund.name}" }
        add({ categories }, { it.id }) { verb, category, _ ->
            val kind = if (category is Category.Income) "income" else "expense"
            "$verb $kind category ${category.name}"
        }
        add({ allocationRules }, { it.id }) { verb, rule, data ->
            "$verb the allocation rule of ${data.categoryName(rule.incomeCategoryId)}"
        }
    }.lines
    return when (changes.size) {
        0 -> "Save the ledger"
        1 -> changes.single()
        else -> "${changes.first()}, and ${changes.size - 1} more\n\n" + changes.joinToString("\n") { "- $it" }
    }
}

private class Changes(val before: LedgerData, val after: LedgerData) {
    val lines = mutableListOf<String>()

    /**
     * Describes the records of the list [records] that were added, updated or deleted. Names in a
     * description come from the data the record is in: [before] for a deleted one.
     */
    fun <T, K> add(
        records: LedgerData.() -> List<T>,
        key: (T) -> K,
        describe: (verb: String, record: T, data: LedgerData) -> String,
    ) {
        val old = before.records().associateBy(key)
        val new = after.records().associateBy(key)
        new.forEach { (key, record) ->
            when (old[key]) {
                null -> lines += describe("Add", record, after)
                record -> {}
                else -> lines += describe("Update", record, after)
            }
        }
        old.filterKeys { it !in new }.values.forEach { lines += describe("Delete", it, before) }
    }
}
