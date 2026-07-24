package worq.order.data.local

import android.database.sqlite.SQLiteConstraintException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.ActiveTimerRepository
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.EntityIdGenerator
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

    override suspend fun createActiveInterval(
        taskId: String,
        boundaryZoneId: ZoneId,
        start: Instant,
    ): CreateActiveIntervalResult =
        try {
            val transaction =
                activeTimerDao.createActiveIntervalAndTimer(
                    intervalId = idGenerator.newId(),
                    taskId = taskId,
                    boundaryZoneId = boundaryZoneId.id,
                    startEpochMs = start.toEpochMilli(),
                    createdAtEpochMs = clock.now().toEpochMilli(),
                )
            CreateActiveIntervalResult.Created(transaction.toModel())
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
}
