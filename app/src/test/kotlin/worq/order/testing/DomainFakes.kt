package worq.order.testing

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.NewDailyTask
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState
import worq.order.data.TaskRepository
import worq.order.data.TimerSplitBoundary
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.Client
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.MonotonicTimeSource
import worq.order.timer.UtcClock

class FakeUtcClock(
    var instant: Instant,
) : UtcClock {
    override fun now(): Instant = instant
}

class FakeMonotonicTimeSource(
    var nanos: Long = 0,
) : MonotonicTimeSource {
    override fun elapsedRealtimeNanos(): Long = nanos
}

class FakeZoneIdProvider(
    var current: ZoneId,
) : EffectiveZoneIdProvider {
    override fun zoneId(): ZoneId = current
}

class FakeSelectedTaskRepository(
    initial: SelectedTaskState? = null,
) : SelectedTaskRepository {
    private val state = MutableStateFlow(initial)

    override fun observeSelection(): Flow<SelectedTaskState?> = state

    override suspend fun readSelection(): SelectedTaskState? = state.value

    override suspend fun select(selection: SelectedTaskState) {
        state.value = selection
    }

    override suspend fun clear() {
        state.value = null
    }
}

class FakeTaskRepository : TaskRepository {
    private val mutex = Mutex()
    private val taskState = MutableStateFlow<Map<String, DailyTask>>(emptyMap())
    private val intervalRevision = MutableStateFlow(0L)
    private val intervals = mutableMapOf<String, MutableList<WorkInterval>>()
    private var taskId = 0
    private var intervalId = 0

    override fun observeTasksForDate(workDate: LocalDate): Flow<List<TaskListItem>> =
        combine(taskState, intervalRevision) { tasks, _ ->
            tasks.values
                .filter { it.workDate == workDate }
                .sortedWith(compareBy(DailyTask::createdAt, DailyTask::id))
                .map { task ->
                    TaskListItem(
                        task = task,
                        clientName = CLIENT.name,
                        clientIsActive = true,
                        completedDuration =
                            Duration.ofMillis(
                                completedDurationMillis(task.id),
                            ),
                    )
                }
        }

    override fun observeTask(taskId: String): Flow<DailyTask?> =
        taskState.map { it[taskId] }

    override suspend fun readTaskWithClient(taskId: String): TaskWithClient? =
        taskState.value[taskId]?.let {
            TaskWithClient(task = it, client = CLIENT.copy(id = it.clientId))
        }

    override suspend fun readTaskWithIntervals(taskId: String): TaskWithIntervals? =
        readTaskWithClient(taskId)?.let {
            TaskWithIntervals(
                taskWithClient = it,
                intervals = intervals[taskId].orEmpty().sortedBy(WorkInterval::start),
            )
        }

    override suspend fun findCorrespondingTask(
        seriesId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask? =
        taskState.value.values.firstOrNull {
            it.seriesId == seriesId &&
                it.workDate == workDate &&
                it.zoneId == zoneId
        }

    override suspend fun insertDailyTask(newTask: NewDailyTask): DailyTask =
        mutex.withLock {
            val now = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(taskId.toLong())
            val task =
                DailyTask(
                    id = "task-${++taskId}",
                    seriesId = newTask.seriesId ?: "series-$taskId",
                    clientId = newTask.clientId,
                    description = newTask.description.trim(),
                    workDate = newTask.workDate,
                    zoneId = newTask.zoneId,
                    createdAt = now,
                    updatedAt = now,
                )
            check(
                taskState.value.values.none {
                    it.seriesId == task.seriesId &&
                        it.workDate == task.workDate &&
                        it.zoneId == task.zoneId
                },
            )
            taskState.value = taskState.value + (task.id to task)
            task
        }

    override suspend fun findOrCreateDailyTaskCopy(
        sourceTaskId: String,
        workDate: LocalDate,
        zoneId: ZoneId,
    ): DailyTask? =
        mutex.withLock {
            val source = taskState.value[sourceTaskId] ?: return@withLock null
            taskState.value.values
                .firstOrNull {
                    it.seriesId == source.seriesId &&
                        it.workDate == workDate &&
                        it.zoneId == zoneId
                } ?: source.copy(
                id = "task-${++taskId}",
                workDate = workDate,
                zoneId = zoneId,
                createdAt = source.updatedAt.plusSeconds(taskId.toLong()),
                updatedAt = source.updatedAt.plusSeconds(taskId.toLong()),
            ).also { copy ->
                taskState.value = taskState.value + (copy.id to copy)
            }
        }

    override suspend fun updateTaskMetadata(
        taskId: String,
        clientId: String,
        description: String,
    ): Boolean =
        mutex.withLock {
            val current = taskState.value[taskId] ?: return@withLock false
            taskState.value =
                taskState.value +
                    (
                        taskId to
                            current.copy(
                                clientId = clientId,
                                description = description,
                            )
                    )
            true
        }

    override suspend fun deleteTask(taskId: String): Boolean =
        mutex.withLock {
            val existed = taskState.value.containsKey(taskId)
            taskState.value = taskState.value - taskId
            intervals.remove(taskId)
            existed
        }

    override suspend fun insertCompletedInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
        wasManuallyEdited: Boolean,
    ): WorkInterval {
        val interval =
            newInterval(
                taskId = taskId,
                start = start,
                stop = stop,
                wasManuallyEdited = wasManuallyEdited,
            )
        addInterval(interval)
        return interval
    }

    override suspend fun readIntervalsForOverlapValidation(
        taskId: String,
    ): List<WorkInterval> = intervals[taskId].orEmpty().toList()

    override suspend fun readCompletedDurationMillis(taskId: String): Long =
        completedDurationMillis(taskId)

    suspend fun addTask(task: DailyTask) {
        mutex.withLock {
            taskState.value = taskState.value + (task.id to task)
        }
    }

    suspend fun addInterval(interval: WorkInterval) {
        mutex.withLock {
            intervals.getOrPut(interval.taskId, ::mutableListOf).add(interval)
            intervalRevision.value += 1L
        }
    }

    suspend fun replaceInterval(interval: WorkInterval) {
        mutex.withLock {
            val taskIntervals = intervals.getOrPut(interval.taskId, ::mutableListOf)
            val index = taskIntervals.indexOfFirst { it.id == interval.id }
            if (index >= 0) {
                taskIntervals[index] = interval
            } else {
                taskIntervals += interval
            }
            intervalRevision.value += 1L
        }
    }

    fun newInterval(
        taskId: String,
        start: Instant,
        stop: Instant?,
        wasManuallyEdited: Boolean = false,
    ): WorkInterval {
        val id = "interval-${++intervalId}"
        val now = stop ?: start
        return WorkInterval(
            id = id,
            taskId = taskId,
            ordinal = intervals[taskId].orEmpty().size + 1,
            start = start,
            stop = stop,
            wasManuallyEdited = wasManuallyEdited,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun completedDurationMillis(taskId: String): Long =
        intervals[taskId]
            .orEmpty()
            .mapNotNull { interval ->
                interval.stop?.let { Duration.between(interval.start, it).toMillis() }
            }.sum()

    private companion object {
        val CLIENT =
            Client(
                id = "client-1",
                name = "Client",
                canonicalName = "client",
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                archivedAt = null,
            )
    }
}

class FakeActiveTimerRepository(
    private val tasks: FakeTaskRepository,
) : ActiveTimerRepository {
    private val mutex = Mutex()
    private val active = MutableStateFlow<ActiveTimerSnapshot?>(null)

    override fun observeActiveTimer(): Flow<ActiveTimer?> =
        active.map { it?.activeTimer }

    override suspend fun readActiveTimer(): ActiveTimer? =
        active.value?.activeTimer

    override suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot? =
        active.value

    override suspend fun createActiveInterval(
        taskId: String,
        boundaryZoneId: ZoneId,
        start: Instant,
    ): CreateActiveIntervalResult =
        mutex.withLock {
            if (active.value != null) {
                return@withLock CreateActiveIntervalResult.AlreadyActive
            }
            val interval = tasks.newInterval(taskId = taskId, start = start, stop = null)
            tasks.addInterval(interval)
            val timer =
                ActiveTimer(
                    intervalId = interval.id,
                    taskId = taskId,
                    boundaryZoneId = boundaryZoneId,
                    createdAt = start,
                    updatedAt = start,
                )
            val snapshot = ActiveTimerSnapshot(activeTimer = timer, interval = interval)
            active.value = snapshot
            CreateActiveIntervalResult.Created(snapshot)
        }

    override suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot? {
        val expected = active.value?.interval?.id ?: return null
        return closeActiveInterval(
            expectedIntervalId = expected,
            boundaries = emptyList(),
            stop = stop,
        )
    }

    override suspend fun normalizeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
    ): ActiveTimerSnapshot? =
        mutex.withLock {
            applyBoundaries(expectedIntervalId, boundaries)
        }

    override suspend fun closeActiveInterval(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
        stop: Instant,
    ): ActiveTimerSnapshot? =
        mutex.withLock {
            val continued = applyBoundaries(expectedIntervalId, boundaries) ?: return@withLock null
            val closed =
                continued.interval.copy(
                    stop = stop,
                    updatedAt = stop,
                )
            tasks.replaceInterval(closed)
            active.value = null
            ActiveTimerSnapshot(
                activeTimer = continued.activeTimer.copy(updatedAt = stop),
                interval = closed,
            )
        }

    private suspend fun applyBoundaries(
        expectedIntervalId: String,
        boundaries: List<TimerSplitBoundary>,
    ): ActiveTimerSnapshot? {
        var snapshot = active.value ?: return null
        if (snapshot.interval.id != expectedIntervalId) {
            return null
        }
        boundaries.forEach { boundary ->
            val closed =
                snapshot.interval.copy(
                    stop = boundary.instant,
                    updatedAt = boundary.instant,
                )
            tasks.replaceInterval(closed)
            val nextTask =
                requireNotNull(
                    tasks.findOrCreateDailyTaskCopy(
                        sourceTaskId = snapshot.interval.taskId,
                        workDate = boundary.workDate,
                        zoneId = boundary.zoneId,
                    ),
                )
            val continuation =
                tasks.newInterval(
                    taskId = nextTask.id,
                    start = boundary.instant,
                    stop = null,
                )
            tasks.addInterval(continuation)
            snapshot =
                ActiveTimerSnapshot(
                    activeTimer =
                        snapshot.activeTimer.copy(
                            intervalId = continuation.id,
                            taskId = nextTask.id,
                            updatedAt = boundary.instant,
                        ),
                    interval = continuation,
                )
            active.value = snapshot
        }
        return snapshot
    }
}
