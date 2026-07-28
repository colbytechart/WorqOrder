package worq.order.timer

import java.time.Duration
import java.time.Instant
import kotlin.math.max

enum class LiveDurationSource {
    START_ANCHOR,
    RECOVERED_WALL_CLOCK,
}

data class LiveDurationState(
    val total: Duration,
    val clockAnomalyDetected: Boolean,
    val source: LiveDurationSource,
)

data class LiveTimerAnchor(
    val intervalId: String,
    val totalAtAnchor: Duration,
    val activeDurationAtAnchor: Duration,
    val wallInstantAtAnchor: Instant,
    val monotonicAnchorNanos: Long,
    val source: LiveDurationSource,
    val clockAnomalyDetected: Boolean,
)

/**
 * Process-local bridge between persisted UTC boundaries and a non-jumping live display.
 *
 * No value in this class is authoritative or persisted. A new process reconstructs once from the
 * open interval's UTC start, then advances from elapsed realtime.
 */
class LiveTimerSession(
    private val monotonicTimeSource: MonotonicTimeSource,
) {
    @Volatile
    private var anchor: LiveTimerAnchor? = null

    fun establishAtStart(
        intervalId: String,
        completedTotal: Duration,
        wallStart: Instant,
    ): LiveTimerAnchor {
        require(!completedTotal.isNegative) { "completedTotal must not be negative" }
        return LiveTimerAnchor(
            intervalId = intervalId,
            totalAtAnchor = completedTotal,
            activeDurationAtAnchor = Duration.ZERO,
            wallInstantAtAnchor = wallStart,
            monotonicAnchorNanos = monotonicTimeSource.elapsedRealtimeNanos(),
            source = LiveDurationSource.START_ANCHOR,
            clockAnomalyDetected = false,
        ).also { anchor = it }
    }

    fun recover(
        intervalId: String,
        completedTotal: Duration,
        intervalStart: Instant,
        wallNow: Instant,
    ): LiveTimerAnchor {
        require(!completedTotal.isNegative) { "completedTotal must not be negative" }
        val wallElapsed = Duration.between(intervalStart, wallNow)
        val anomaly = wallElapsed.isNegative
        val nonNegativeWallElapsed = if (anomaly) Duration.ZERO else wallElapsed
        return LiveTimerAnchor(
            intervalId = intervalId,
            totalAtAnchor = completedTotal.plus(nonNegativeWallElapsed),
            activeDurationAtAnchor = nonNegativeWallElapsed,
            wallInstantAtAnchor = wallNow,
            monotonicAnchorNanos = monotonicTimeSource.elapsedRealtimeNanos(),
            source = LiveDurationSource.RECOVERED_WALL_CLOCK,
            clockAnomalyDetected = anomaly,
        ).also { anchor = it }
    }

    fun read(intervalId: String): LiveDurationState? {
        val currentAnchor = anchor?.takeIf { it.intervalId == intervalId } ?: return null
        val elapsed = monotonicElapsedSince(currentAnchor)
        return LiveDurationState(
            total = currentAnchor.totalAtAnchor.plus(elapsed),
            clockAnomalyDetected = currentAnchor.clockAnomalyDetected,
            source = currentAnchor.source,
        )
    }

    /**
     * Projects a stable UTC instant from the persisted segment start and Android elapsed realtime.
     *
     * This is available only while the process owns a valid anchor. A negative wall-clock recovery
     * is deliberately excluded because elapsed time before that recovery cannot be reconstructed
     * safely.
     */
    fun projectedInstant(
        intervalId: String,
        intervalStart: Instant,
    ): Instant? {
        val currentAnchor =
            anchor
                ?.takeIf {
                    it.intervalId == intervalId &&
                        !it.clockAnomalyDetected
                } ?: return null
        val activeDuration =
            currentAnchor.activeDurationAtAnchor
                .plus(monotonicElapsedSince(currentAnchor))
        return runCatching {
            intervalStart.plus(activeDuration)
        }.getOrNull()
    }

    fun hasClockAnomaly(
        intervalId: String,
        wallNow: Instant,
        tolerance: Duration = DEFAULT_CLOCK_DIAGNOSTIC_TOLERANCE,
    ): Boolean {
        require(!tolerance.isNegative) { "tolerance must not be negative" }
        val currentAnchor = anchor?.takeIf { it.intervalId == intervalId } ?: return false
        val monotonicElapsed =
            Duration.ofNanos(
                max(
                    0L,
                    monotonicTimeSource.elapsedRealtimeNanos() -
                        currentAnchor.monotonicAnchorNanos,
                ),
            )
        val expectedActive =
            currentAnchor.activeDurationAtAnchor.plus(monotonicElapsed)
        val wallSinceAnchor = Duration.between(currentAnchor.wallInstantAtAnchor, wallNow)
        if (wallSinceAnchor.isNegative) {
            return true
        }
        val wallActive = currentAnchor.activeDurationAtAnchor.plus(wallSinceAnchor)
        return expectedActive.minus(wallActive).abs() > tolerance
    }

    fun clear() {
        anchor = null
    }

    private fun monotonicElapsedSince(anchor: LiveTimerAnchor): Duration =
        Duration.ofNanos(
            max(
                0L,
                monotonicTimeSource.elapsedRealtimeNanos() -
                    anchor.monotonicAnchorNanos,
            ),
        )

    companion object {
        val DEFAULT_CLOCK_DIAGNOSTIC_TOLERANCE: Duration = Duration.ofMinutes(2)
    }
}
