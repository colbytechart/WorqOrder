package worq.order.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats user-facing and canonical-export clock values under one 12-hour policy. */
object ClockTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    fun format(
        instant: Instant,
        zoneId: ZoneId,
    ): String = formatter.format(instant.atZone(zoneId))

    fun format(localDateTime: LocalDateTime): String = formatter.format(localDateTime)
}
