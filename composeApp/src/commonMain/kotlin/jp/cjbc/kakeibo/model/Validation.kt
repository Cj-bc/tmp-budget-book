package jp.cjbc.kakeibo.model

/**
 * Describes what makes this data inconsistent; empty if nothing does. Data with problems is
 * neither saved nor imported.
 */
fun LedgerData.problems(): List<String> {
    val fundIds = funds.map { it.id }.toSet()
    val expenseCategoryIds = categories.filterIsInstance<Category.Expense>().map { it.id }.toSet()
    val incomeCategoryIds = categories.filterIsInstance<Category.Income>().map { it.id }.toSet()
    val problems = mutableListOf<String>()
    fun checkFund(fundId: FundId, owner: String) {
        if (fundId !in fundIds) problems += "$owner refers to the unknown fund ${fundId.value}"
    }

    if (fundIds.size != funds.size) problems += "fund IDs are not unique"
    if (categories.map { it.id }.toSet().size != categories.size) problems += "category IDs are not unique"
    categories.filterIsInstance<Category.Expense>().forEach {
        checkFund(it.defaultFundId, "category ${it.id.value}")
    }
    transactions.forEach { transaction ->
        val owner = "transaction ${transaction.id.value}"
        if (transaction.amount <= 0) problems += "$owner has a non-positive amount"
        when (transaction) {
            is Transaction.Expense -> {
                checkFund(transaction.fundId, owner)
                if (transaction.categoryId !in expenseCategoryIds) {
                    problems += "$owner has the unknown expense category ${transaction.categoryId.value}"
                }
            }
            is Transaction.Income -> {
                transaction.allocations.forEach { checkFund(it.fundId, owner) }
                if (transaction.allocations.any { it.amount < 0 }) {
                    problems += "$owner has a negative allocation"
                }
                if (!transaction.isFullyAllocated) {
                    problems += "$owner allocates other than its amount"
                }
                if (transaction.categoryId !in incomeCategoryIds) {
                    problems += "$owner has the unknown income category ${transaction.categoryId.value}"
                }
            }
        }
    }
    allocationRules.forEach { rule ->
        val owner = "the allocation rule of ${rule.incomeCategoryId.value}"
        checkFund(rule.restFundId, owner)
        rule.lines.forEach { checkFund(it.fundId, owner) }
    }
    recurringItems.forEach { item ->
        val owner = "recurring item ${item.id.value}"
        checkFund(item.fundId, owner)
        if (item.amount <= 0) problems += "$owner has a non-positive amount"
        if (item.dayOfMonth !in MIN_RECURRING_DAY..MAX_RECURRING_DAY) {
            problems += "$owner has the day ${item.dayOfMonth}"
        }
        if (item.categoryId !in expenseCategoryIds) {
            problems += "$owner has the unknown expense category ${item.categoryId.value}"
        }
    }
    return problems
}
