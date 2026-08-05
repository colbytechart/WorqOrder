package worq.order.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTimeFormatterTest {
    @Test
    fun formatsMidnightNoonAndAfternoonWithStrictAmPmPolicy() {
        assertEquals(
            "12:00 AM",
            ClockTimeFormatter.format(LocalDateTime.of(2026, 8, 5, 0, 0, 59, 999_000_000)),
        )
        assertEquals(
            "12:00 PM",
            ClockTimeFormatter.format(LocalDateTime.of(2026, 8, 5, 12, 0)),
        )
        assertEquals(
            "01:05 PM",
            ClockTimeFormatter.format(LocalDateTime.of(2026, 8, 5, 13, 5)),
        )
    }

    @Test
    fun formatsAnInstantInTheTaskZoneWithoutChangingItsPrecision() {
        val instant = Instant.parse("2026-07-24T13:05:42.987Z")

        assertEquals(
            "09:05 AM",
            ClockTimeFormatter.format(instant, ZoneId.of("America/New_York")),
        )
        assertEquals("2026-07-24T13:05:42.987Z", instant.toString())
    }

    @Test
    fun fallBackOccurrencesKeepTheSameHonestLocalClockPresentation() {
        val zone = ZoneId.of("America/New_York")

        assertEquals(
            "01:30 AM",
            ClockTimeFormatter.format(Instant.parse("2026-11-01T05:30:00Z"), zone),
        )
        assertEquals(
            "01:30 AM",
            ClockTimeFormatter.format(Instant.parse("2026-11-01T06:30:00Z"), zone),
        )
    }
}
