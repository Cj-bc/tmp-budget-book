package jp.cjbc.kakeibo.model

/** The funds that are not archived, in their order. */
val LedgerData.activeFunds: List<Fund>
    get() = funds.filterNot { it.archived }.sortedBy { it.sortOrder }

/** The expense categories that are not archived, in their order. */
val LedgerData.activeExpenseCategories: List<Category.Expense>
    get() = categories.filterIsInstance<Category.Expense>().filterNot { it.archived }.sortedBy { it.sortOrder }

/** The income categories that are not archived, in their order. */
val LedgerData.activeIncomeCategories: List<Category.Income>
    get() = categories.filterIsInstance<Category.Income>().filterNot { it.archived }.sortedBy { it.sortOrder }

fun LedgerData.fund(id: FundId): Fund? = funds.find { it.id == id }

fun LedgerData.expenseCategory(id: CategoryId): Category.Expense? =
    categories.filterIsInstance<Category.Expense>().find { it.id == id }

/** The sort order that puts a new item after [items]. */
fun nextSortOrder(sortOrders: List<Int>): Int = (sortOrders.maxOrNull() ?: -1) + 1
