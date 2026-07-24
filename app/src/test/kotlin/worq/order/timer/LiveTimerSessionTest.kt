package worq.order.timer

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.testing.FakeMonotonicTimeSource

class LiveTimerSessionTest {
    @Test
    fun startAnchorAdvancesFromCompletedTotalUsingMonotonicTime() {
        val monotonic = FakeMonotonicTimeSource(nanos = 10_000)
        val session = LiveTimerSession(monotonic)
        session.establishAtStart(
            intervalId = "interval-1",
            completedTotal = Duration.ofHours(1),
            wallStart = Instant.parse("2026-07-24T13:00:00Z"),
        )

        monotonic.nanos += Duration.ofMinutes(30).toNanos()

        assertEquals(
            Duration.ofMinutes(90),
            session.read("interval-1")?.total,
        )
        assertFalse(requireNotNull(session.read("interval-1")).clockAnomalyDetected)
    }

    @Test
    fun processRecoveryReconstructsFromUtcThenUsesMonotonicTime() {
        val monotonic = FakeMonotonicTimeSource(nanos = 1_000)
        val session = LiveTimerSession(monotonic)
        session.recover(
            intervalId = "interval-1",
            completedTotal = Duration.ofMinutes(15),
            intervalStart = Instant.parse("2026-07-24T13:00:00Z"),
            wallNow = Instant.parse("2026-07-24T13:45:00Z"),
        )
        monotonic.nanos += Duration.ofMinutes(5).toNanos()

        val state = requireNotNull(session.read("interval-1"))

        assertEquals(Duration.ofMinutes(65), state.total)
        assertEquals(LiveDurationSource.RECOVERED_WALL_CLOCK, state.source)
        assertFalse(state.clockAnomalyDetected)
    }

    @Test
    fun negativeRecoveryIsClampedAndSurfaced() {
        val session = LiveTimerSession(FakeMonotonicTimeSource())
        session.recover(
            intervalId = "interval-1",
            completedTotal = Duration.ofHours(2),
            intervalStart = Instant.parse("2026-07-24T13:00:00Z"),
            wallNow = Instant.parse("2026-07-24T12:59:00Z"),
        )

        val state = requireNotNull(session.read("interval-1"))

        assertEquals(Duration.ofHours(2), state.total)
        assertTrue(state.clockAnomalyDetected)
    }

    @Test
    fun largeWallClockJumpComparedWithMonotonicTimeIsDetected() {
        val monotonic = FakeMonotonicTimeSource()
        val session = LiveTimerSession(monotonic)
        val start = Instant.parse("2026-07-24T13:00:00Z")
        session.establishAtStart(
            intervalId = "interval-1",
            completedTotal = Duration.ZERO,
            wallStart = start,
        )
        monotonic.nanos += Duration.ofMinutes(1).toNanos()

        assertTrue(
            session.hasClockAnomaly(
                intervalId = "interval-1",
                wallNow = start.plus(Duration.ofMinutes(10)),
            ),
        )
    }
}
