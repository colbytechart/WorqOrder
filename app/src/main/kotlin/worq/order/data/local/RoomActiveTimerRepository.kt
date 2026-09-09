package worq.order.data.local

import android.database.sqlite.SQLiteConstraintException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.ActiveTimerRepository
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.EntityIdGenerator
import worq.order.data.TimerSplitBoundary
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.timer.UtcClock

class RoomActiveTimerRepository(
    private val activeTimerDao: ActiveTimerDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : ActiveTimerRepository {
    override fun observeActiveTimer(): Flow<ActiveTimer?> =
        activeTimerDao.observeActiveTimer().map { activeTimer ->
            activeTimer?.toModel()
        }

    override suspend fun readActiveTimer(): ActiveTimer? =
        activeTimerDao.readActiveTimer()?.toModel()

    override suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot? =
        activeTimerDao.readActiveTimerSnapshot()?.toModel()

    override suspend fun createActiveInterval(
        taskId: String,
        boundaryZoneId: ZoneId,
        start: Instant,
    ): CreateActiveIntervalResult =
        try {
            val transaction =
                activeTimerDao.createActiveIntervalAndTimer(
                    intervalId = idGenerator.newId(),
                    repeatedTaskId = idGenerator.newId(),
                    taskId = taskId,
                    boundaryZoneId = boundaryZoneId.id,
                    startEpochMs = start.toEpochMilli(),
                    createdAtEpochMs = clock.now().toEpochMilli(),
                )
            CreateActiveIntervalResult.Created(
                snapshot =
                    ActiveTimerSnapshot(
                        activeTimer = transaction.activeTimer.toModel(),
                        interval = transaction.interval.toModel(),
                    ),
                startedTask = transaction.startedTask.toModel(),
                repeatedTaskCreated = transaction.repeatedTaskCreated,
            )
        } catch (_: ActiveTimerAlreadyExistsException) {
            CreateActiveIntervalResult.AlreadyActive
        } catch (error: SQLiteConstraintException) {
            if (activeTimerDao.readActiveTimer() != null) {
                CreateActiveIntervalResult.AlreadyActive
            } else {
                throw error
            }
        }

    override suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot? =
        activeTimerDao
            .closeActiveIntervalAndClearTimer(
                stopEpochMs = stop.toEpochMilli(),
                updatedAtEpochMs = clock.now().toEpochMilli(),
            )?.toModel()

    override suspend fun closeActiveIntervalAtBoundary(
        expectedIntervalId: String,
        boundary: Instant,
    ): ActiveTimerSnapshot? =
        activeTimerDao
            .closeActiveIntervalAtBoundary(
                expectedIntervalId = expectedIntervalId,
                boundaryEpochMs = boundary.toEpochMilli(),
                updatedAtEpochMs = clock.now().toEpochMilli(),
            )?.toModel()

    override suspend fun normalizeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
    ): ActiveTimerSnapshot? {
        val updatedAtEpochMs = clock.now().toEpochMilli()
        return activeTimerDao
            .normalizeActiveInterval(
                expectedIntervalId = expectedIntervalId,
                continuations = boundaries.toEntityInputs(),
                updatedAtEpochMs = updatedAtEpochMs,
            )?.toModel()
    }

    override suspend fun closeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
        stop: Instant,
    ): ActiveTimerSnapshot? {
        val updatedAtEpochMs = clock.now().toEpochMilli()
        return activeTimerDao
            .closeActiveIntervalAndClearTimer(
                expectedIntervalId = expectedIntervalId,
                continuations = boundaries.toEntityInputs(),
                stopEpochMs = stop.toEpochMilli(),
                updatedAtEpochMs = updatedAtEpochMs,
            )?.toModel()
    }

    private fun List<TimerSplitBoundary>.toEntityInputs(): List<TimerContinuationEntityInput> =
        map { boundary ->
            TimerContinuationEntityInput(
                boundaryEpochMs = boundary.instant.toEpochMilli(),
                workDateEpochDay = boundary.workDate.toEpochDay(),
                zoneId = boundary.zoneId.id,
                proposedTaskId = idGenerator.newId(),
                proposedIntervalId = idGenerator.newId(),
            )
        }
}
