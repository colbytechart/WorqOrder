package worq.order.timer

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidnightBoundaryCalculatorTest {
    @Test
    fun oneAndMultipleMidnightsUseLocalDateBoundaries() {
        val zone = ZoneId.of("America/New_York")
        val one =
            MidnightBoundaryCalculator.boundaries(
                segmentStart = Instant.parse("2026-07-25T03:30:00Z"),
                endpoint = Instant.parse("2026-07-25T05:00:00Z"),
                zoneId = zone,
                includeEndpoint = false,
            )
        val multiple =
            MidnightBoundaryCalculator.boundaries(
                segmentStart = Instant.parse("2026-07-25T03:30:00Z"),
                endpoint = Instant.parse("2026-07-27T05:00:00Z"),
                zoneId = zone,
                includeEndpoint = false,
            )

        assertEquals(listOf(LocalDate.of(2026, 7, 25)), one.map { it.workDate })
        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27),
            ),
            multiple.map { it.workDate },
        )
    }

    @Test
    fun exactStopBoundaryDoesNotCreateZeroLengthContinuation() {
        val zone = ZoneId.of("America/New_York")
        val boundary = LocalDate.of(2026, 7, 25).atStartOfDay(zone).toInstant()

        val stopBoundaries =
            MidnightBoundaryCalculator.boundaries(
                segmentStart = boundary.minusSeconds(60),
                endpoint = boundary,
                zoneId = zone,
                includeEndpoint = false,
            )
        val runningBoundaries =
            MidnightBoundaryCalculator.boundaries(
                segmentStart = boundary.minusSeconds(60),
                endpoint = boundary,
                zoneId = zone,
                includeEndpoint = true,
            )

        assertTrue(stopBoundaries.isEmpty())
        assertEquals(listOf(boundary), runningBoundaries.map { it.instant })
    }

    @Test
    fun springForwardAndFallBackDaysAreNotForcedToTwentyFourHours() {
        val zone = ZoneId.of("America/New_York")
        val spring =
            MidnightBoundaryCalculator.boundaries(
                segmentStart =
                    LocalDate.of(2026, 3, 7).atStartOfDay(zone).toInstant(),
                endpoint =
                    LocalDate.of(2026, 3, 10).atStartOfDay(zone).toInstant(),
                zoneId = zone,
                includeEndpoint = false,
            )
        val fall =
            MidnightBoundaryCalculator.boundaries(
                segmentStart =
                    LocalDate.of(2026, 10, 31).atStartOfDay(zone).toInstant(),
                endpoint =
                    LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant(),
                zoneId = zone,
                includeEndpoint = false,
            )

        assertEquals(
            Duration.ofHours(23),
            Duration.between(spring[0].instant, spring[1].instant),
        )
        assertEquals(
            Duration.ofHours(25),
            Duration.between(fall[0].instant, fall[1].instant),
        )
    }

    @Test
    fun nonDstZoneUsesItsOwnBoundaries() {
        val zone = ZoneId.of("Asia/Kolkata")
        val date = LocalDate.of(2026, 7, 25)
        val boundaries =
            MidnightBoundaryCalculator.boundaries(
                segmentStart = date.atStartOfDay(zone).minusSeconds(1).toInstant(),
                endpoint = date.plusDays(1).atStartOfDay(zone).plusSeconds(1).toInstant(),
                zoneId = zone,
                includeEndpoint = false,
            )

        assertEquals(
            listOf(date, date.plusDays(1)),
            boundaries.map { it.workDate },
        )
        assertEquals(
            Duration.ofHours(24),
            Duration.between(boundaries[0].instant, boundaries[1].instant),
        )
    }

    @Test
    fun firstBoundaryUsesZoneRulesWhenAnEntireLocalDateWasSkipped() {
        val zone = ZoneId.of("Pacific/Apia")
        val start = Instant.parse("2011-12-30T09:30:00Z")

        val boundary = MidnightBoundaryCalculator.firstBoundaryAfter(start, zone)

        assertEquals(Instant.parse("2011-12-30T10:00:00Z"), boundary)
        assertEquals(LocalDate.of(2011, 12, 31), boundary.atZone(zone).toLocalDate())
        assertEquals(Duration.ofMinutes(30), Duration.between(start, boundary))
    }
}
