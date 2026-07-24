package worq.order.timer

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import worq.order.model.WorkInterval

class NegativeDurationException(
    message: String,
) : IllegalArgumentException(message)

object DurationMath {
    fun completedInterval(interval: WorkInterval): Duration {
        val stop =
            interval.stop
                ?: throw IllegalArgumentException(
                    "Interval ${interval.id} is still open",
                )
        return nonNegativeBetween(interval.start, stop)
    }

    fun activeInterval(
        start: Instant,
        evaluationInstant: Instant,
    ): Duration = nonNegativeBetween(start, evaluationInstant)

    fun taskTotal(
        intervals: List<WorkInterval>,
        activeEvaluationInstant: Instant? = null,
    ): Duration {
        val openIntervals = intervals.filter { it.stop == null }
        require(openIntervals.size <= 1) { "A task cannot contain more than one open interval" }
        if (openIntervals.isNotEmpty()) {
            requireNotNull(activeEvaluationInstant) {
                "An evaluation instant is required for an open interval"
            }
        }

        return intervals.fold(Duration.ZERO) { total, interval ->
            val contribution =
                interval.stop?.let {
                    nonNegativeBetween(interval.start, it)
                } ?: activeInterval(
                    start = interval.start,
                    evaluationInstant = requireNotNull(activeEvaluationInstant),
                )
            total.plus(contribution)
        }
    }

    fun nonNegativeBetween(
        start: Instant,
        stop: Instant,
    ): Duration {
        if (stop.isBefore(start)) {
            throw NegativeDurationException(
                "Duration endpoint $stop is before start $start",
            )
        }
        return Duration.between(start, stop)
    }

    fun formatAccumulated(duration: Duration): String {
        if (duration.isNegative) {
            throw NegativeDurationException("Cannot format a negative duration")
        }
        val totalMillis = duration.toMillis()
        val hours = totalMillis / MILLIS_PER_HOUR
        val minutes = (totalMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
        val seconds = (totalMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
        val millis = totalMillis % MILLIS_PER_SECOND
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d.%03d",
            hours,
            minutes,
            seconds,
            millis,
        )
    }

    fun toLocalDisplay(
        instant: Instant,
        zoneId: ZoneId,
    ): ZonedDateTime = instant.atZone(zoneId)

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
}
