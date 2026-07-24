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

const val MAX_TASK_DESCRIPTION_CODE_POINTS = 200

data class NewDailyTask(
    val clientId: String,
    val description: String,
    val workDate: LocalDate,
    val zoneId: ZoneId,
    val seriesId: String? = null,
)

interface TaskRepository {
    fun observeTasksForDate(workDate: LocalDate): Flow<List<TaskListItem>>

    fun observeTask(taskId: String): Flow<DailyTask?>

    suspend fun readTaskWithClient(taskId: String): TaskWithClient?

    suspend fun readTaskWithIntervals(taskId: String): TaskWithIntervals?

    suspend fun findCorrespondingTask(
        seriesId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask?

    suspend fun insertDailyTask(newTask: NewDailyTask): DailyTask

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
    ): Boolean

    suspend fun deleteTask(taskId: String): Boolean

    suspend fun insertCompletedInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
        wasManuallyEdited: Boolean,
    ): WorkInterval

    suspend fun readIntervalsForOverlapValidation(taskId: String): List<WorkInterval>

    suspend fun readCompletedDurationMillis(taskId: String): Long
}
