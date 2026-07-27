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
import worq.order.data.AppSettings
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.CreateDailyTaskResult
import worq.order.data.DeleteTaskResult
import worq.order.data.ManualIntervalPersistenceResult
import worq.order.data.NewDailyTask
import worq.order.data.ExportDestination
import worq.order.data.LastExportAttempt
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState
import worq.order.data.SettingsRepository
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.TimeZoneSettingResult
import worq.order.data.TaskRepository
import worq.order.data.TimerSplitBoundary
import worq.order.data.UpdateTaskMetadataResult
import worq.order.export.csv.DocumentOutputDestination
import worq.order.export.csv.DocumentWriteResult
import worq.order.export.xlsx.BinaryDocumentOutputDestination
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

class FakeDocumentOutputDestination(
    var result: DocumentWriteResult = DocumentWriteResult.Success,
) : DocumentOutputDestination {
    data class Write(
        val documentUri: String,
        val contents: String,
    )

    val writes = mutableListOf<Write>()

    override suspend fun write(
        documentUri: String,
        contents: String,
    ): DocumentWriteResult {
        writes += Write(documentUri, contents)
        return result
    }
}

class FakeBinaryDocumentOutputDestination(
    var result: DocumentWriteResult = DocumentWriteResult.Success,
) : BinaryDocumentOutputDestination {
    data class Write(
        val documentUri: String,
        val contents: ByteArray,
    )

    val writes = mutableListOf<Write>()

    override suspend fun write(
        documentUri: String,
        contents: ByteArray,
    ): DocumentWriteResult {
        writes += Write(documentUri, contents.copyOf())
        return result
    }
}

class FakeZoneIdProvider(
    initial: ZoneId,
) : EffectiveZoneIdProvider {
    private val state = MutableStateFlow(initial)

    var current: ZoneId
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun zoneId(): ZoneId = state.value

    override fun observeZoneId(): Flow<ZoneId> = state
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<AppSettings> = state

    override suspend fun readSettings(): AppSettings = state.value

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun setTimeZoneMode(
        timeZoneMode: TimeZoneMode,
    ): TimeZoneSettingResult {
        val current = state.value
        if (timeZoneMode == TimeZoneMode.MANUAL && current.manualZoneId == null) {
            return TimeZoneSettingResult.InvalidManualZone
        }
        val updated = current.copy(timeZoneMode = timeZoneMode)
        state.value = updated
        return TimeZoneSettingResult.Updated(updated)
    }

    override suspend fun setManualZoneId(zoneId: ZoneId): TimeZoneSettingResult {
        val updated =
            state.value.copy(
                timeZoneMode = TimeZoneMode.MANUAL,
                manualZoneId = zoneId,
            )
        state.value = updated
        return TimeZoneSettingResult.Updated(updated)
    }

    override suspend fun setDefaultExportDestination(
        destination: ExportDestination,
    ) {
        state.value =
            state.value.copy(defaultExportDestination = destination)
    }

    override suspend fun recordLastExportAttempt(attempt: LastExportAttempt) {
        state.value = state.value.copy(lastExportAttempt = attempt)
    }
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
    private val runningTaskIds = mutableSetOf<String>()
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

    override fun observeTaskWithIntervals(taskId: String): Flow<TaskWithIntervals?> =
        combine(taskState, intervalRevision) { tasks, _ ->
            tasks[taskId]?.let { task ->
                TaskWithIntervals(
                    taskWithClient =
                        TaskWithClient(
                            task = task,
                            client = CLIENT.copy(id = task.clientId),
                        ),
                    intervals =
                        intervals[taskId]
                            .orEmpty()
                            .sortedWith(compareBy(WorkInterval::start, WorkInterval::ordinal)),
                )
            }
        }

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

    override suspend fun readTasksWithIntervalsForDate(
        workDate: LocalDate,
    ): List<TaskWithIntervals> =
        taskState.value.values
            .filter { it.workDate == workDate }
            .sortedWith(compareBy(DailyTask::createdAt, DailyTask::id))
            .map { task ->
                TaskWithIntervals(
                    taskWithClient =
                        TaskWithClient(
                            task = task,
                            client = CLIENT.copy(id = task.clientId),
                        ),
                    intervals =
                        intervals[task.id]
                            .orEmpty()
                            .sortedWith(
                                compareBy<WorkInterval> { it.start }
                                    .thenBy { it.ordinal }
                                    .thenBy { it.id },
                            ),
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
                    hardwareSoftwarePurchases =
                        newTask.hardwareSoftwarePurchases.trim(),
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

    override suspend fun createDailyTask(newTask: NewDailyTask): CreateDailyTaskResult =
        CreateDailyTaskResult.Created(insertDailyTask(newTask))

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
        hardwareSoftwarePurchases: String,
    ): UpdateTaskMetadataResult =
        mutex.withLock {
            val current =
                taskState.value[taskId]
                    ?: return@withLock UpdateTaskMetadataResult.TaskNotFound
            if (taskId in runningTaskIds) {
                return@withLock UpdateTaskMetadataResult.RunningTask
            }
            taskState.value =
                taskState.value +
                    (
                        taskId to
                            current.copy(
                                clientId = clientId,
                                description = description,
                                hardwareSoftwarePurchases =
                                    hardwareSoftwarePurchases,
                            )
                    )
            UpdateTaskMetadataResult.Updated(requireNotNull(taskState.value[taskId]))
        }

    override suspend fun deleteTask(taskId: String): DeleteTaskResult =
        mutex.withLock {
            if (taskId in runningTaskIds) {
                return@withLock DeleteTaskResult.RunningTask
            }
            val existed = taskState.value.containsKey(taskId)
            taskState.value = taskState.value - taskId
            intervals.remove(taskId)
            if (existed) {
                DeleteTaskResult.Deleted
            } else {
                DeleteTaskResult.TaskNotFound
            }
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

    override suspend fun addManualInterval(
        taskId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult {
        if (taskState.value[taskId] == null) {
            return ManualIntervalPersistenceResult.TaskNotFound
        }
        if (taskId in runningTaskIds) {
            return ManualIntervalPersistenceResult.RunningTask
        }
        if (hasOverlap(taskId, start, stop)) {
            return ManualIntervalPersistenceResult.Overlap
        }
        val interval =
            newInterval(
                taskId = taskId,
                start = start,
                stop = stop,
                wasManuallyEdited = true,
            )
        addInterval(interval)
        return ManualIntervalPersistenceResult.Saved(interval)
    }

    override suspend fun updateManualInterval(
        taskId: String,
        intervalId: String,
        start: Instant,
        stop: Instant,
    ): ManualIntervalPersistenceResult {
        if (taskState.value[taskId] == null) {
            return ManualIntervalPersistenceResult.TaskNotFound
        }
        if (taskId in runningTaskIds) {
            return ManualIntervalPersistenceResult.RunningTask
        }
        val current =
            intervals[taskId]
                .orEmpty()
                .firstOrNull { it.id == intervalId }
                ?: return ManualIntervalPersistenceResult.IntervalNotFound
        if (current.stop == null) {
            return ManualIntervalPersistenceResult.RunningInterval
        }
        if (hasOverlap(taskId, start, stop, intervalId)) {
            return ManualIntervalPersistenceResult.Overlap
        }
        val changed =
            current.copy(
                start = start,
                stop = stop,
                wasManuallyEdited = true,
                updatedAt = stop,
            )
        replaceInterval(changed)
        return ManualIntervalPersistenceResult.Saved(changed)
    }

    override suspend fun deleteManualInterval(
        taskId: String,
        intervalId: String,
    ): ManualIntervalPersistenceResult {
        if (taskState.value[taskId] == null) {
            return ManualIntervalPersistenceResult.TaskNotFound
        }
        return mutex.withLock {
            if (taskId in runningTaskIds) {
                return@withLock ManualIntervalPersistenceResult.RunningTask
            }
            val taskIntervals = intervals[taskId].orEmpty()
            val interval =
                taskIntervals.firstOrNull { it.id == intervalId }
                    ?: return@withLock ManualIntervalPersistenceResult.IntervalNotFound
            if (interval.stop == null) {
                return@withLock ManualIntervalPersistenceResult.RunningInterval
            }
            intervals[taskId] =
                taskIntervals.filterNot { it.id == intervalId }.toMutableList()
            intervalRevision.value += 1L
            ManualIntervalPersistenceResult.Deleted
        }
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

    suspend fun markRunning(taskId: String) {
        mutex.withLock {
            runningTaskIds += taskId
        }
    }

    suspend fun markStopped(taskId: String) {
        mutex.withLock {
            runningTaskIds -= taskId
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

    private fun hasOverlap(
        taskId: String,
        start: Instant,
        stop: Instant,
        excludingIntervalId: String? = null,
    ): Boolean =
        intervals[taskId]
            .orEmpty()
            .asSequence()
            .filter { it.id != excludingIntervalId }
            .any { interval ->
                val existingStop = interval.stop
                if (existingStop == null) {
                    stop.isAfter(interval.start)
                } else {
                    start.isBefore(existingStop) && interval.start.isBefore(stop)
                }
            }

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
            tasks.markRunning(taskId)
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
            tasks.markStopped(closed.taskId)
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
            tasks.markStopped(closed.taskId)
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
            tasks.markRunning(nextTask.id)
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
