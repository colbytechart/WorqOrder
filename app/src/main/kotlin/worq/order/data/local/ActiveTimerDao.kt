package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ActiveTimerDao {
    @Query(
        """
        SELECT *
        FROM active_timer
        WHERE singleton_id = 1
        LIMIT 1
        """,
    )
    abstract suspend fun readActiveTimer(): ActiveTimerEntity?

    @Query(
        """
        SELECT *
        FROM active_timer
        WHERE singleton_id = 1
        LIMIT 1
        """,
    )
    abstract fun observeActiveTimer(): Flow<ActiveTimerEntity?>

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId LIMIT 1")
    protected abstract suspend fun readTaskInternal(taskId: String): DailyTaskEntity?

    @Query(
        """
        SELECT *
        FROM daily_tasks
        WHERE series_id = :seriesId
          AND work_date_epoch_day = :workDateEpochDay
          AND zone_id = :zoneId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findCorrespondingTaskInternal(
        seriesId: String,
        workDateEpochDay: Long,
        zoneId: String,
    ): DailyTaskEntity?

    @Query("SELECT * FROM work_intervals WHERE id = :intervalId LIMIT 1")
    protected abstract suspend fun readIntervalInternal(
        intervalId: String,
    ): WorkIntervalEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM work_intervals
        WHERE stop_epoch_ms IS NULL
           OR active_slot IS NOT NULL
        """,
    )
    protected abstract suspend fun countOpenIntervalCandidates(): Int

    @Query("SELECT MAX(ordinal) FROM work_intervals WHERE task_id = :taskId")
    protected abstract suspend fun readMaxOrdinal(taskId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertIntervalInternal(interval: WorkIntervalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTaskInternal(task: DailyTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertActiveTimerInternal(activeTimer: ActiveTimerEntity)

    @Query(
        """
        UPDATE work_intervals
        SET stop_epoch_ms = :stopEpochMs,
            active_slot = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :intervalId
          AND task_id = :taskId
          AND stop_epoch_ms IS NULL
          AND active_slot = 1
        """,
    )
    protected abstract suspend fun closeIntervalInternal(
        intervalId: String,
        taskId: String,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        DELETE FROM active_timer
        WHERE singleton_id = 1
          AND interval_id = :intervalId
          AND task_id = :taskId
        """,
    )
    protected abstract suspend fun clearActiveTimerInternal(
        intervalId: String,
        taskId: String,
    ): Int

    @Query(
        """
        UPDATE active_timer
        SET interval_id = :newIntervalId,
            task_id = :newTaskId,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE singleton_id = 1
          AND interval_id = :expectedIntervalId
          AND task_id = :expectedTaskId
        """,
    )
    protected abstract suspend fun retargetActiveTimerInternal(
        expectedIntervalId: String,
        expectedTaskId: String,
        newIntervalId: String,
        newTaskId: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE daily_tasks
        SET updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :taskId
        """,
    )
    protected abstract suspend fun touchTask(
        taskId: String,
        updatedAtEpochMs: Long,
    ): Int

    @Transaction
    open suspend fun createActiveIntervalAndTimer(
        intervalId: String,
        taskId: String,
        boundaryZoneId: String,
        startEpochMs: Long,
        createdAtEpochMs: Long,
    ): ActiveTimerTransactionEntity {
        require(intervalId.isNotBlank()) { "intervalId must not be blank" }
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(boundaryZoneId.isNotBlank()) { "boundaryZoneId must not be blank" }

        if (readActiveTimer() != null || countOpenIntervalCandidates() != 0) {
            throw ActiveTimerAlreadyExistsException()
        }

        val interval =
            WorkIntervalEntity(
                id = intervalId,
                taskId = taskId,
                ordinal = (readMaxOrdinal(taskId) ?: 0) + 1,
                startEpochMs = startEpochMs,
                stopEpochMs = null,
                activeSlot = WorkIntervalEntity.ACTIVE_SLOT,
                wasManuallyEdited = false,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = createdAtEpochMs,
            )
        val activeTimer =
            ActiveTimerEntity(
                intervalId = intervalId,
                taskId = taskId,
                boundaryZoneId = boundaryZoneId,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = createdAtEpochMs,
            )

        insertIntervalInternal(interval)
        insertActiveTimerInternal(activeTimer)
        if (touchTask(taskId, createdAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Active interval $intervalId has no owning task $taskId",
            )
        }
        return ActiveTimerTransactionEntity(
            activeTimer = activeTimer,
            interval = interval,
        )
    }

    @Transaction
    open suspend fun readActiveTimerSnapshot(): ActiveTimerTransactionEntity? {
        val activeTimer = readActiveTimer()
        val openCandidateCount = countOpenIntervalCandidates()
        if (activeTimer == null) {
            if (openCandidateCount != 0) {
                throw PersistenceInvariantException(
                    "Found $openCandidateCount open interval candidate(s) without active timer",
                )
            }
            return null
        }
        if (openCandidateCount != 1) {
            throw PersistenceInvariantException(
                "Active timer requires exactly one open interval candidate; found " +
                    openCandidateCount,
            )
        }
        val interval =
            readIntervalInternal(activeTimer.intervalId)
                ?: throw PersistenceInvariantException(
                    "Active timer points to missing interval ${activeTimer.intervalId}",
                )
        validateActivePair(activeTimer, interval)
        return ActiveTimerTransactionEntity(
            activeTimer = activeTimer,
            interval = interval,
        )
    }

    @Transaction
    open suspend fun normalizeActiveInterval(
        expectedIntervalId: String,
        continuations: List<TimerContinuationEntityInput>,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? =
        applyContinuations(
            expectedIntervalId = expectedIntervalId,
            continuations = continuations,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    @Transaction
    open suspend fun closeActiveIntervalAndClearTimer(
        expectedIntervalId: String,
        continuations: List<TimerContinuationEntityInput>,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? {
        val continued =
            applyContinuations(
                expectedIntervalId = expectedIntervalId,
                continuations = continuations,
                updatedAtEpochMs = updatedAtEpochMs,
            ) ?: return null
        val activeTimer = continued.activeTimer
        val interval = continued.interval
        require(stopEpochMs > interval.startEpochMs) {
            "An active interval must stop after it starts"
        }

        if (
            closeIntervalInternal(
                intervalId = interval.id,
                taskId = interval.taskId,
                stopEpochMs = stopEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            ) != 1
        ) {
            throw PersistenceInvariantException(
                "Active interval ${interval.id} could not be closed",
            )
        }
        if (clearActiveTimerInternal(interval.id, interval.taskId) != 1) {
            throw PersistenceInvariantException(
                "Active timer for interval ${interval.id} could not be cleared",
            )
        }
        if (touchTask(interval.taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Closed interval ${interval.id} has no owning task ${interval.taskId}",
            )
        }

        return ActiveTimerTransactionEntity(
            activeTimer = activeTimer.copy(updatedAtEpochMs = updatedAtEpochMs),
            interval =
                interval.copy(
                    stopEpochMs = stopEpochMs,
                    activeSlot = null,
                    updatedAtEpochMs = updatedAtEpochMs,
                ),
        )
    }

    @Transaction
    open suspend fun closeActiveIntervalAndClearTimer(
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? {
        val activeTimer = readActiveTimer() ?: return null
        return closeActiveIntervalAndClearTimer(
            expectedIntervalId = activeTimer.intervalId,
            continuations = emptyList(),
            stopEpochMs = stopEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    private suspend fun applyContinuations(
        expectedIntervalId: String,
        continuations: List<TimerContinuationEntityInput>,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? {
        require(expectedIntervalId.isNotBlank()) {
            "expectedIntervalId must not be blank"
        }

        var activeTimer = readActiveTimer() ?: return null
        if (activeTimer.intervalId != expectedIntervalId) {
            return null
        }
        var interval =
            readIntervalInternal(activeTimer.intervalId)
                ?: throw PersistenceInvariantException(
                    "Active timer points to missing interval ${activeTimer.intervalId}",
                )
        validateActivePair(activeTimer, interval)
        var task =
            readTaskInternal(activeTimer.taskId)
                ?: throw PersistenceInvariantException(
                    "Active timer points to missing task ${activeTimer.taskId}",
                )

        continuations.forEach { continuation ->
            require(continuation.zoneId == activeTimer.boundaryZoneId) {
                "Continuation zone must match the active timer boundary zone"
            }
            require(continuation.boundaryEpochMs > interval.startEpochMs) {
                "Continuation boundary must be after the open interval start"
            }
            require(continuation.proposedTaskId.isNotBlank()) {
                "proposedTaskId must not be blank"
            }
            require(continuation.proposedIntervalId.isNotBlank()) {
                "proposedIntervalId must not be blank"
            }

            if (
                closeIntervalInternal(
                    intervalId = interval.id,
                    taskId = interval.taskId,
                    stopEpochMs = continuation.boundaryEpochMs,
                    updatedAtEpochMs = updatedAtEpochMs,
                ) != 1
            ) {
                throw PersistenceInvariantException(
                    "Active interval ${interval.id} could not be split",
                )
            }
            if (touchTask(task.id, updatedAtEpochMs) != 1) {
                throw PersistenceInvariantException(
                    "Split interval ${interval.id} has no owning task ${task.id}",
                )
            }

            val nextTask =
                findCorrespondingTaskInternal(
                    seriesId = task.seriesId,
                    workDateEpochDay = continuation.workDateEpochDay,
                    zoneId = continuation.zoneId,
                ) ?: DailyTaskEntity(
                    id = continuation.proposedTaskId,
                    seriesId = task.seriesId,
                    clientId = task.clientId,
                    description = task.description,
                    hardwareSoftwarePurchases = task.hardwareSoftwarePurchases,
                    employeeId = task.employeeId,
                    employeeNameSnapshot = task.employeeNameSnapshot,
                    workType = task.workType,
                    billingStatus = task.billingStatus,
                    mileage = task.mileage,
                    workDateEpochDay = continuation.workDateEpochDay,
                    zoneId = continuation.zoneId,
                    createdAtEpochMs = updatedAtEpochMs,
                    updatedAtEpochMs = updatedAtEpochMs,
                ).also { insertTaskInternal(it) }

            val nextInterval =
                WorkIntervalEntity(
                    id = continuation.proposedIntervalId,
                    taskId = nextTask.id,
                    ordinal = (readMaxOrdinal(nextTask.id) ?: 0) + 1,
                    startEpochMs = continuation.boundaryEpochMs,
                    stopEpochMs = null,
                    activeSlot = WorkIntervalEntity.ACTIVE_SLOT,
                    wasManuallyEdited = false,
                    createdAtEpochMs = updatedAtEpochMs,
                    updatedAtEpochMs = updatedAtEpochMs,
                )
            insertIntervalInternal(nextInterval)
            if (
                retargetActiveTimerInternal(
                    expectedIntervalId = interval.id,
                    expectedTaskId = interval.taskId,
                    newIntervalId = nextInterval.id,
                    newTaskId = nextTask.id,
                    updatedAtEpochMs = updatedAtEpochMs,
                ) != 1
            ) {
                throw PersistenceInvariantException(
                    "Active timer could not be retargeted to ${nextInterval.id}",
                )
            }
            if (touchTask(nextTask.id, updatedAtEpochMs) != 1) {
                throw PersistenceInvariantException(
                    "Continuation interval ${nextInterval.id} has no owning task ${nextTask.id}",
                )
            }

            activeTimer =
                activeTimer.copy(
                    intervalId = nextInterval.id,
                    taskId = nextTask.id,
                    updatedAtEpochMs = updatedAtEpochMs,
                )
            interval = nextInterval
            task = nextTask
        }

        return ActiveTimerTransactionEntity(
            activeTimer = activeTimer,
            interval = interval,
        )
    }

    private fun validateActivePair(
        activeTimer: ActiveTimerEntity,
        interval: WorkIntervalEntity,
    ) {
        if (
            interval.taskId != activeTimer.taskId ||
            interval.stopEpochMs != null ||
            interval.activeSlot != WorkIntervalEntity.ACTIVE_SLOT
        ) {
            throw PersistenceInvariantException(
                "Active timer and interval ${interval.id} are inconsistent",
            )
        }
    }
}
