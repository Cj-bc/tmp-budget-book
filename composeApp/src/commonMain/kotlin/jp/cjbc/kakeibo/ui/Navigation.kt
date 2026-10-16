package jp.cjbc.kakeibo.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.cjbc.kakeibo.model.TransactionId
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
}

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

    fun back() {
        stack.removeLastOrNull()
    }
}
