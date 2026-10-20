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

fun LedgerData.expenseCategory(id: CategoryId): Category.Expense? = category(id) as? Category.Expense

fun LedgerData.category(id: CategoryId): Category? = categories.find { it.id == id }

/** The name of the category [id], or the ID itself if there is no such category. */
fun LedgerData.categoryName(id: CategoryId): String = category(id)?.name ?: id.value

/**
 * The category of the kind named [name], ignoring case and surrounding spaces, preferring one that
 * is not archived; `null` if there is none.
 */
fun LedgerData.categoryNamed(name: String, isExpense: Boolean): Category? = categories
    .filter { (it is Category.Expense) == isExpense && it.name.equals(name.trim(), ignoreCase = true) }
    .minByOrNull { it.archived }

/** The sort order that puts a new item after [items]. */
fun nextSortOrder(sortOrders: List<Int>): Int = (sortOrders.maxOrNull() ?: -1) + 1
