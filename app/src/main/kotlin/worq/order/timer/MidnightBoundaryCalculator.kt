package worq.order.timer

import java.time.Instant
import java.time.ZoneId

object MidnightBoundaryCalculator {
    /** Returns the first real start-of-day instant after [segmentStart] in [zoneId]. */
    fun firstBoundaryAfter(
        segmentStart: Instant,
        zoneId: ZoneId,
    ): Instant =
        segmentStart
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
}
