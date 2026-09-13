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

    data class ClosedAtBoundary(
        val snapshot: ActiveTimerSnapshot,
        val boundary: Instant,
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
    private val boundaryCloser: ActiveTimerBoundaryCloser =
        ActiveTimerBoundaryCloser(
            activeTimerRepository = activeTimerRepository,
            selectedTaskRepository = selectedTaskRepository,
            liveTimerSession = liveTimerSession,
        ),
) : ActiveTimerBoundaryReconciler {
    suspend fun normalize(): NormalizeTimerResult = normalize(clock.now())

    override suspend fun normalize(
        evaluationInstant: Instant,
    ): NormalizeTimerResult =
        operationLock.mutex.withLock {
            normalizeWhileLocked(evaluationInstant)
        }

    /**
     * Normalizes while the caller already owns [TimerOperationLock.mutex].
     *
     * This is used by export so no Start or Stop can race between the captured export instant,
     * midnight normalization, and the authoritative Room snapshot read.
     */
    internal suspend fun normalizeWhileLocked(
        evaluationInstant: Instant,
    ): NormalizeTimerResult {
        val current =
            activeTimerRepository.readActiveTimerSnapshot()
                ?: return NormalizeTimerResult.NoActiveTimer
        val liveState = liveTimerSession.read(current.interval.id)
        val projectedInstant =
            liveTimerSession.projectedInstant(
                intervalId = current.interval.id,
                intervalStart = current.interval.start,
            )
        val normalizationInstant = projectedInstant ?: evaluationInstant
        if (normalizationInstant.isBefore(current.interval.start)) {
            recoverLiveSessionIfNeeded(
                snapshot = current,
                evaluationInstant = evaluationInstant,
            )
            return NormalizeTimerResult.ClockChanged(
                intervalStart = current.interval.start,
                evaluationInstant = evaluationInstant,
            )
        }
        if (
            liveState != null &&
            !liveState.clockAnomalyDetected &&
            projectedInstant == null
        ) {
            return NormalizeTimerResult.ClockChanged(
                intervalStart = current.interval.start,
                evaluationInstant = evaluationInstant,
            )
        }

        return when (
            val boundaryResult =
                boundaryCloser.closeIfReached(
                    snapshot = current,
                    evaluationInstant = normalizationInstant,
                )
        ) {
            BoundaryCloseResult.BeforeBoundary -> {
                recoverLiveSessionIfNeeded(
                    snapshot = current,
                    evaluationInstant = evaluationInstant,
                )
                NormalizeTimerResult.NoChange
            }
            BoundaryCloseResult.ActiveTimerChanged ->
                NormalizeTimerResult.ActiveTimerChanged
            is BoundaryCloseResult.Closed ->
                NormalizeTimerResult.ClosedAtBoundary(
                    snapshot = boundaryResult.snapshot,
                    boundary = boundaryResult.boundary,
                )
        }
    }

    private suspend fun recoverLiveSessionIfNeeded(
        snapshot: ActiveTimerSnapshot,
        evaluationInstant: Instant,
    ) {
        val liveState = liveTimerSession.read(snapshot.interval.id)
        if (liveState != null && !liveState.clockAnomalyDetected) {
            return
        }
        val completedTotal =
            Duration.ofMillis(
                taskRepository.readCompletedDurationMillis(
                    snapshot.interval.taskId,
                ),
            )
        liveTimerSession.recover(
            intervalId = snapshot.interval.id,
            completedTotal = completedTotal,
            intervalStart = snapshot.interval.start,
            wallNow = evaluationInstant,
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
    private val boundaryCloser: ActiveTimerBoundaryCloser =
        ActiveTimerBoundaryCloser(
            activeTimerRepository = activeTimerRepository,
            selectedTaskRepository = selectedTaskRepository,
            liveTimerSession = liveTimerSession,
        ),
) {
    suspend fun start(): StartTimerResult =
        operationLock.mutex.withLock {
            zoneIdProvider.awaitZoneId()
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
                    if (result.repeatedTaskCreated) {
                        selectedTaskRepository.select(
                            SelectedTaskState(
                                taskId = result.startedTask.id,
                                seriesId = result.startedTask.seriesId,
                                selectedOnDate = result.startedTask.workDate,
                                selectedInZone = effectiveZone,
                            ),
                        )
                    }
                    val anchor =
                        liveTimerSession.establishAtStart(
                            intervalId = result.snapshot.interval.id,
                            completedTotal = Duration.ZERO,
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
            val wallStop = clock.now()
            val liveState = liveTimerSession.read(current.interval.id)
            val projectedStop =
                liveTimerSession.projectedInstant(
                    intervalId = current.interval.id,
                    intervalStart = current.interval.start,
                )
            val stop = projectedStop ?: wallStop
            if (
                !stop.isAfter(current.interval.start) ||
                liveState?.clockAnomalyDetected == true ||
                (liveState != null && projectedStop == null)
            ) {
                return@withLock StopTimerResult.ClockChanged(
                    intervalStart = current.interval.start,
                    attemptedStop = wallStop,
                )
            }
            when (
                val boundaryResult =
                    boundaryCloser.closeIfReached(
                        snapshot = current,
                        evaluationInstant = stop,
                    )
            ) {
                BoundaryCloseResult.ActiveTimerChanged ->
                    return@withLock StopTimerResult.ActiveTimerChanged
                is BoundaryCloseResult.Closed ->
                    return@withLock StopTimerResult.Stopped(
                        interval = boundaryResult.snapshot.interval,
                    )
                BoundaryCloseResult.BeforeBoundary -> Unit
            }
            val closed =
                activeTimerRepository.closeActiveInterval(
                    expectedIntervalId = current.interval.id,
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
            )
        }
}
