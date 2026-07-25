package worq.order.domain

import java.time.LocalDate
import java.time.LocalDateTime
import worq.order.data.CreateDailyTaskResult
import worq.order.data.DeleteTaskResult
import worq.order.data.ManualIntervalPersistenceResult
import worq.order.data.NewDailyTask
import worq.order.data.TaskMetadataValidationError
import worq.order.data.TaskMetadataValidationResult
import worq.order.data.TaskMetadataValidator
import worq.order.data.TaskRepository
import worq.order.data.UpdateTaskMetadataResult
import worq.order.model.DailyTask
import worq.order.model.WorkInterval
import worq.order.timer.CurrentDateProvider
import worq.order.timer.EffectiveZoneIdProvider

sealed interface CreateTaskOperationResult {
    data class Created(
        val task: DailyTask,
        val selectedForTiming: Boolean,
    ) : CreateTaskOperationResult

    data class InvalidMetadata(
        val errors: Set<TaskMetadataValidationError>,
    ) : CreateTaskOperationResult

    data object ClientUnavailable : CreateTaskOperationResult
}

sealed interface UpdateTaskOperationResult {
    data class Updated(
        val task: DailyTask,
    ) : UpdateTaskOperationResult

    data class InvalidMetadata(
        val errors: Set<TaskMetadataValidationError>,
    ) : UpdateTaskOperationResult

    data object TaskNotFound : UpdateTaskOperationResult

    data object ClientUnavailable : UpdateTaskOperationResult

    data object RunningTask : UpdateTaskOperationResult
}

sealed interface DeleteTaskOperationResult {
    data object Deleted : DeleteTaskOperationResult

    data object TaskNotFound : DeleteTaskOperationResult

    data object RunningTask : DeleteTaskOperationResult
}

sealed interface ManualIntervalOperationResult {
    data class Saved(
        val interval: WorkInterval,
    ) : ManualIntervalOperationResult

    data object Deleted : ManualIntervalOperationResult

    data class Invalid(
        val errors: Set<ManualIntervalValidationError>,
    ) : ManualIntervalOperationResult

    data object TaskNotFound : ManualIntervalOperationResult

    data object IntervalNotFound : ManualIntervalOperationResult

    data object RunningTask : ManualIntervalOperationResult

    data object RunningInterval : ManualIntervalOperationResult

    data object ConcurrentOverlap : ManualIntervalOperationResult
}

class TaskMutationCoordinator(
    private val taskRepository: TaskRepository,
    private val selectionCoordinator: SelectionCoordinator,
    private val currentDateProvider: CurrentDateProvider,
    private val zoneIdProvider: EffectiveZoneIdProvider,
) {
    suspend fun createTask(
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
        workDate: LocalDate,
    ): CreateTaskOperationResult {
        zoneIdProvider.awaitZoneId()
        val metadata =
            when (
                val validation =
                    TaskMetadataValidator.validate(
                        description = description,
                        hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                    )
            ) {
                is TaskMetadataValidationResult.Valid -> validation.metadata
                is TaskMetadataValidationResult.Invalid ->
                    return CreateTaskOperationResult.InvalidMetadata(validation.errors)
            }
        val created =
            when (
                val result =
                    taskRepository.createDailyTask(
                        NewDailyTask(
                            clientId = clientId,
                            description = metadata.description,
                            hardwareSoftwarePurchases =
                                metadata.hardwareSoftwarePurchases,
                            workDate = workDate,
                            zoneId = zoneIdProvider.zoneId(),
                        ),
                    )
            ) {
                is CreateDailyTaskResult.Created -> result.task
                CreateDailyTaskResult.ClientUnavailable ->
                    return CreateTaskOperationResult.ClientUnavailable
            }
        val selected =
            if (created.workDate == currentDateProvider.today()) {
                selectionCoordinator.selectTask(created.id) is SelectTaskResult.Selected
            } else {
                false
            }
        return CreateTaskOperationResult.Created(
            task = created,
            selectedForTiming = selected,
        )
    }

    suspend fun updateTask(
        taskId: String,
        clientId: String,
        description: String,
        hardwareSoftwarePurchases: String,
    ): UpdateTaskOperationResult {
        val metadata =
            when (
                val validation =
                    TaskMetadataValidator.validate(
                        description = description,
                        hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                    )
            ) {
                is TaskMetadataValidationResult.Valid -> validation.metadata
                is TaskMetadataValidationResult.Invalid ->
                    return UpdateTaskOperationResult.InvalidMetadata(validation.errors)
            }
        return when (
            val result =
                taskRepository.updateTaskMetadata(
                    taskId = taskId,
                    clientId = clientId,
                    description = metadata.description,
                    hardwareSoftwarePurchases = metadata.hardwareSoftwarePurchases,
                )
        ) {
            is UpdateTaskMetadataResult.Updated ->
                UpdateTaskOperationResult.Updated(result.task)
            UpdateTaskMetadataResult.TaskNotFound ->
                UpdateTaskOperationResult.TaskNotFound
            UpdateTaskMetadataResult.ClientUnavailable ->
                UpdateTaskOperationResult.ClientUnavailable
            UpdateTaskMetadataResult.RunningTask ->
                UpdateTaskOperationResult.RunningTask
        }
    }

    suspend fun deleteTask(taskId: String): DeleteTaskOperationResult {
        val wasSelected = selectionCoordinator.readSelection()?.taskId == taskId
        return when (taskRepository.deleteTask(taskId)) {
            DeleteTaskResult.Deleted -> {
                if (wasSelected) {
                    selectionCoordinator.clearSelection()
                }
                DeleteTaskOperationResult.Deleted
            }
            DeleteTaskResult.TaskNotFound ->
                DeleteTaskOperationResult.TaskNotFound
            DeleteTaskResult.RunningTask ->
                DeleteTaskOperationResult.RunningTask
        }
    }

    suspend fun saveManualInterval(
        taskId: String,
        startLocal: LocalDateTime,
        stopLocal: LocalDateTime,
        editingIntervalId: String? = null,
        startOverlapChoice: OverlapOffsetChoice? = null,
        stopOverlapChoice: OverlapOffsetChoice? = null,
    ): ManualIntervalOperationResult {
        val detail =
            taskRepository.readTaskWithIntervals(taskId)
                ?: return ManualIntervalOperationResult.TaskNotFound
        val validation =
            ManualIntervalValidator.validateLocal(
                task = detail.taskWithClient.task,
                startLocal = startLocal,
                stopLocal = stopLocal,
                existingIntervals = detail.intervals,
                editingIntervalId = editingIntervalId,
                startOverlapChoice = startOverlapChoice,
                stopOverlapChoice = stopOverlapChoice,
            )
        val valid =
            when (validation) {
                is ManualIntervalValidationResult.Valid -> validation
                is ManualIntervalValidationResult.Invalid ->
                    return ManualIntervalOperationResult.Invalid(validation.errors)
            }
        val persistence =
            if (editingIntervalId == null) {
                taskRepository.addManualInterval(
                    taskId = taskId,
                    start = valid.start,
                    stop = valid.stop,
                )
            } else {
                taskRepository.updateManualInterval(
                    taskId = taskId,
                    intervalId = editingIntervalId,
                    start = valid.start,
                    stop = valid.stop,
                )
            }
        return persistence.toOperationResult()
    }

    suspend fun deleteInterval(
        taskId: String,
        intervalId: String,
    ): ManualIntervalOperationResult =
        taskRepository
            .deleteManualInterval(taskId, intervalId)
            .toOperationResult()
}

private fun ManualIntervalPersistenceResult.toOperationResult():
    ManualIntervalOperationResult =
    when (this) {
        is ManualIntervalPersistenceResult.Saved ->
            ManualIntervalOperationResult.Saved(interval)
        ManualIntervalPersistenceResult.Deleted ->
            ManualIntervalOperationResult.Deleted
        ManualIntervalPersistenceResult.TaskNotFound ->
            ManualIntervalOperationResult.TaskNotFound
        ManualIntervalPersistenceResult.IntervalNotFound ->
            ManualIntervalOperationResult.IntervalNotFound
        ManualIntervalPersistenceResult.RunningTask ->
            ManualIntervalOperationResult.RunningTask
        ManualIntervalPersistenceResult.RunningInterval ->
            ManualIntervalOperationResult.RunningInterval
        ManualIntervalPersistenceResult.Overlap ->
            ManualIntervalOperationResult.ConcurrentOverlap
    }
