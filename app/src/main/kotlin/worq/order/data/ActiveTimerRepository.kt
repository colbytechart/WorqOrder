package worq.order.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot

sealed interface CreateActiveIntervalResult {
    data class Created(
        val snapshot: ActiveTimerSnapshot,
    ) : CreateActiveIntervalResult

    data object AlreadyActive : CreateActiveIntervalResult
}

data class TimerSplitBoundary(
    val instant: Instant,
    val workDate: LocalDate,
    val zoneId: ZoneId,
)

interface ActiveTimerRepository {
    fun observeActiveTimer(): Flow<ActiveTimer?>

    suspend fun readActiveTimer(): ActiveTimer?

    suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot?

    suspend fun createActiveInterval(
        taskId: String,
        boundaryZoneId: ZoneId,
        start: Instant,
    ): CreateActiveIntervalResult

    suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot?

    /**
     * Atomically closes the current open segment at every boundary, creates or finds each daily
     * continuation task, and retargets the singleton active-timer pointer.
     *
     * Null means the expected active interval no longer exists.
     */
    suspend fun normalizeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
    ): ActiveTimerSnapshot?

    /**
     * Performs the same boundary splitting as [normalizeActiveInterval], then atomically closes
     * the final segment and clears the singleton active-timer pointer.
     */
    suspend fun closeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
        stop: Instant,
    ): ActiveTimerSnapshot?
}
