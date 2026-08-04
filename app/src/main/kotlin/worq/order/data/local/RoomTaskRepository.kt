package worq.order.data.local

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
import worq.order.data.UpdateTaskMetadataResult
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType
import worq.order.timer.UtcClock

class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val workIntervalDao: WorkIntervalDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : TaskRepository {
    override fun observeTasksForDate(workDate: LocalDate): Flow<List<TaskListItem>> =
        taskDao.observeTasksForWorkDate(workDate.toEpochDay()).map { tasks ->
            tasks.map(TaskListItemEntity::toModel)
        }

    override fun observeTask(taskId: String): Flow<DailyTask?> =
        taskDao.observeTask(taskId).map { task -> task?.toModel() }

    override fun observeTaskWithIntervals(taskId: String): Flow<TaskWithIntervals?> =
        combine(
            taskDao.observeTaskWithClient(taskId),
            workIntervalDao.observeOrderedIntervals(taskId),
        ) { taskWithClient, intervals ->
            taskWithClient?.let {
                TaskWithIntervals(
                    taskWithClient = it.toModel(),
                    intervals = intervals.map(WorkIntervalEntity::toModel),
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

    override suspend fun findCorrespondingTask(
        seriesId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask? =
        taskDao
            .findCorrespondingTask(
                seriesId = seriesId,
                workDateEpochDay = workDate.toEpochDay(),
                zoneId = zoneId.id,
            )?.toModel()

    override suspend fun insertDailyTask(newTask: NewDailyTask): DailyTask {
        require(newTask.clientId.isNotBlank()) { "clientId must not be blank" }
        val entity = newTask.toEntity()
        taskDao.insertDailyTask(entity)
        return entity.toModel()
    }

    override suspend fun createDailyTask(newTask: NewDailyTask): CreateDailyTaskResult {
        require(newTask.clientId.isNotBlank()) { "clientId must not be blank" }
        val entity = newTask.toEntity()
        val result = taskDao.insertDailyTaskIfReferencesActive(entity)
        return when (result.status) {
            TaskCreationWriteStatus.CREATED ->
                CreateDailyTaskResult.Created(requireNotNull(result.task).toModel())
            TaskCreationWriteStatus.CLIENT_UNAVAILABLE ->
                CreateDailyTaskResult.ClientUnavailable
            TaskCreationWriteStatus.EMPLOYEE_UNAVAILABLE ->
                CreateDailyTaskResult.EmployeeUnavailable
        }
    }

    override suspend fun findOrCreateDailyTaskCopy(
        sourceTaskId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask? {
        require(sourceTaskId.isNotBlank()) { "sourceTaskId must not be blank" }
        val proposedTaskId = idGenerator.newId()
        val createdAtEpochMs = clock.now().toEpochMilli()
        return try {
            taskDao
                .findOrCreateDailyTaskCopy(
                    sourceTaskId = sourceTaskId,
                    proposedTaskId = proposedTaskId,
                    workDateEpochDay = workDate.toEpochDay(),
                    zoneId = zoneId.id,
                    createdAtEpochMs = createdAtEpochMs,
                )?.toModel()
        } catch (error: android.database.sqlite.SQLiteConstraintException) {
            val source = taskDao.readTask(sourceTaskId) ?: return null
            taskDao
                .findCorrespondingTask(
                    seriesId = source.seriesId,
                    workDateEpochDay = workDate.toEpochDay(),
                    zoneId = zoneId.id,
                )?.toModel()
                ?: throw error
        }
    }

    override suspend fun updateTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
        employeeId: String?,
        workType: WorkType,
        mileage: String?,
    ): UpdateTaskMetadataResult {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        val metadata =
            normalizeMetadata(
                description = description,
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                workType = workType,
                mileage = mileage,
            )
        val result =
            taskDao.updateStoppedTaskMetadata(
                taskId = taskId,
                clientId = clientId,
                description = metadata.description,
                hardwareSoftwarePurchases = metadata.hardwareSoftwarePurchases,
                employeeId = employeeId,
                workType = metadata.workType.name,
                mileage = metadata.mileage,
                updatedAtEpochMs = clock.now().toEpochMilli(),
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

    private fun NewDailyTask.toEntity(): DailyTaskEntity {
        val metadata =
            normalizeMetadata(
                description = description,
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                workType = workType,
                mileage = mileage,
            )
        val nowEpochMs = clock.now().toEpochMilli()
        return DailyTaskEntity(
            id = idGenerator.newId(),
            seriesId = seriesId ?: idGenerator.newId(),
            clientId = clientId,
            description = metadata.description,
            hardwareSoftwarePurchases = metadata.hardwareSoftwarePurchases,
            employeeId = employeeId,
            employeeNameSnapshot = employeeNameSnapshot,
            workType = metadata.workType.name,
            mileage = metadata.mileage,
            workDateEpochDay = workDate.toEpochDay(),
            zoneId = zoneId.id,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    private fun normalizeMetadata(
        description: String,
        hardwareSoftwarePurchases: String,
        workType: WorkType = WorkType.UNSPECIFIED,
        mileage: String? = null,
    ): NormalizedTaskMetadata =
        when (
            val validation =
                TaskMetadataValidator.validate(
                    description = description,
                    hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                    workType = workType,
                    mileage = mileage,
                )
        ) {
            is TaskMetadataValidationResult.Valid -> validation.metadata
            is TaskMetadataValidationResult.Invalid ->
                throw IllegalArgumentException(
                    "Invalid task metadata: ${validation.errors.joinToString()}",
                )
        }

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
        ManualIntervalWriteStatus.INTERVAL_NOT_FOUND ->
            ManualIntervalPersistenceResult.IntervalNotFound
        ManualIntervalWriteStatus.RUNNING_TASK ->
            ManualIntervalPersistenceResult.RunningTask
        ManualIntervalWriteStatus.RUNNING_INTERVAL ->
            ManualIntervalPersistenceResult.RunningInterval
        ManualIntervalWriteStatus.OVERLAP ->
            ManualIntervalPersistenceResult.Overlap
    }
