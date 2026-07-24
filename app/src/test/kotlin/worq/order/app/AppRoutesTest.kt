package worq.order.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {
    @Test
    fun destinationPatternsAreUnique() {
        assertEquals(
            AppRoutes.destinationPatterns.size,
            AppRoutes.destinationPatterns.toSet().size,
        )
    }

    @Test
    fun editTaskRouteIncludesStableIdentifier() {
        assertTrue(AppRoutes.editTask("task-123").contains("task-123"))
    }
}
