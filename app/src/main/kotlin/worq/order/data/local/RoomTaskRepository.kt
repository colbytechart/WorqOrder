package worq.order.data.local

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.EntityIdGenerator
import worq.order.data.MAX_TASK_DESCRIPTION_CODE_POINTS
import worq.order.data.NewDailyTask
import worq.order.data.TaskRepository
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
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

    override suspend fun readTaskWithClient(taskId: String): TaskWithClient? =
        taskDao.readTaskWithClient(taskId)?.toModel()

    override suspend fun readTaskWithIntervals(taskId: String): TaskWithIntervals? =
        taskDao.readTaskWithOrderedIntervals(taskId)?.toModel()

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
        val description = normalizeDescription(newTask.description)
        val nowEpochMs = clock.now().toEpochMilli()
        val entity =
            DailyTaskEntity(
                id = idGenerator.newId(),
                seriesId = newTask.seriesId ?: idGenerator.newId(),
                clientId = newTask.clientId,
                description = description,
                workDateEpochDay = newTask.workDate.toEpochDay(),
                zoneId = newTask.zoneId.id,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            )
        taskDao.insertDailyTask(entity)
        return entity.toModel()
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
    ): Boolean {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        return taskDao.updateTaskMetadata(
            taskId = taskId,
            clientId = clientId,
            description = normalizeDescription(description),
            updatedAtEpochMs = clock.now().toEpochMilli(),
        ) == 1
    }

    override suspend fun deleteTask(taskId: String): Boolean =
        taskDao.deleteTask(taskId) == 1

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

    override suspend fun readIntervalsForOverlapValidation(
        taskId: String,
    ): List<WorkInterval> =
        workIntervalDao
            .readIntervalsForOverlapValidation(taskId)
            .map(WorkIntervalEntity::toModel)

    override suspend fun readCompletedDurationMillis(taskId: String): Long =
        workIntervalDao.readCompletedDurationMs(taskId)

    private fun normalizeDescription(rawDescription: String): String {
        val description = rawDescription.trim()
        require(description.isNotEmpty()) { "Description must not be blank" }
        require(
            description.codePointCount(0, description.length) <=
                MAX_TASK_DESCRIPTION_CODE_POINTS,
        ) {
            "Description must not exceed $MAX_TASK_DESCRIPTION_CODE_POINTS characters"
        }
        return description
    }
}
