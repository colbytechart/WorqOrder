package worq.order.timer

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MidnightBoundaryCalculatorTest {
    @Test
    fun firstBoundaryUsesTwentyThreeHourSpringForwardDay() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant()

        val boundary = MidnightBoundaryCalculator.firstBoundaryAfter(start, zone)

        assertEquals(LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant(), boundary)
        assertEquals(Duration.ofHours(23), Duration.between(start, boundary))
    }

    @Test
    fun firstBoundaryUsesTwentyFiveHourFallBackDay() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()

        val boundary = MidnightBoundaryCalculator.firstBoundaryAfter(start, zone)

        assertEquals(LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant(), boundary)
        assertEquals(Duration.ofHours(25), Duration.between(start, boundary))
    }

    @Test
    fun firstBoundaryUsesNonDstZoneRules() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = LocalDate.of(2026, 7, 25).atStartOfDay(zone).toInstant()

        val boundary = MidnightBoundaryCalculator.firstBoundaryAfter(start, zone)

        assertEquals(LocalDate.of(2026, 7, 26).atStartOfDay(zone).toInstant(), boundary)
        assertEquals(Duration.ofHours(24), Duration.between(start, boundary))
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
