package jp.cjbc.kakeibo.data

import jp.cjbc.kakeibo.model.AllocationRuleId
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.LedgerData
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import jp.cjbc.kakeibo.util.generateUlid
import jp.cjbc.kakeibo.util.isUlid
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Gives a new ULID to each fund, category, transaction, recurring item and allocation rule whose
 * ID is not one, and changes everything referring to it. Data saved in the key-value storage, and
 * backups of it, have IDs such as `20250101-123456` and `fund-food`.
 *
 * The new IDs decrease along each list, a millisecond apart, so that the latest first order of
 * [LedgerData.transactions] stays the same among the transactions of a day.
 */
@OptIn(ExperimentalTime::class)
fun LedgerData.withUlids(): LedgerData {
    val now = Clock.System.now().toEpochMilliseconds()
    fun newIds(ids: List<String>): Map<String, String> = ids.filterNot(::isUlid).distinct()
        .withIndex()
        .associate { (index, id) -> id to generateUlid(timeMillis = now - index) }
    val fundIds = newIds(funds.map { it.id.value })
    val categoryIds = newIds(categories.map { it.id.value })
    val transactionIds = newIds(transactions.map { it.id.value })
    val itemIds = newIds(recurringItems.map { it.id.value })
    val ruleIds = newIds(allocationRules.map { it.id.value })
    // A reference to what does not exist is kept, and fails validation.
    fun FundId.renewed() = FundId(fundIds[value] ?: value)
    fun CategoryId.renewed() = CategoryId(categoryIds[value] ?: value)
    fun RecurringItemId.renewed() = RecurringItemId(itemIds[value] ?: value)

    return LedgerData(
        funds = funds.map { it.copy(id = it.id.renewed()) },
        categories = categories.map {
            when (it) {
                is Category.Expense -> it.copy(id = it.id.renewed(), defaultFundId = it.defaultFundId.renewed())
                is Category.Income -> it.copy(id = it.id.renewed())
            }
        },
        transactions = transactions.map {
            val id = TransactionId(transactionIds[it.id.value] ?: it.id.value)
            when (it) {
                is Transaction.Expense -> it.copy(
                    id = id,
                    categoryId = it.categoryId.renewed(),
                    fundId = it.fundId.renewed(),
                    recurringItemId = it.recurringItemId?.renewed(),
                )
                is Transaction.Income -> it.copy(
                    id = id,
                    categoryId = it.categoryId.renewed(),
                    allocations = it.allocations.map { allocation -> allocation.copy(fundId = allocation.fundId.renewed()) },
                )
            }
        },
        allocationRules = allocationRules.map { rule ->
            rule.copy(
                id = AllocationRuleId(ruleIds[rule.id.value] ?: rule.id.value),
                incomeCategoryId = rule.incomeCategoryId.renewed(),
                lines = rule.lines.map {
                    when (it) {
                        is RuleLine.Fixed -> it.copy(fundId = it.fundId.renewed())
                        is RuleLine.Percent -> it.copy(fundId = it.fundId.renewed())
                        is RuleLine.Ask -> it.copy(fundId = it.fundId.renewed())
                    }
                },
                restFundId = rule.restFundId.renewed(),
            )
        },
        recurringItems = recurringItems.map {
            it.copy(id = it.id.renewed(), categoryId = it.categoryId.renewed(), fundId = it.fundId.renewed())
        },
    )
}
