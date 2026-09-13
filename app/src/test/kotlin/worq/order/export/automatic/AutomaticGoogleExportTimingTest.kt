package worq.order.export.automatic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticGoogleExportTimingTest {
    @Test
    fun capturedDateIsNotEligibleUntilItsRealZoneBoundary() {
        val workDate = LocalDate.of(2026, 3, 8)
        val zoneId = ZoneId.of("America/New_York")

        assertEquals(
            Instant.parse("2026-03-09T04:00:00Z"),
            automaticGoogleExportBoundary(workDate, zoneId),
        )
    }
}
