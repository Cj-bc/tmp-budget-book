package jp.cjbc.kakeibo.ui.component

import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Asks before a swiped row is deleted, so that a swipe made by mistake deletes nothing: swiping
 * only asks, and [delete] runs when the user confirms.
 */
internal class DeleteConfirmation(private val delete: () -> Unit) {
    /** Whether the dialog asking to delete is shown. */
    var isAsking by mutableStateOf(false)
        private set

    /**
     * For the swipe's `confirmValueChange`: asks when the row is swiped all the way to the start.
     * Always `false`, so that the row goes back into place while the dialog asks.
     */
    fun onSwipe(value: SwipeToDismissBoxValue): Boolean {
        if (value == SwipeToDismissBoxValue.EndToStart) isAsking = true
        return false
    }

    /** Deletes, if the dialog is asking. */
    fun confirm() {
        if (!isAsking) return
        isAsking = false
        delete()
    }

    /** Closes the dialog without deleting. */
    fun dismiss() {
        isAsking = false
    }
}
