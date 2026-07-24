package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Query(
        """
        SELECT
            task.*,
            client.name AS joined_client_name,
            client.is_active AS joined_client_is_active,
            COALESCE(
                SUM(
                    CASE
                        WHEN interval.stop_epoch_ms IS NOT NULL
                            THEN interval.stop_epoch_ms - interval.start_epoch_ms
                        ELSE 0
                    END
                ),
                0
            ) AS completed_duration_ms
        FROM daily_tasks AS task
        INNER JOIN clients AS client ON client.id = task.client_id
        LEFT JOIN work_intervals AS interval ON interval.task_id = task.id
        WHERE task.work_date_epoch_day = :workDateEpochDay
        GROUP BY task.id
        ORDER BY task.created_at_epoch_ms ASC, task.id ASC
        """,
    )
    abstract fun observeTasksForWorkDate(
        workDateEpochDay: Long,
    ): Flow<List<TaskListItemEntity>>

    @Query(
        """
        SELECT
            task.*,
            client.name AS joined_client_name,
            client.canonical_name AS joined_client_canonical_name,
            client.is_active AS joined_client_is_active,
            client.created_at_epoch_ms AS joined_client_created_at_epoch_ms,
            client.updated_at_epoch_ms AS joined_client_updated_at_epoch_ms,
            client.archived_at_epoch_ms AS joined_client_archived_at_epoch_ms
        FROM daily_tasks AS task
        INNER JOIN clients AS client ON client.id = task.client_id
        WHERE task.id = :taskId
        LIMIT 1
        """,
    )
    abstract suspend fun readTaskWithClient(taskId: String): TaskWithClientEntity?

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId LIMIT 1")
    abstract suspend fun readTask(taskId: String): DailyTaskEntity?

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
    abstract suspend fun findCorrespondingTask(
        seriesId: String,
        workDateEpochDay: Long,
        zoneId: String,
    ): DailyTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDailyTask(task: DailyTaskEntity)

    @Query(
        """
        UPDATE daily_tasks
        SET client_id = :clientId,
            description = :description,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :taskId
        """,
    )
    abstract suspend fun updateTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query("DELETE FROM daily_tasks WHERE id = :taskId")
    abstract suspend fun deleteTask(taskId: String): Int

    @Query(
        """
        SELECT *
        FROM work_intervals
        WHERE task_id = :taskId
        ORDER BY start_epoch_ms ASC, ordinal ASC, id ASC
        """,
    )
    protected abstract suspend fun readOrderedIntervals(
        taskId: String,
    ): List<WorkIntervalEntity>

    @Transaction
    open suspend fun readTaskWithOrderedIntervals(
        taskId: String,
    ): TaskWithOrderedIntervalsEntity? {
        val taskWithClient = readTaskWithClient(taskId) ?: return null
        return TaskWithOrderedIntervalsEntity(
            taskWithClient = taskWithClient,
            intervals = readOrderedIntervals(taskId),
        )
    }
}
