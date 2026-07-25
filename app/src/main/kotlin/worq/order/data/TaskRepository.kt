package worq.order.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval

data class NewDailyTask(
    val clientId: String,
    val description: String,
    val hardwareSoftwarePurchases: String = "",
    val workDate: LocalDate,
    val zoneId: ZoneId,
    val seriesId: String? = null,
)

sealed interface CreateDailyTaskResult {
    data class Created(
        val task: DailyTask,
    ) : CreateDailyTaskResult

    data object ClientUnavailable : CreateDailyTaskResult
}

sealed interface UpdateTaskMetadataResult {
    data class Updated(
        val task: DailyTask,
    ) : UpdateTaskMetadataResult

    data object TaskNotFound : UpdateTaskMetadataResult

    data object ClientUnavailable : UpdateTaskMetadataResult

    data object RunningTask : UpdateTaskMetadataResult
}

sealed interface DeleteTaskResult {
    data object Deleted : DeleteTaskResult

    data object TaskNotFound : DeleteTaskResult

    data object RunningTask : DeleteTaskResult
}

sealed interface ManualIntervalPersistenceResult {
    data class Saved(
        val interval: WorkInterval,
    ) : ManualIntervalPersistenceResult

    data object Deleted : ManualIntervalPersistenceResult

    data object TaskNotFound : ManualIntervalPersistenceResult

    data object IntervalNotFound : ManualIntervalPersistenceResult

    data object RunningTask : ManualIntervalPersistenceResult

    data object RunningInterval : ManualIntervalPersistenceResult

    data object Overlap : ManualIntervalPersistenceResult
}

interface TaskRepository {
    fun observeTasksForDate(workDate: LocalDate): Flow<List<TaskListItem>>

    fun observeTask(taskId: String): Flow<DailyTask?>

    fun observeTaskWithIntervals(taskId: String): Flow<TaskWithIntervals?>

    suspend fun readTaskWithClient(taskId: String): TaskWithClient?

    suspend fun readTaskWithIntervals(taskId: String): TaskWithIntervals?

    suspend fun findCorrespondingTask(
        seriesId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask?

    suspend fun insertDailyTask(newTask: NewDailyTask): DailyTask

    suspend fun createDailyTask(newTask: NewDailyTask): CreateDailyTaskResult

    /**
     * Finds or atomically creates the exact series/date/zone copy of [sourceTaskId].
     *
     * Returns null when the source task no longer exists. The new copy retains the source task's
     * current client, description, and series ID.
     */
    suspend fun findOrCreateDailyTaskCopy(
        sourceTaskId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask?

    suspend fun updateTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
    ): UpdateTaskMetadataResult

    suspend fun deleteTask(taskId: String): DeleteTaskResult

    suspend fun insertCompletedInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
        wasManuallyEdited: Boolean,
    ): WorkInterval

    suspend fun addManualInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult

    suspend fun updateManualInterval(
        taskId: String,
        intervalId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult

    suspend fun deleteManualInterval(
        taskId: String,
        intervalId: String,
    ): ManualIntervalPersistenceResult

    suspend fun readIntervalsForOverlapValidation(taskId: String): List<WorkInterval>

    suspend fun readCompletedDurationMillis(taskId: String): Long
}
