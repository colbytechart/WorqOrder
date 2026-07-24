package worq.order.data

import java.time.Instant
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

interface ActiveTimerRepository {
    fun observeActiveTimer(): Flow<ActiveTimer?>

    suspend fun readActiveTimer(): ActiveTimer?

    suspend fun createActiveInterval(
        taskId: String,
        boundaryZoneId: ZoneId,
        start: Instant,
    ): CreateActiveIntervalResult

    suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot?
}
