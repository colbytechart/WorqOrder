package worq.order.data.local

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import worq.order.data.CreateDailyTaskResult
import worq.order.data.DeleteTaskResult
import worq.order.data.EntityIdGenerator
import worq.order.data.ManualIntervalPersistenceResult
import worq.order.data.NewDailyTask
import worq.order.data.NormalizedTaskMetadata
import worq.order.data.TaskMetadataValidationResult
import worq.order.data.TaskMetadataValidator
import worq.order.data.TaskRepository
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationResult
import worq.order.data.UpdateTaskMetadataResult
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType
import worq.order.model.BillingStatus
import worq.order.model.TagCategory
import worq.order.model.TaskTagSnapshotDraft
import worq.order.timer.UtcClock

class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val workIntervalDao: WorkIntervalDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : TaskRepository {
    override fun observeTasksForDate(workDate: LocalDate): Flow<List<TaskListItem>> =
        taskDao.observeTasksForWorkDate(workDate.toEpochDay()).flatMapLatest { tasks ->
            if (tasks.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    tasks.map { task ->
                        taskDao.observeTaskTagSnapshots(task.task.id).map { snapshots ->
                            snapshots
                                .filter { it.category == TagCategory.DESCRIPTION.name }
                                .sortedWith(
                                    compareBy(TaskTagSnapshotEntity::selectionOrder)
                                        .thenBy(TaskTagSnapshotEntity::id),
                                ).map(TaskTagSnapshotEntity::textSnapshot)
                        }
                    },
                ) { tagTexts ->
                    tasks.mapIndexed { index, task ->
                        task.toModel().copy(descriptionTagTexts = tagTexts[index])
                    }
                }
            }
        }

    override fun observeTask(taskId: String): Flow<DailyTask?> =
        taskDao.observeTask(taskId).map { task -> task?.toModel() }

    override fun observeTaskWithIntervals(taskId: String): Flow<TaskWithIntervals?> =
        combine(
            taskDao.observeTaskWithClient(taskId),
            workIntervalDao.observeOrderedIntervals(taskId),
            taskDao.observeTaskTagSnapshots(taskId),
        ) { taskWithClient, intervals, snapshots ->
            taskWithClient?.let {
                TaskWithIntervals(
                    taskWithClient = it.toModel(),
                    intervals = intervals.map(WorkIntervalEntity::toModel),
                    tagSnapshots = snapshots.map(TaskTagSnapshotEntity::toModel),
                )
            }
        }

    override suspend fun readTaskWithClient(taskId: String): TaskWithClient? =
        taskDao.readTaskWithClient(taskId)?.toModel()

    override suspend fun readTaskWithIntervals(taskId: String): TaskWithIntervals? =
        taskDao.readTaskWithOrderedIntervals(taskId)?.toModel()

    override suspend fun readTasksWithIntervalsForDate(
        workDate: LocalDate,
    ): List<TaskWithIntervals> =
        taskDao
            .readTasksWithOrderedIntervalsForWorkDate(workDate.toEpochDay())
            .map(TaskWithOrderedIntervalsEntity::toModel)

    override suspend fun insertDailyTask(newTask: NewDailyTask): DailyTask {
        require(newTask.clientId.isNotBlank()) { "clientId must not be blank" }
        val write = newTask.toWrite()
        taskDao.insertDailyTaskWithSnapshots(write.task, write.snapshots)
        return write.task.toModel()
    }

    override suspend fun createDailyTask(newTask: NewDailyTask): CreateDailyTaskResult {
        require(newTask.clientId.isNotBlank()) { "clientId must not be blank" }
        val write = newTask.toWrite()
        val result = taskDao.insertDailyTaskIfReferencesActive(write.task, write.snapshots)
        return when (result.status) {
            TaskCreationWriteStatus.CREATED ->
                CreateDailyTaskResult.Created(requireNotNull(result.task).toModel())
            TaskCreationWriteStatus.CLIENT_UNAVAILABLE ->
                CreateDailyTaskResult.ClientUnavailable
            TaskCreationWriteStatus.EMPLOYEE_UNAVAILABLE ->
                CreateDailyTaskResult.EmployeeUnavailable
        }
    }

    override suspend fun updateTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
        employeeId: String?,
        workType: WorkType,
        billingStatus: BillingStatus?,
        mileage: String?,
        notes: String,
        descriptionTagSnapshots: List<TaskTagSnapshotDraft>?,
        hardwareSoftwarePurchaseTagSnapshots: List<TaskTagSnapshotDraft>?,
    ): UpdateTaskMetadataResult {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        val metadata =
            normalizeMetadata(
                description = description,
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                workType = workType,
                billingStatus = billingStatus,
                mileage = mileage,
                notes = notes,
                descriptionTagSnapshots = descriptionTagSnapshots.orEmpty(),
                hardwareSoftwarePurchaseTagSnapshots =
                    hardwareSoftwarePurchaseTagSnapshots.orEmpty(),
            )
        require(
            (descriptionTagSnapshots == null) ==
                (hardwareSoftwarePurchaseTagSnapshots == null),
        ) {
            "Both Tag snapshot categories must be supplied together"
        }
        val nowEpochMs = clock.now().toEpochMilli()
        val snapshots =
            if (descriptionTagSnapshots == null) {
                null
            } else {
                snapshotEntities(
                    taskId = taskId,
                    category = TagCategory.DESCRIPTION,
                    drafts = descriptionTagSnapshots,
                    createdAtEpochMs = nowEpochMs,
                ) + snapshotEntities(
                    taskId = taskId,
                    category = TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                    drafts = hardwareSoftwarePurchaseTagSnapshots.orEmpty(),
                    createdAtEpochMs = nowEpochMs,
                )
            }
        val result =
            taskDao.updateStoppedTaskMetadata(
                taskId = taskId,
                clientId = clientId,
                description = metadata.description,
                hardwareSoftwarePurchases = metadata.hardwareSoftwarePurchases,
                employeeId = employeeId,
                workType = metadata.workType.name,
                billingStatus = metadata.billingStatus?.name,
                mileage = metadata.mileage,
                notes = metadata.notes,
                updatedAtEpochMs = nowEpochMs,
                snapshots = snapshots,
            )
        return when (result.status) {
            TaskMetadataWriteStatus.UPDATED ->
                UpdateTaskMetadataResult.Updated(requireNotNull(result.task).toModel())
            TaskMetadataWriteStatus.TASK_NOT_FOUND ->
                UpdateTaskMetadataResult.TaskNotFound
            TaskMetadataWriteStatus.CLIENT_UNAVAILABLE ->
                UpdateTaskMetadataResult.ClientUnavailable
            TaskMetadataWriteStatus.EMPLOYEE_UNAVAILABLE ->
                UpdateTaskMetadataResult.EmployeeUnavailable
            TaskMetadataWriteStatus.RUNNING_TASK ->
                UpdateTaskMetadataResult.RunningTask
        }
    }

    override suspend fun deleteTask(taskId: String): DeleteTaskResult =
        when (taskDao.deleteStoppedTask(taskId)) {
            TaskDeleteStatus.DELETED -> DeleteTaskResult.Deleted
            TaskDeleteStatus.TASK_NOT_FOUND -> DeleteTaskResult.TaskNotFound
            TaskDeleteStatus.RUNNING_TASK -> DeleteTaskResult.RunningTask
        }

    override suspend fun insertCompletedInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
        wasManuallyEdited: Boolean,
    ): WorkInterval {
        val nowEpochMs = clock.now().toEpochMilli()
        return workIntervalDao
            .insertInterval(
                intervalId = idGenerator.newId(),
                taskId = taskId,
                startEpochMs = start.toEpochMilli(),
                stopEpochMs = stop.toEpochMilli(),
                wasManuallyEdited = wasManuallyEdited,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ).toModel()
    }

    override suspend fun addManualInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult =
        workIntervalDao
            .addManualInterval(
                intervalId = idGenerator.newId(),
                taskId = taskId,
                startEpochMs = start.toEpochMilli(),
                stopEpochMs = stop.toEpochMilli(),
                updatedAtEpochMs = clock.now().toEpochMilli(),
            ).toPersistenceResult()

    override suspend fun updateManualInterval(
        taskId: String,
        intervalId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult =
        workIntervalDao
            .updateManualInterval(
                intervalId = intervalId,
                taskId = taskId,
                startEpochMs = start.toEpochMilli(),
                stopEpochMs = stop.toEpochMilli(),
                updatedAtEpochMs = clock.now().toEpochMilli(),
            ).toPersistenceResult()

    override suspend fun deleteManualInterval(
        taskId: String,
        intervalId: String,
    ): ManualIntervalPersistenceResult =
        workIntervalDao
            .deleteManualInterval(
                intervalId = intervalId,
                taskId = taskId,
                updatedAtEpochMs = clock.now().toEpochMilli(),
            ).toPersistenceResult()

    override suspend fun readIntervalsForOverlapValidation(
        taskId: String,
    ): List<WorkInterval> =
        workIntervalDao
            .readIntervalsForOverlapValidation(taskId)
            .map(WorkIntervalEntity::toModel)

    override suspend fun readCompletedDurationMillis(taskId: String): Long =
        workIntervalDao.readCompletedDurationMs(taskId)

    private fun NewDailyTask.toWrite(): TaskWrite {
        val metadata =
            normalizeMetadata(
                description = description,
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                workType = workType,
                billingStatus = billingStatus,
                mileage = mileage,
                notes = notes,
                descriptionTagSnapshots = descriptionTagSnapshots,
                hardwareSoftwarePurchaseTagSnapshots = hardwareSoftwarePurchaseTagSnapshots,
            )
        val nowEpochMs = clock.now().toEpochMilli()
        val task =
            DailyTaskEntity(
                id = idGenerator.newId(),
                seriesId = seriesId ?: idGenerator.newId(),
                clientId = clientId,
                description = metadata.description,
                hardwareSoftwarePurchases = metadata.hardwareSoftwarePurchases,
                employeeId = employeeId,
                employeeNameSnapshot = employeeNameSnapshot,
                workType = metadata.workType.name,
                billingStatus = metadata.billingStatus?.name,
                mileage = metadata.mileage,
                notes = metadata.notes,
                workDateEpochDay = workDate.toEpochDay(),
                zoneId = zoneId.id,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            )
        return TaskWrite(
            task = task,
            snapshots =
                snapshotEntities(
                    taskId = task.id,
                    category = TagCategory.DESCRIPTION,
                    drafts = descriptionTagSnapshots,
                    createdAtEpochMs = nowEpochMs,
                ) + snapshotEntities(
                    taskId = task.id,
                    category = TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                    drafts = hardwareSoftwarePurchaseTagSnapshots,
                    createdAtEpochMs = nowEpochMs,
                ),
        )
    }

    private fun normalizeMetadata(
        description: String,
        hardwareSoftwarePurchases: String,
        workType: WorkType = WorkType.UNSPECIFIED,
        billingStatus: BillingStatus? = null,
        mileage: String? = null,
        notes: String = "",
        descriptionTagSnapshots: List<TaskTagSnapshotDraft> = emptyList(),
        hardwareSoftwarePurchaseTagSnapshots: List<TaskTagSnapshotDraft> = emptyList(),
    ): NormalizedTaskMetadata =
        when (
            val validation =
                TaskMetadataValidator.validate(
                    description = description,
                    hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                    workType = workType,
                    billingStatus = billingStatus,
                    mileage = mileage,
                    notes = notes,
                    descriptionTagSnapshots = descriptionTagSnapshots,
                    hardwareSoftwarePurchaseTagSnapshots = hardwareSoftwarePurchaseTagSnapshots,
                )
        ) {
            is TaskMetadataValidationResult.Valid -> validation.metadata
            is TaskMetadataValidationResult.Invalid ->
                throw IllegalArgumentException(
                    "Invalid task metadata: ${validation.errors.joinToString()}",
                )
        }

    private fun snapshotEntities(
        taskId: String,
        category: TagCategory,
        drafts: List<TaskTagSnapshotDraft>,
        createdAtEpochMs: Long,
    ): List<TaskTagSnapshotEntity> {
        val sourceTagIds =
            drafts.mapNotNull { draft ->
                draft.sourceTagId?.trim()?.takeIf(String::isNotEmpty)
            }
        require(sourceTagIds.size == sourceTagIds.distinct().size) {
            "A task cannot select the same source Tag twice in ${category.name}"
        }
        return drafts.mapIndexed { selectionOrder, draft ->
            val normalized =
                (TagTextNormalizer.validate(draft.text) as? TagTextValidationResult.Valid)?.text
                    ?: throw IllegalArgumentException("Invalid ${category.name} Tag snapshot")
            TaskTagSnapshotEntity(
                id = idGenerator.newId(),
                taskId = taskId,
                category = category.name,
                textSnapshot = normalized.displayText,
                sourceTagId = draft.sourceTagId?.trim()?.takeIf(String::isNotEmpty),
                selectionOrder = selectionOrder,
                createdAtEpochMs = createdAtEpochMs,
            )
        }
    }

    private data class TaskWrite(
        val task: DailyTaskEntity,
        val snapshots: List<TaskTagSnapshotEntity>,
    )

}

private fun ManualIntervalWriteEntityResult.toPersistenceResult():
    ManualIntervalPersistenceResult =
    when (status) {
        ManualIntervalWriteStatus.SAVED ->
            ManualIntervalPersistenceResult.Saved(requireNotNull(interval).toModel())
        ManualIntervalWriteStatus.DELETED ->
            ManualIntervalPersistenceResult.Deleted
        ManualIntervalWriteStatus.TASK_NOT_FOUND ->
            ManualIntervalPersistenceResult.TaskNotFound
        ManualIntervalWriteStatus.TASK_ALREADY_HAS_INTERVAL ->
            ManualIntervalPersistenceResult.TaskAlreadyHasInterval
        ManualIntervalWriteStatus.INTERVAL_NOT_FOUND ->
            ManualIntervalPersistenceResult.IntervalNotFound
        ManualIntervalWriteStatus.RUNNING_TASK ->
            ManualIntervalPersistenceResult.RunningTask
        ManualIntervalWriteStatus.RUNNING_INTERVAL ->
            ManualIntervalPersistenceResult.RunningInterval
        ManualIntervalWriteStatus.OVERLAP ->
            ManualIntervalPersistenceResult.Overlap
    }
