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

    @Query(
        """
        SELECT *
        FROM work_intervals
        WHERE task_id = :taskId
        ORDER BY start_epoch_ms ASC, id ASC
        """,
    )
    protected abstract suspend fun readTaskIntervalsInternal(
        taskId: String,
    ): List<WorkIntervalEntity>

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
        repeatedTaskId: String = intervalId,
    ): StartTimerTransactionEntity {
        require(intervalId.isNotBlank()) { "intervalId must not be blank" }
        require(repeatedTaskId.isNotBlank()) { "repeatedTaskId must not be blank" }
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(boundaryZoneId.isNotBlank()) { "boundaryZoneId must not be blank" }

        if (readActiveTimer() != null || countOpenIntervalCandidates() != 0) {
            throw ActiveTimerAlreadyExistsException()
        }

        val sourceTask =
            readTaskInternal(taskId)
                ?: throw PersistenceInvariantException(
                    "Cannot start missing task $taskId",
                )
        val existingIntervals = readTaskIntervalsInternal(taskId)
        val repeatedTaskCreated: Boolean
        val startedTask =
            when (existingIntervals.size) {
                0 -> {
                    repeatedTaskCreated = false
                    sourceTask
                }
                1 -> {
                    val existing = existingIntervals.single()
                    if (existing.stopEpochMs == null || existing.activeSlot != null) {
                        throw PersistenceInvariantException(
                            "Task $taskId has an open interval without authoritative active state",
                        )
                    }
                    val copy =
                        sourceTask.copy(
                            id = repeatedTaskId,
                            createdAtEpochMs = createdAtEpochMs,
                            updatedAtEpochMs = createdAtEpochMs,
                        )
                    insertTaskInternal(copy)
                    repeatedTaskCreated = true
                    copy
                }
                else ->
                    throw PersistenceInvariantException(
                        "Schema-5 task $taskId owns ${existingIntervals.size} intervals",
                    )
            }

        val interval =
            WorkIntervalEntity(
                id = intervalId,
                taskId = startedTask.id,
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
                taskId = startedTask.id,
                boundaryZoneId = boundaryZoneId,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = createdAtEpochMs,
            )

        insertIntervalInternal(interval)
        insertActiveTimerInternal(activeTimer)
        if (touchTask(startedTask.id, createdAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Active interval $intervalId has no owning task ${startedTask.id}",
            )
        }
        return StartTimerTransactionEntity(
            startedTask = startedTask,
            activeTimer = activeTimer,
            interval = interval,
            repeatedTaskCreated = repeatedTaskCreated,
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

    /**
     * Schema-5 boundary policy: close once at the first crossed local midnight and clear the
     * authoritative timer without creating a continuation task or interval.
     */
    @Transaction
    open suspend fun closeActiveIntervalAtBoundary(
        expectedIntervalId: String,
        boundaryEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? {
        require(expectedIntervalId.isNotBlank()) {
            "expectedIntervalId must not be blank"
        }
        val activeTimer = readActiveTimer() ?: return null
        if (activeTimer.intervalId != expectedIntervalId) {
            return null
        }
        val interval =
            readIntervalInternal(activeTimer.intervalId)
                ?: throw PersistenceInvariantException(
                    "Active timer points to missing interval ${activeTimer.intervalId}",
                )
        validateActivePair(activeTimer, interval)
        require(boundaryEpochMs > interval.startEpochMs) {
            "The boundary must be after the active interval start"
        }

        if (
            closeIntervalInternal(
                intervalId = interval.id,
                taskId = interval.taskId,
                stopEpochMs = boundaryEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            ) != 1
        ) {
            throw PersistenceInvariantException(
                "Active interval ${interval.id} could not be closed at its boundary",
            )
        }
        if (clearActiveTimerInternal(interval.id, interval.taskId) != 1) {
            throw PersistenceInvariantException(
                "Active timer for interval ${interval.id} could not be cleared",
            )
        }
        if (touchTask(interval.taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Boundary-closed interval ${interval.id} has no owning task ${interval.taskId}",
            )
        }

        return ActiveTimerTransactionEntity(
            activeTimer = activeTimer.copy(updatedAtEpochMs = updatedAtEpochMs),
            interval =
                interval.copy(
                    stopEpochMs = boundaryEpochMs,
                    activeSlot = null,
                    updatedAtEpochMs = updatedAtEpochMs,
                ),
        )
    }

    @Transaction
    open suspend fun closeActiveIntervalAndClearTimer(
        expectedIntervalId: String,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ActiveTimerTransactionEntity? {
        require(expectedIntervalId.isNotBlank()) {
            "expectedIntervalId must not be blank"
        }
        val activeTimer = readActiveTimer() ?: return null
        if (activeTimer.intervalId != expectedIntervalId) {
            return null
        }
        val interval =
            readIntervalInternal(activeTimer.intervalId)
                ?: throw PersistenceInvariantException(
                    "Active timer points to missing interval ${activeTimer.intervalId}",
                )
        validateActivePair(activeTimer, interval)
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
            stopEpochMs = stopEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
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
