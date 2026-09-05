package worq.order.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState
import worq.order.data.TaskRepository
import worq.order.model.DailyTask
import worq.order.timer.CurrentDateProvider
import worq.order.timer.EffectiveZoneIdProvider

sealed interface SelectTaskResult {
    data class Selected(
        val task: DailyTask,
    ) : SelectTaskResult

    data object NotFound : SelectTaskResult

    data class LockedByActiveTimer(
        val runningTaskId: String,
    ) : SelectTaskResult
}

sealed interface ClearSelectionResult {
    data object Cleared : ClearSelectionResult

    data class LockedByActiveTimer(
        val runningTaskId: String,
    ) : ClearSelectionResult
}

sealed interface SelectionReconciliationResult {
    data object NoSelection : SelectionReconciliationResult

    data object AlreadyCurrent : SelectionReconciliationResult

    /** Room says the persisted timing selection is not eligible in the current date/zone. */
    data object IneligibleSelectionCleared : SelectionReconciliationResult

    data object MissingSelectionCleared : SelectionReconciliationResult

    data class ActiveTimerOwnsSelection(
        val runningTaskId: String,
    ) : SelectionReconciliationResult

    data object ActiveTimerTaskMissing : SelectionReconciliationResult
}

class SelectionCoordinator(
    private val selectedTaskRepository: SelectedTaskRepository,
    private val taskRepository: TaskRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val mutex: Mutex = Mutex(),
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSelectedTask(): Flow<DailyTask?> =
        selectedTaskRepository.observeSelection().flatMapLatest { selection ->
            if (selection == null) {
                flowOf(null)
            } else {
                taskRepository.observeTask(selection.taskId)
            }
        }

    suspend fun readSelection(): SelectedTaskState? =
        selectedTaskRepository.readSelection()

    suspend fun selectTask(taskId: String): SelectTaskResult =
        mutex.withLock {
            zoneIdProvider.awaitZoneId()
            val activeTimer = activeTimerRepository.readActiveTimer()
            if (activeTimer != null && activeTimer.taskId != taskId) {
                return@withLock SelectTaskResult.LockedByActiveTimer(
                    runningTaskId = activeTimer.taskId,
                )
            }
            val task = taskRepository.readTaskWithClient(taskId)?.task
                ?: return@withLock SelectTaskResult.NotFound
            val today = currentDateProvider.today()
            val effectiveZone = zoneIdProvider.zoneId()
            selectedTaskRepository.select(
                task.toSelection(
                    selectedOnDate = today,
                    selectedInZone = effectiveZone,
                ),
            )
            SelectTaskResult.Selected(task)
        }

    suspend fun clearSelection(): ClearSelectionResult =
        mutex.withLock {
            activeTimerRepository.readActiveTimer()?.let { activeTimer ->
                return@withLock ClearSelectionResult.LockedByActiveTimer(
                    runningTaskId = activeTimer.taskId,
                )
            }
            selectedTaskRepository.clear()
            ClearSelectionResult.Cleared
        }

    suspend fun reconcileForToday(): SelectionReconciliationResult =
        mutex.withLock {
            zoneIdProvider.awaitZoneId()
            activeTimerRepository.readActiveTimer()?.let { activeTimer ->
                val runningTask =
                    taskRepository.readTaskWithClient(activeTimer.taskId)?.task
                        ?: return@withLock SelectionReconciliationResult.ActiveTimerTaskMissing
                selectedTaskRepository.select(
                    runningTask.toSelection(
                        selectedOnDate = runningTask.workDate,
                        selectedInZone = activeTimer.boundaryZoneId,
                    ),
                )
                return@withLock SelectionReconciliationResult.ActiveTimerOwnsSelection(
                    runningTaskId = activeTimer.taskId,
                )
            }
            val selection =
                selectedTaskRepository.readSelection()
                    ?: return@withLock SelectionReconciliationResult.NoSelection
            val source =
                taskRepository.readTaskWithClient(selection.taskId)?.task
                    ?: run {
                        selectedTaskRepository.clear()
                        return@withLock SelectionReconciliationResult.MissingSelectionCleared
                    }
            if (source.seriesId != selection.seriesId) {
                selectedTaskRepository.clear()
                return@withLock SelectionReconciliationResult.MissingSelectionCleared
            }
            val today = currentDateProvider.today()
            val effectiveZone = zoneIdProvider.zoneId()

            if (source.workDate == today && source.zoneId == effectiveZone) {
                if (
                    selection.selectedOnDate != today ||
                    selection.selectedInZone != effectiveZone
                ) {
                    selectedTaskRepository.select(
                        source.toSelection(today, effectiveZone),
                    )
                }
                return@withLock SelectionReconciliationResult.AlreadyCurrent
            }

            // The persisted date/zone fields describe when the selection was made; they cannot
            // override the authoritative date/zone stored on the Room task. Reconciliation never
            // creates or reuses a task for another date or zone.
            selectedTaskRepository.clear()
            SelectionReconciliationResult.IneligibleSelectionCleared
        }

    private fun DailyTask.toSelection(
        selectedOnDate: LocalDate,
        selectedInZone: ZoneId,
    ): SelectedTaskState =
        SelectedTaskState(
            taskId = id,
            seriesId = seriesId,
            selectedOnDate = selectedOnDate,
            selectedInZone = selectedInZone,
        )
}
