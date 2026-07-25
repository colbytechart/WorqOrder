package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkIntervalDao {
    @Query(
        """
        SELECT *
        FROM work_intervals
        WHERE task_id = :taskId
        ORDER BY start_epoch_ms ASC, ordinal ASC, id ASC
        """,
    )
    abstract suspend fun readIntervalsForOverlapValidation(
        taskId: String,
    ): List<WorkIntervalEntity>

    @Query(
        """
        SELECT *
        FROM work_intervals
        WHERE task_id = :taskId
        ORDER BY start_epoch_ms ASC, ordinal ASC, id ASC
        """,
    )
    abstract fun observeOrderedIntervals(taskId: String): Flow<List<WorkIntervalEntity>>

    @Query("SELECT * FROM work_intervals WHERE id = :intervalId LIMIT 1")
    abstract suspend fun readInterval(intervalId: String): WorkIntervalEntity?

    @Query(
        """
        SELECT COALESCE(SUM(stop_epoch_ms - start_epoch_ms), 0)
        FROM work_intervals
        WHERE task_id = :taskId
          AND stop_epoch_ms IS NOT NULL
        """,
    )
    abstract suspend fun readCompletedDurationMs(taskId: String): Long

    @Query("SELECT COUNT(*) FROM work_intervals WHERE task_id = :taskId")
    abstract suspend fun countIntervalsForTask(taskId: String): Int

    @Query("SELECT MAX(ordinal) FROM work_intervals WHERE task_id = :taskId")
    protected abstract suspend fun readMaxOrdinal(taskId: String): Int?

    @Query("SELECT COUNT(*) FROM daily_tasks WHERE id = :taskId")
    protected abstract suspend fun countTask(taskId: String): Int

    @Query("SELECT COUNT(*) FROM active_timer WHERE task_id = :taskId")
    protected abstract suspend fun countActiveTimerForTask(taskId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertIntervalInternal(interval: WorkIntervalEntity)

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

    @Query(
        """
        UPDATE work_intervals
        SET start_epoch_ms = :startEpochMs,
            stop_epoch_ms = :stopEpochMs,
            was_manually_edited = 1,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :intervalId
          AND task_id = :taskId
          AND stop_epoch_ms IS NOT NULL
          AND active_slot IS NULL
        """,
    )
    protected abstract suspend fun updateCompletedIntervalInternal(
        intervalId: String,
        taskId: String,
        startEpochMs: Long,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        DELETE FROM work_intervals
        WHERE id = :intervalId
          AND task_id = :taskId
          AND stop_epoch_ms IS NOT NULL
          AND active_slot IS NULL
        """,
    )
    protected abstract suspend fun deleteCompletedIntervalInternal(
        intervalId: String,
        taskId: String,
    ): Int

    @Transaction
    open suspend fun insertInterval(
        intervalId: String,
        taskId: String,
        startEpochMs: Long,
        stopEpochMs: Long,
        wasManuallyEdited: Boolean,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
    ): WorkIntervalEntity {
        require(intervalId.isNotBlank()) { "intervalId must not be blank" }
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(startEpochMs < stopEpochMs) {
            "A completed interval must stop after it starts"
        }

        val interval =
            WorkIntervalEntity(
                id = intervalId,
                taskId = taskId,
                ordinal = (readMaxOrdinal(taskId) ?: 0) + 1,
                startEpochMs = startEpochMs,
                stopEpochMs = stopEpochMs,
                activeSlot = null,
                wasManuallyEdited = wasManuallyEdited,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        insertIntervalInternal(interval)
        if (touchTask(taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Inserted interval ${interval.id} has no owning task $taskId",
            )
        }
        return interval
    }

    @Transaction
    open suspend fun addManualInterval(
        intervalId: String,
        taskId: String,
        startEpochMs: Long,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ManualIntervalWriteEntityResult {
        require(startEpochMs < stopEpochMs) {
            "A completed interval must stop after it starts"
        }
        if (countTask(taskId) == 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.TASK_NOT_FOUND,
            )
        }
        if (countActiveTimerForTask(taskId) != 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_TASK,
            )
        }
        if (
            overlaps(
                startEpochMs = startEpochMs,
                stopEpochMs = stopEpochMs,
                intervals = readIntervalsForOverlapValidation(taskId),
            )
        ) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.OVERLAP,
            )
        }
        val interval =
            WorkIntervalEntity(
                id = intervalId,
                taskId = taskId,
                ordinal = (readMaxOrdinal(taskId) ?: 0) + 1,
                startEpochMs = startEpochMs,
                stopEpochMs = stopEpochMs,
                activeSlot = null,
                wasManuallyEdited = true,
                createdAtEpochMs = updatedAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        insertIntervalInternal(interval)
        if (touchTask(taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Inserted manual interval ${interval.id} has no owning task $taskId",
            )
        }
        return ManualIntervalWriteEntityResult(
            status = ManualIntervalWriteStatus.SAVED,
            interval = interval,
        )
    }

    @Transaction
    open suspend fun updateManualInterval(
        intervalId: String,
        taskId: String,
        startEpochMs: Long,
        stopEpochMs: Long,
        updatedAtEpochMs: Long,
    ): ManualIntervalWriteEntityResult {
        require(startEpochMs < stopEpochMs) {
            "A completed interval must stop after it starts"
        }
        if (countTask(taskId) == 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.TASK_NOT_FOUND,
            )
        }
        if (countActiveTimerForTask(taskId) != 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_TASK,
            )
        }
        val existing =
            readInterval(intervalId)
                ?.takeIf { it.taskId == taskId }
                ?: return ManualIntervalWriteEntityResult(
                    ManualIntervalWriteStatus.INTERVAL_NOT_FOUND,
                )
        if (existing.stopEpochMs == null || existing.activeSlot != null) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_INTERVAL,
            )
        }
        if (
            overlaps(
                startEpochMs = startEpochMs,
                stopEpochMs = stopEpochMs,
                intervals = readIntervalsForOverlapValidation(taskId),
                excludingIntervalId = intervalId,
            )
        ) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.OVERLAP,
            )
        }
        if (
            updateCompletedIntervalInternal(
                intervalId = intervalId,
                taskId = taskId,
                startEpochMs = startEpochMs,
                stopEpochMs = stopEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            ) != 1
        ) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_INTERVAL,
            )
        }
        if (touchTask(taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Updated interval $intervalId has no owning task $taskId",
            )
        }
        return ManualIntervalWriteEntityResult(
            status = ManualIntervalWriteStatus.SAVED,
            interval =
                existing.copy(
                    startEpochMs = startEpochMs,
                    stopEpochMs = stopEpochMs,
                    wasManuallyEdited = true,
                    updatedAtEpochMs = updatedAtEpochMs,
                ),
        )
    }

    @Transaction
    open suspend fun deleteManualInterval(
        intervalId: String,
        taskId: String,
        updatedAtEpochMs: Long,
    ): ManualIntervalWriteEntityResult {
        if (countTask(taskId) == 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.TASK_NOT_FOUND,
            )
        }
        if (countActiveTimerForTask(taskId) != 0) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_TASK,
            )
        }
        val existing =
            readInterval(intervalId)
                ?.takeIf { it.taskId == taskId }
                ?: return ManualIntervalWriteEntityResult(
                    ManualIntervalWriteStatus.INTERVAL_NOT_FOUND,
                )
        if (existing.stopEpochMs == null || existing.activeSlot != null) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.RUNNING_INTERVAL,
            )
        }
        if (deleteCompletedIntervalInternal(intervalId, taskId) != 1) {
            return ManualIntervalWriteEntityResult(
                ManualIntervalWriteStatus.INTERVAL_NOT_FOUND,
            )
        }
        if (touchTask(taskId, updatedAtEpochMs) != 1) {
            throw PersistenceInvariantException(
                "Deleted interval $intervalId has no owning task $taskId",
            )
        }
        return ManualIntervalWriteEntityResult(
            ManualIntervalWriteStatus.DELETED,
        )
    }

    private fun overlaps(
        startEpochMs: Long,
        stopEpochMs: Long,
        intervals: List<WorkIntervalEntity>,
        excludingIntervalId: String? = null,
    ): Boolean =
        intervals
            .asSequence()
            .filter { it.id != excludingIntervalId }
            .any { interval ->
                val existingStop = interval.stopEpochMs
                if (existingStop == null) {
                    stopEpochMs > interval.startEpochMs
                } else {
                    startEpochMs < existingStop && interval.startEpochMs < stopEpochMs
                }
            }
}
