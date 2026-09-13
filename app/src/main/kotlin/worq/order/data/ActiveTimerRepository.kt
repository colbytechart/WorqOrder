package worq.order.data

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.DailyTask

sealed interface CreateActiveIntervalResult {
    data class Created(
        val snapshot: ActiveTimerSnapshot,
        val startedTask: DailyTask,
        val repeatedTaskCreated: Boolean,
    ) : CreateActiveIntervalResult

    data object AlreadyActive : CreateActiveIntervalResult
}

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
     * Atomically closes the expected sole open interval at an exact local-date boundary and
     * clears the singleton active-timer row. No continuation task or interval is created.
     *
     * Null means another operation already changed or closed the expected active interval.
     */
    suspend fun closeActiveIntervalAtBoundary(
        expectedIntervalId: String,
        boundary: Instant,
    ): ActiveTimerSnapshot?

    /**
     * Atomically closes the expected sole open interval and clears the singleton active-timer row.
     *
     * Null means another operation already changed or closed the expected active interval.
     */
    suspend fun closeActiveInterval(
        expectedIntervalId: String,
        stop: Instant,
    ): ActiveTimerSnapshot?
}
