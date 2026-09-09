package worq.order.timer

import java.time.Instant
import worq.order.data.ActiveTimerRepository
import worq.order.data.SelectedTaskRepository
import worq.order.model.ActiveTimerSnapshot

sealed interface BoundaryCloseResult {
    data object BeforeBoundary : BoundaryCloseResult

    data object ActiveTimerChanged : BoundaryCloseResult

    data class Closed(
        val snapshot: ActiveTimerSnapshot,
        val boundary: Instant,
    ) : BoundaryCloseResult
}

/**
 * Applies the schema-5 midnight policy to one authoritative active-timer snapshot.
 *
 * The Room repository compare-and-closes the expected interval transactionally, so concurrent or
 * repeated callers have at most one winner. The first next-day boundary is always used, even when
 * [evaluationInstant] is several days late. DataStore selection is compare-and-cleared only after
 * Room commits, and the process-local monotonic anchor is never treated as authority.
 */
class ActiveTimerBoundaryCloser(
    private val activeTimerRepository: ActiveTimerRepository,
    private val selectedTaskRepository: SelectedTaskRepository,
    private val liveTimerSession: LiveTimerSession,
) {
    suspend fun closeIfReached(
        snapshot: ActiveTimerSnapshot,
        evaluationInstant: Instant,
    ): BoundaryCloseResult {
        val boundary =
            MidnightBoundaryCalculator.firstBoundaryAfter(
                segmentStart = snapshot.interval.start,
                zoneId = snapshot.activeTimer.boundaryZoneId,
            )
        if (evaluationInstant.isBefore(boundary)) {
            return BoundaryCloseResult.BeforeBoundary
        }

        val closed =
            activeTimerRepository.closeActiveIntervalAtBoundary(
                expectedIntervalId = snapshot.interval.id,
                boundary = boundary,
            ) ?: return BoundaryCloseResult.ActiveTimerChanged

        liveTimerSession.clear()
        selectedTaskRepository.clearIfSelected(closed.interval.taskId)
        return BoundaryCloseResult.Closed(
            snapshot = closed,
            boundary = boundary,
        )
    }
}
