package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.TransactionId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** The bottom bar's tabs. */
enum class Tab { ENTRY, FUNDS, RECURRING, ANALYTICS }

/** A screen opened over the tabs, with a back arrow. */
sealed interface Route {
    /** The history, showing only [month] if given. */
    data class History(val month: YearMonth? = null) : Route

    /** The entry screen editing the transaction [id]. */
    data class Edit(val id: TransactionId) : Route

    data object Settings : Route

    /** The expense categories, or the income ones unless [isExpense]. */
    data class Categories(val isExpense: Boolean) : Route

    /** Splitting the income in [draft] into funds before it is recorded. */
    data class Allocate(val draft: IncomeDraft) : Route

    /** The allocation rules, showing the rule of [categoryId] or the first income category's. */
    data class AllocationRules(val categoryId: CategoryId? = null) : Route

    data class FundDetail(val id: FundId) : Route

    /** Editing the fund [id], or adding a fund when it is `null`. */
    data class FundEdit(val id: FundId?) : Route

    /** The incomes whose allocations are for [month]. */
    data class MonthIncomes(val month: YearMonth) : Route
}

/** An income entered on the record screen, waiting for its allocation. */
data class IncomeDraft(
    /** The income being edited, or `null` for a new one. */
    val editing: TransactionId?,
    val date: LocalDate,
    val amount: Long,
    val categoryId: CategoryId,
    val memo: String,
)

/** Which tab is shown and which screens are stacked over it. */
@Stable
class AppNavigator {
    var tab by mutableStateOf(Tab.ENTRY)
        private set

    private val stack = mutableStateListOf<Route>()

    /** The screen on top, or `null` when the tab itself shows. */
    val route: Route? get() = stack.lastOrNull()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    /** Shows [tab], closing any screen over the tabs. */
    fun select(tab: Tab) {
        this.tab = tab
        stack.clear()
    }

    fun open(route: Route) {
        stack += route
    }

    /** Closes the top [count] screens. */
    fun back(count: Int = 1) {
        repeat(count) { stack.removeLastOrNull() }
    }
}
