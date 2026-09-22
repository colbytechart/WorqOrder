package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationResult
import worq.order.model.TagCategory

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

    /** Stable read order for a portable logical snapshot. */
    @Query("SELECT * FROM daily_tasks ORDER BY id ASC")
    abstract suspend fun readAllTasksForPortableBackup(): List<DailyTaskEntity>

    /** Stable category/selection ordering for a portable logical snapshot. */
    @Query(
        """
        SELECT * FROM task_tag_snapshots
        ORDER BY task_id ASC, category ASC, selection_order ASC, id ASC
        """,
    )
    abstract suspend fun readAllTaskTagSnapshotsForPortableBackup(): List<TaskTagSnapshotEntity>

    /** Internal bulk primitive for a fully prevalidated portable-state replacement transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAllForPortableReplacement(tasks: List<DailyTaskEntity>)

    /** Internal bulk primitive for a fully prevalidated portable-state replacement transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAllSnapshotsForPortableReplacement(
        snapshots: List<TaskTagSnapshotEntity>,
    )

    /** Children must be removed before this parent table. */
    @Query("DELETE FROM task_tag_snapshots")
    abstract suspend fun deleteAllSnapshotsForPortableReplacement(): Int

    @Query("DELETE FROM daily_tasks")
    abstract suspend fun deleteAllForPortableReplacement(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDailyTask(task: DailyTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTaskTagSnapshots(
        snapshots: List<TaskTagSnapshotEntity>,
    )

    @Query(
        """
        SELECT *
        FROM task_tag_snapshots
        WHERE task_id = :taskId
        ORDER BY category ASC, selection_order ASC, id ASC
        """,
    )
    abstract fun observeTaskTagSnapshots(taskId: String): Flow<List<TaskTagSnapshotEntity>>

    @Query(
        """
        SELECT *
        FROM task_tag_snapshots
        WHERE task_id = :taskId
        ORDER BY category ASC, selection_order ASC, id ASC
        """,
    )
    protected abstract suspend fun readTaskTagSnapshots(
        taskId: String,
    ): List<TaskTagSnapshotEntity>

    @Query("DELETE FROM task_tag_snapshots WHERE task_id = :taskId")
    protected abstract suspend fun deleteTaskTagSnapshots(taskId: String): Int

    @Transaction
    open suspend fun insertDailyTaskWithSnapshots(
        task: DailyTaskEntity,
        snapshots: List<TaskTagSnapshotEntity>,
    ) {
        assertSnapshotsBelongTo(task.id, snapshots)
        insertDailyTask(task)
        if (snapshots.isNotEmpty()) {
            insertTaskTagSnapshots(snapshots)
        }
    }

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
        snapshots: List<TaskTagSnapshotEntity> = emptyList(),
    ): TaskCreationEntityResult {
        assertSnapshotsBelongTo(task.id, snapshots)
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
        if (snapshots.isNotEmpty()) {
            insertTaskTagSnapshots(snapshots)
        }
        return TaskCreationEntityResult(
            status = TaskCreationWriteStatus.CREATED,
            task = assignedTask,
        )
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
            notes = :notes,
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
        notes: String,
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
        notes: String,
        updatedAtEpochMs: Long,
        snapshots: List<TaskTagSnapshotEntity>? = null,
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
                notes = notes,
                updatedAtEpochMs = updatedAtEpochMs,
            ) != 1
        ) {
            return TaskMetadataWriteEntityResult(TaskMetadataWriteStatus.TASK_NOT_FOUND)
        }
        if (snapshots != null) {
            assertSnapshotsBelongTo(taskId, snapshots)
            deleteTaskTagSnapshots(taskId)
            if (snapshots.isNotEmpty()) {
                insertTaskTagSnapshots(snapshots)
            }
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
            tagSnapshots = readTaskTagSnapshots(taskId),
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
                tagSnapshots = readTaskTagSnapshots(taskWithClient.task.id),
            )
        }

    private fun assertSnapshotsBelongTo(
        taskId: String,
        snapshots: List<TaskTagSnapshotEntity>,
    ) {
        require(snapshots.all { snapshot ->
            val normalizedText = TagTextNormalizer.validate(snapshot.textSnapshot)
            snapshot.taskId == taskId &&
                snapshot.category in TAG_CATEGORY_VALUES &&
                normalizedText is TagTextValidationResult.Valid &&
                normalizedText.text.displayText == snapshot.textSnapshot &&
                (
                    snapshot.sourceTagId == null ||
                        (
                            snapshot.sourceTagId.isNotBlank() &&
                                snapshot.sourceTagId == snapshot.sourceTagId.trim()
                        )
                ) &&
                snapshot.selectionOrder >= 0
        }) { "Task Tag snapshots must be valid and belong to their task" }

        snapshots.groupBy(TaskTagSnapshotEntity::category).forEach { (category, categoryRows) ->
            val orders = categoryRows.map(TaskTagSnapshotEntity::selectionOrder).sorted()
            require(orders == orders.indices.toList()) {
                "$category Tag snapshot order must be contiguous and zero-based"
            }
            val sourceTagIds = categoryRows.mapNotNull(TaskTagSnapshotEntity::sourceTagId)
            require(sourceTagIds.size == sourceTagIds.distinct().size) {
                "A task cannot select the same source Tag twice in $category"
            }
        }
    }

    private companion object {
        val TAG_CATEGORY_VALUES = TagCategory.entries.map(TagCategory::name).toSet()
    }
}
