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

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId LIMIT 1")
    abstract fun observeTask(taskId: String): Flow<DailyTaskEntity?>

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
        WHERE task.work_date_epoch_day = :workDateEpochDay
        ORDER BY task.created_at_epoch_ms ASC, task.id ASC
        """,
    )
    protected abstract suspend fun readTasksWithClientsForWorkDate(
        workDateEpochDay: Long,
    ): List<TaskWithClientEntity>

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
    abstract fun observeTaskWithClient(taskId: String): Flow<TaskWithClientEntity?>

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
        SELECT is_active
        FROM clients
        WHERE id = :clientId
        LIMIT 1
        """,
    )
    protected abstract suspend fun readClientActive(clientId: String): Boolean?

    @Query(
        """
        SELECT name
        FROM employees
        WHERE id = :employeeId AND is_active = 1
        LIMIT 1
        """,
    )
    protected abstract suspend fun readActiveEmployeeName(employeeId: String): String?

    @Query(
        """
        SELECT COUNT(*)
        FROM active_timer
        WHERE task_id = :taskId
        """,
    )
    protected abstract suspend fun countActiveTimerForTask(taskId: String): Int

    @Transaction
    open suspend fun insertDailyTaskIfReferencesActive(
        task: DailyTaskEntity,
    ): TaskCreationEntityResult {
        if (readClientActive(task.clientId) != true) {
            return TaskCreationEntityResult(TaskCreationWriteStatus.CLIENT_UNAVAILABLE)
        }
        val employeeName =
            task.employeeId?.let { employeeId ->
                readActiveEmployeeName(employeeId)
                    ?: return TaskCreationEntityResult(
                        TaskCreationWriteStatus.EMPLOYEE_UNAVAILABLE,
                    )
            }
        val assignedTask =
            if (employeeName == null) {
                task
            } else {
                task.copy(employeeNameSnapshot = employeeName)
            }
        insertDailyTask(assignedTask)
        return TaskCreationEntityResult(
            status = TaskCreationWriteStatus.CREATED,
            task = assignedTask,
        )
    }

    @Transaction
    open suspend fun findOrCreateDailyTaskCopy(
        sourceTaskId: String,
        proposedTaskId: String,
        workDateEpochDay: Long,
        zoneId: String,
        createdAtEpochMs: Long,
    ): DailyTaskEntity? {
        require(proposedTaskId.isNotBlank()) { "proposedTaskId must not be blank" }
        require(zoneId.isNotBlank()) { "zoneId must not be blank" }

        val source = readTask(sourceTaskId) ?: return null
        findCorrespondingTask(
            seriesId = source.seriesId,
            workDateEpochDay = workDateEpochDay,
            zoneId = zoneId,
        )?.let { existing ->
            return existing
        }

        val copy =
            DailyTaskEntity(
                id = proposedTaskId,
                seriesId = source.seriesId,
                clientId = source.clientId,
                description = source.description,
                hardwareSoftwarePurchases = source.hardwareSoftwarePurchases,
                employeeId = source.employeeId,
                employeeNameSnapshot = source.employeeNameSnapshot,
                workType = source.workType,
                billingStatus = source.billingStatus,
                mileage = source.mileage,
                workDateEpochDay = workDateEpochDay,
                zoneId = zoneId,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = createdAtEpochMs,
            )
        insertDailyTask(copy)
        return copy
    }

    @Query(
        """
        UPDATE daily_tasks
        SET client_id = :clientId,
            description = :description,
            hardware_software_purchases = :hardwareSoftwarePurchases,
            employee_id = :employeeId,
            employee_name_snapshot = :employeeNameSnapshot,
            work_type = :workType,
            billing_status = :billingStatus,
            mileage = :mileage,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :taskId
        """,
    )
    protected abstract suspend fun updateTaskMetadataInternal(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
        employeeId: String?,
        employeeNameSnapshot: String,
        workType: String,
        billingStatus: String?,
        mileage: String?,
        updatedAtEpochMs: Long,
    ): Int

    @Transaction
    open suspend fun updateStoppedTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
        employeeId: String?,
        workType: String,
        billingStatus: String?,
        mileage: String?,
        updatedAtEpochMs: Long,
    ): TaskMetadataWriteEntityResult {
        val currentTask = readTask(taskId)
        if (currentTask == null) {
            return TaskMetadataWriteEntityResult(TaskMetadataWriteStatus.TASK_NOT_FOUND)
        }
        if (countActiveTimerForTask(taskId) != 0) {
            return TaskMetadataWriteEntityResult(TaskMetadataWriteStatus.RUNNING_TASK)
        }
        if (readClientActive(clientId) != true) {
            return TaskMetadataWriteEntityResult(TaskMetadataWriteStatus.CLIENT_UNAVAILABLE)
        }
        val assignedEmployeeId: String?
        val employeeName: String
        if (employeeId == null) {
            assignedEmployeeId = currentTask.employeeId
            employeeName = currentTask.employeeNameSnapshot
        } else {
            assignedEmployeeId = employeeId
            employeeName =
                readActiveEmployeeName(employeeId)
                    ?: return TaskMetadataWriteEntityResult(
                        TaskMetadataWriteStatus.EMPLOYEE_UNAVAILABLE,
                    )
        }
        if (
            updateTaskMetadataInternal(
                taskId = taskId,
                clientId = clientId,
                description = description,
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                employeeId = assignedEmployeeId,
                employeeNameSnapshot = employeeName,
                workType = workType,
                billingStatus = billingStatus,
                mileage = mileage,
                updatedAtEpochMs = updatedAtEpochMs,
            ) != 1
        ) {
            return TaskMetadataWriteEntityResult(TaskMetadataWriteStatus.TASK_NOT_FOUND)
        }
        return TaskMetadataWriteEntityResult(
            status = TaskMetadataWriteStatus.UPDATED,
            task = readTask(taskId),
        )
    }

    @Query("DELETE FROM daily_tasks WHERE id = :taskId")
    abstract suspend fun deleteTask(taskId: String): Int

    @Transaction
    open suspend fun deleteStoppedTask(taskId: String): TaskDeleteStatus {
        if (readTask(taskId) == null) {
            return TaskDeleteStatus.TASK_NOT_FOUND
        }
        if (countActiveTimerForTask(taskId) != 0) {
            return TaskDeleteStatus.RUNNING_TASK
        }
        return if (deleteTask(taskId) == 1) {
            TaskDeleteStatus.DELETED
        } else {
            TaskDeleteStatus.TASK_NOT_FOUND
        }
    }

    @Query(
        """
        SELECT *
        FROM work_intervals
        WHERE task_id = :taskId
        ORDER BY start_epoch_ms ASC, id ASC
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

    @Transaction
    open suspend fun readTasksWithOrderedIntervalsForWorkDate(
        workDateEpochDay: Long,
    ): List<TaskWithOrderedIntervalsEntity> =
        readTasksWithClientsForWorkDate(workDateEpochDay).map { taskWithClient ->
            TaskWithOrderedIntervalsEntity(
                taskWithClient = taskWithClient,
                intervals = readOrderedIntervals(taskWithClient.task.id),
            )
        }
}
