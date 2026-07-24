package worq.order.timer

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState
import worq.order.data.TaskRepository
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.DailyTask
import worq.order.model.WorkInterval

sealed interface StartTimerResult {
    data object NoSelectedTask : StartTimerResult

    data object SelectedTaskMissing : StartTimerResult

    data class TaskNotEligibleToday(
        val task: DailyTask,
        val today: java.time.LocalDate,
        val effectiveZoneId: java.time.ZoneId,
    ) : StartTimerResult

    data object AlreadyActive : StartTimerResult

    data class Started(
        val snapshot: ActiveTimerSnapshot,
        val initialDisplayTotal: Duration,
    ) : StartTimerResult
}

sealed interface StopTimerResult {
    data object NoActiveTimer : StopTimerResult

    data object ActiveTimerChanged : StopTimerResult

    data class ClockChanged(
        val intervalStart: Instant,
        val attemptedStop: Instant,
    ) : StopTimerResult

    data class Stopped(
        val interval: WorkInterval,
        val splitCount: Int,
    ) : StopTimerResult
}

sealed interface NormalizeTimerResult {
    data object NoActiveTimer : NormalizeTimerResult

    data object NoChange : NormalizeTimerResult

    data object ActiveTimerChanged : NormalizeTimerResult

    data class ClockChanged(
        val intervalStart: Instant,
        val evaluationInstant: Instant,
    ) : NormalizeTimerResult

    data class Normalized(
        val snapshot: ActiveTimerSnapshot,
        val splitCount: Int,
    ) : NormalizeTimerResult
}

class TimerOperationLock(
    val mutex: Mutex = Mutex(),
)

class ActiveTimerNormalizer(
    private val activeTimerRepository: ActiveTimerRepository,
    private val taskRepository: TaskRepository,
    private val selectedTaskRepository: SelectedTaskRepository,
    private val clock: UtcClock,
    private val liveTimerSession: LiveTimerSession,
    private val operationLock: TimerOperationLock,
) {
    suspend fun normalize(
        evaluationInstant: Instant = clock.now(),
    ): NormalizeTimerResult =
        operationLock.mutex.withLock {
            val current =
                activeTimerRepository.readActiveTimerSnapshot()
                    ?: return@withLock NormalizeTimerResult.NoActiveTimer
            if (evaluationInstant.isBefore(current.interval.start)) {
                return@withLock NormalizeTimerResult.ClockChanged(
                    intervalStart = current.interval.start,
                    evaluationInstant = evaluationInstant,
                )
            }

            val boundaries =
                MidnightBoundaryCalculator.boundaries(
                    segmentStart = current.interval.start,
                    endpoint = evaluationInstant,
                    zoneId = current.activeTimer.boundaryZoneId,
                    includeEndpoint = true,
                )
            if (boundaries.isEmpty()) {
                return@withLock NormalizeTimerResult.NoChange
            }

            val normalized =
                activeTimerRepository.normalizeActiveInterval(
                    expectedIntervalId = current.interval.id,
                    boundaries = boundaries,
                ) ?: return@withLock NormalizeTimerResult.ActiveTimerChanged
            val task =
                taskRepository.readTaskWithClient(normalized.interval.taskId)?.task
                    ?: return@withLock NormalizeTimerResult.ActiveTimerChanged
            selectedTaskRepository.select(
                SelectedTaskState(
                    taskId = task.id,
                    seriesId = task.seriesId,
                    selectedOnDate = task.workDate,
                    selectedInZone = normalized.activeTimer.boundaryZoneId,
                ),
            )
            val completedTotal =
                Duration.ofMillis(
                    taskRepository.readCompletedDurationMillis(task.id),
                )
            liveTimerSession.recover(
                intervalId = normalized.interval.id,
                completedTotal = completedTotal,
                intervalStart = normalized.interval.start,
                wallNow = evaluationInstant,
            )
            NormalizeTimerResult.Normalized(
                snapshot = normalized,
                splitCount = boundaries.size,
            )
        }
}

class TimerCoordinator(
    private val activeTimerRepository: ActiveTimerRepository,
    private val taskRepository: TaskRepository,
    private val selectedTaskRepository: SelectedTaskRepository,
    private val clock: UtcClock,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val liveTimerSession: LiveTimerSession,
    private val operationLock: TimerOperationLock,
) {
    suspend fun start(): StartTimerResult =
        operationLock.mutex.withLock {
            val selection =
                selectedTaskRepository.readSelection()
                    ?: return@withLock StartTimerResult.NoSelectedTask
            val task =
                taskRepository.readTaskWithClient(selection.taskId)?.task
                    ?: run {
                        selectedTaskRepository.clear()
                        return@withLock StartTimerResult.SelectedTaskMissing
                    }
            if (task.seriesId != selection.seriesId) {
                selectedTaskRepository.clear()
                return@withLock StartTimerResult.SelectedTaskMissing
            }
            val now = clock.now()
            val effectiveZone = zoneIdProvider.zoneId()
            val today = now.atZone(effectiveZone).toLocalDate()
            if (task.workDate != today || task.zoneId != effectiveZone) {
                return@withLock StartTimerResult.TaskNotEligibleToday(
                    task = task,
                    today = today,
                    effectiveZoneId = effectiveZone,
                )
            }

            val completedTotal =
                Duration.ofMillis(
                    taskRepository.readCompletedDurationMillis(task.id),
                )
            when (
                val result =
                    activeTimerRepository.createActiveInterval(
                        taskId = task.id,
                        boundaryZoneId = effectiveZone,
                        start = now,
                    )
            ) {
                CreateActiveIntervalResult.AlreadyActive -> StartTimerResult.AlreadyActive
                is CreateActiveIntervalResult.Created -> {
                    val anchor =
                        liveTimerSession.establishAtStart(
                            intervalId = result.snapshot.interval.id,
                            completedTotal = completedTotal,
                            wallStart = result.snapshot.interval.start,
                        )
                    StartTimerResult.Started(
                        snapshot = result.snapshot,
                        initialDisplayTotal = anchor.totalAtAnchor,
                    )
                }
            }
        }

    suspend fun stop(): StopTimerResult =
        operationLock.mutex.withLock {
            val current =
                activeTimerRepository.readActiveTimerSnapshot()
                    ?: return@withLock StopTimerResult.NoActiveTimer
            val stop = clock.now()
            if (
                !stop.isAfter(current.interval.start) ||
                liveTimerSession.hasClockAnomaly(
                    intervalId = current.interval.id,
                    wallNow = stop,
                )
            ) {
                return@withLock StopTimerResult.ClockChanged(
                    intervalStart = current.interval.start,
                    attemptedStop = stop,
                )
            }
            val boundaries =
                MidnightBoundaryCalculator.boundaries(
                    segmentStart = current.interval.start,
                    endpoint = stop,
                    zoneId = current.activeTimer.boundaryZoneId,
                    includeEndpoint = false,
                )
            val closed =
                activeTimerRepository.closeActiveInterval(
                    expectedIntervalId = current.interval.id,
                    boundaries = boundaries,
                    stop = stop,
                ) ?: return@withLock StopTimerResult.ActiveTimerChanged
            val finalTask = taskRepository.readTaskWithClient(closed.interval.taskId)?.task
            if (finalTask != null) {
                selectedTaskRepository.select(
                    SelectedTaskState(
                        taskId = finalTask.id,
                        seriesId = finalTask.seriesId,
                        selectedOnDate = finalTask.workDate,
                        selectedInZone = closed.activeTimer.boundaryZoneId,
                    ),
                )
            }
            liveTimerSession.clear()
            StopTimerResult.Stopped(
                interval = closed.interval,
                splitCount = boundaries.size,
            )
        }
}
