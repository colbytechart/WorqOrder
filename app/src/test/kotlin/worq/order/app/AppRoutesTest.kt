package worq.order.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun createTaskRouteCarriesDisplayedDate() {
        val date = LocalDate.of(2026, 7, 25)

        assertTrue(AppRoutes.createTask(date).contains(date.toEpochDay().toString()))
    }
}
