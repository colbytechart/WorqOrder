package worq.order.timer

import java.time.Instant
import java.time.ZoneId
import worq.order.data.TimerSplitBoundary

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

    /**
     * Finds every real local-date boundary after [segmentStart] and before [endpoint].
     *
     * When [includeEndpoint] is true, a boundary equal to [endpoint] is included so an interval
     * that remains active at midnight is moved to the new local date. Stop uses false to avoid a
     * zero-length continuation when its endpoint is exactly midnight.
     */
    fun boundaries(
        segmentStart: Instant,
        endpoint: Instant,
        zoneId: ZoneId,
        includeEndpoint: Boolean,
    ): List<TimerSplitBoundary> {
        if (endpoint.isBefore(segmentStart)) {
            return emptyList()
        }

        val result = mutableListOf<TimerSplitBoundary>()
        var segmentDate = segmentStart.atZone(zoneId).toLocalDate()
        while (true) {
            val nextDate = segmentDate.plusDays(1)
            val boundary = nextDate.atStartOfDay(zoneId).toInstant()
            val belongsBeforeEndpoint =
                boundary.isBefore(endpoint) ||
                    (includeEndpoint && boundary == endpoint)
            if (!belongsBeforeEndpoint) {
                break
            }
            result +=
                TimerSplitBoundary(
                    instant = boundary,
                    workDate = nextDate,
                    zoneId = zoneId,
                )
            segmentDate = nextDate
        }
        return result
    }
}
