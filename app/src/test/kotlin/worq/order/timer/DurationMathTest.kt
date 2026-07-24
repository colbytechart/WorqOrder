package worq.order.timer

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import worq.order.model.WorkInterval

class DurationMathTest {
    @Test
    fun repeatedIntervalsAndActiveContributionProduceTaskTotal() {
        val intervals =
            listOf(
                interval("first", "2026-07-24T13:00:00Z", "2026-07-24T14:00:00Z"),
                interval("second", "2026-07-24T17:00:00Z", "2026-07-24T18:00:00Z"),
                interval("active", "2026-07-24T19:00:00Z", null),
            )

        val total =
            DurationMath.taskTotal(
                intervals = intervals,
                activeEvaluationInstant = Instant.parse("2026-07-24T19:30:00Z"),
            )

        assertEquals(Duration.ofMinutes(150), total)
    }

    @Test
    fun accumulatedFormatterAllowsHoursOverTwentyThreeAndKeepsMilliseconds() {
        val duration =
            Duration
                .ofHours(49)
                .plusMinutes(2)
                .plusSeconds(3)
                .plusMillis(4)

        assertEquals("49:02:03.004", DurationMath.formatAccumulated(duration))
    }

    @Test
    fun negativeDurationsAreRejected() {
        val start = Instant.parse("2026-07-24T14:00:00Z")
        val stop = start.minusMillis(1)

        assertThrows(NegativeDurationException::class.java) {
            DurationMath.nonNegativeBetween(start, stop)
        }
        assertThrows(NegativeDurationException::class.java) {
            DurationMath.formatAccumulated(Duration.ofMillis(-1))
        }
    }

    @Test
    fun persistedUtcInstantConvertsThroughRequestedGeographicalZone() {
        val local =
            DurationMath.toLocalDisplay(
                instant = Instant.parse("2026-07-24T13:00:00Z"),
                zoneId = ZoneId.of("America/New_York"),
            )

        assertEquals(9, local.hour)
        assertEquals("America/New_York", local.zone.id)
    }

    private fun interval(
        id: String,
        start: String,
        stop: String?,
    ) = WorkInterval(
        id = id,
        taskId = "task-1",
        ordinal = 1,
        start = Instant.parse(start),
        stop = stop?.let(Instant::parse),
        wasManuallyEdited = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
