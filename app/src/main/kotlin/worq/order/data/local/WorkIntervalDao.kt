package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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
}
