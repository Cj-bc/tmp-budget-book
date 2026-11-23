package jp.cjbc.kakeibo.ui.component

import androidx.compose.material3.SwipeToDismissBoxValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteConfirmationTest {
    private var deletions = 0
    private val confirmation = DeleteConfirmation { deletions++ }

    @Test
    fun swipingAsksInsteadOfDeleting() {
        confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart)

        assertTrue(confirmation.isAsking)
        assertEquals(0, deletions)
    }

    @Test
    fun swipingKeepsTheRowInPlace() {
        // The row goes back while the dialog asks, so that cancelling leaves it as it was.
        assertFalse(confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart))
    }

    @Test
    fun swipingPartwayDoesNotAsk() {
        confirmation.onSwipe(SwipeToDismissBoxValue.Settled)

        assertFalse(confirmation.isAsking)
    }

    @Test
    fun confirmingDeletesOnce() {
        confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart)
        confirmation.confirm()
        // A second tap on the button before the dialog closes.
        confirmation.confirm()

        assertEquals(1, deletions)
        assertFalse(confirmation.isAsking)
    }

    @Test
    fun cancellingDoesNotDelete() {
        confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart)
        confirmation.dismiss()

        assertEquals(0, deletions)
        assertFalse(confirmation.isAsking)
    }

    @Test
    fun confirmingWithoutAskingDoesNotDelete() {
        confirmation.confirm()

        assertEquals(0, deletions)
    }

    @Test
    fun canAskAgainAfterCancelling() {
        confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart)
        confirmation.dismiss()
        confirmation.onSwipe(SwipeToDismissBoxValue.EndToStart)
        confirmation.confirm()

        assertEquals(1, deletions)
    }
}
