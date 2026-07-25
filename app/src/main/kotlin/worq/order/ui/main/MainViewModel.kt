package worq.order.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.app.ApplicationContainer
import worq.order.data.ActiveTimerRepository
import worq.order.data.TaskRepository
import worq.order.domain.SelectTaskResult
import worq.order.domain.SelectionCoordinator
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.CurrentDateProvider
import worq.order.timer.DurationMath
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.NormalizeTimerResult
import worq.order.timer.StartTimerResult
import worq.order.timer.StopTimerResult
import worq.order.timer.TimerCoordinator
import worq.order.timer.UtcClock

private sealed interface MainLoad<out T> {
    data object Loading : MainLoad<Nothing>

    data class Value<T>(
        val value: T,
    ) : MainLoad<T>

    data object Failed : MainLoad<Nothing>
}

private data class ActivePresentation(
    val snapshot: ActiveTimerSnapshot,
    val taskWithClient: TaskWithClient?,
)

private data class MainRawState(
    val displayedDate: LocalDate,
    val today: LocalDate,
    val effectiveZoneId: ZoneId,
    val tasks: MainLoad<List<TaskListItem>> = MainLoad.Loading,
    val selectedTask: MainLoad<DailyTask?> = MainLoad.Loading,
    val activeTimer: MainLoad<ActivePresentation?> = MainLoad.Loading,
    val isTimerOperationInProgress: Boolean = false,
    val message: MainMessage? = null,
    val isDatePickerVisible: Boolean = false,
    val openTaskMenuTaskId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val taskRepository: TaskRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val selectionCoordinator: SelectionCoordinator,
    private val timerCoordinator: TimerCoordinator,
    private val activeTimerNormalizer: ActiveTimerNormalizer,
    private val liveTimerSession: LiveTimerSession,
    private val utcClock: UtcClock,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {
    private val initialToday = currentDateProvider.today()
    private val rawState =
        MutableStateFlow(
            MainRawState(
                displayedDate = initialToday,
                today = initialToday,
                effectiveZoneId = zoneIdProvider.zoneId(),
            ),
        )
    private val reloadGeneration = MutableStateFlow(0L)
    private val lifecycleRefreshMutex = Mutex()
    private val mutableEffects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 2)

    val effects = mutableEffects.asSharedFlow()

    private val timerTicks: Flow<Unit> =
        rawState
            .map { state ->
                (state.activeTimer as? MainLoad.Value<ActivePresentation?>)
                    ?.value
                    ?.snapshot
                    ?.interval
                    ?.id
            }.distinctUntilChanged()
            .flatMapLatest { intervalId ->
                if (intervalId == null) {
                    emptyFlow()
                } else {
                    flow {
                        while (true) {
                            emit(Unit)
                            delay(TIMER_REFRESH_MILLIS)
                        }
                    }
                }
            }.onEach {
                reconcileChangedDateIfNeeded()
            }.onStart {
                emit(Unit)
            }

    val uiState: StateFlow<MainUiState> =
        combine(rawState, timerTicks) { state, _ ->
            state.toUiState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = rawState.value.toUiState(),
        )

    init {
        observeTasks()
        observeSelection()
        observeActiveTimer()
        refreshLifecycleState()
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.SelectTask -> selectTask(event.taskId)
            MainEvent.StartTimer -> startTimer()
            MainEvent.StopTimer -> stopTimer()
            MainEvent.PreviousDate -> changeDisplayedDateBy(days = -1)
            MainEvent.NextDate -> changeDisplayedDateBy(days = 1)
            MainEvent.OpenDatePicker ->
                rawState.update {
                    it.copy(
                        isDatePickerVisible = true,
                        openTaskMenuTaskId = null,
                    )
                }
            is MainEvent.PickDate ->
                setDisplayedDate(event.date, dismissPicker = true)
            MainEvent.DismissDatePicker ->
                rawState.update { it.copy(isDatePickerVisible = false) }
            MainEvent.ReturnToToday -> {
                refreshClockContext()
                setDisplayedDate(rawState.value.today, dismissPicker = true)
            }
            MainEvent.OpenCreateTask ->
                mutableEffects.tryEmit(
                    MainEffect.NavigateToCreateTask(rawState.value.displayedDate),
                )
            MainEvent.OpenSettings ->
                mutableEffects.tryEmit(MainEffect.NavigateToSettings)
            is MainEvent.OpenTaskMenu ->
                rawState.update {
                    it.copy(
                        openTaskMenuTaskId = event.taskId,
                        message = null,
                    )
                }
            MainEvent.CloseTaskMenu ->
                rawState.update { it.copy(openTaskMenuTaskId = null) }
            MainEvent.DismissMessage ->
                rawState.update { it.copy(message = null) }
            MainEvent.RetryData -> {
                rawState.update { it.copy(message = null) }
                reloadGeneration.update { it + 1L }
                refreshLifecycleState()
            }
            MainEvent.LifecycleResumed -> refreshLifecycleState()
        }
    }

    private fun observeTasks() {
        combine(
            rawState.map { state -> state.displayedDate }.distinctUntilChanged(),
            reloadGeneration,
        ) { date, _ ->
            date
        }.flatMapLatest { date ->
            taskRepository
                .observeTasksForDate(date)
                .map<List<TaskListItem>, MainLoad<List<TaskListItem>>> {
                    MainLoad.Value(it)
                }.onStart {
                    emit(MainLoad.Loading)
                }.catch {
                    emit(MainLoad.Failed)
                }
        }.onEach { tasks ->
            rawState.update { it.copy(tasks = tasks) }
        }.launchIn(viewModelScope)
    }

    private fun observeSelection() {
        reloadGeneration
            .flatMapLatest {
                selectionCoordinator
                    .observeSelectedTask()
                    .map<DailyTask?, MainLoad<DailyTask?>> {
                        MainLoad.Value(it)
                    }.onStart {
                        emit(MainLoad.Loading)
                    }.catch {
                        emit(MainLoad.Failed)
                    }
            }.onEach { selectedTask ->
                rawState.update { it.copy(selectedTask = selectedTask) }
            }.launchIn(viewModelScope)
    }

    private fun observeActiveTimer() {
        reloadGeneration
            .flatMapLatest {
                activeTimerRepository
                    .observeActiveTimer()
                    .mapLatest { activeTimer ->
                        val snapshot =
                            activeTimer?.let {
                                activeTimerRepository.readActiveTimerSnapshot()
                            }
                        if (snapshot == null) {
                            liveTimerSession.clear()
                            null
                        } else {
                            ensureLiveTimerRecovered(snapshot)
                            ActivePresentation(
                                snapshot = snapshot,
                                taskWithClient =
                                    taskRepository.readTaskWithClient(
                                        snapshot.activeTimer.taskId,
                                    ),
                            )
                        }
                    }.map<ActivePresentation?, MainLoad<ActivePresentation?>> {
                        MainLoad.Value(it)
                    }.onStart {
                        emit(MainLoad.Loading)
                    }.catch {
                        emit(MainLoad.Failed)
                    }
            }.onEach { activeTimer ->
                rawState.update { it.copy(activeTimer = activeTimer) }
            }.launchIn(viewModelScope)
    }

    private suspend fun ensureLiveTimerRecovered(snapshot: ActiveTimerSnapshot) {
        if (liveTimerSession.read(snapshot.interval.id) != null) {
            return
        }
        val completedTotal =
            Duration.ofMillis(
                taskRepository.readCompletedDurationMillis(snapshot.interval.taskId),
            )
        liveTimerSession.recover(
            intervalId = snapshot.interval.id,
            completedTotal = completedTotal,
            intervalStart = snapshot.interval.start,
            wallNow = utcClock.now(),
        )
    }

    private fun selectTask(taskId: String) {
        if (rawState.value.isTimerOperationInProgress) {
            return
        }
        viewModelScope.launch {
            when (selectionCoordinator.selectTask(taskId)) {
                is SelectTaskResult.Selected ->
                    rawState.update { it.copy(message = null) }
                SelectTaskResult.NotFound ->
                    rawState.update {
                        it.copy(message = MainMessage.SELECTED_TASK_MISSING)
                    }
                is SelectTaskResult.LockedByActiveTimer ->
                    rawState.update {
                        it.copy(message = MainMessage.STOP_BEFORE_SWITCHING)
                    }
            }
        }
    }

    private fun startTimer() {
        runTimerOperation {
            refreshClockContext()
            selectionCoordinator.reconcileForToday()
            when (timerCoordinator.start()) {
                is StartTimerResult.Started -> null
                StartTimerResult.NoSelectedTask -> MainMessage.SELECT_A_TASK_FIRST
                StartTimerResult.SelectedTaskMissing -> MainMessage.SELECTED_TASK_MISSING
                is StartTimerResult.TaskNotEligibleToday ->
                    MainMessage.LIVE_TIMING_TODAY_ONLY
                StartTimerResult.AlreadyActive -> MainMessage.TIMER_ALREADY_RUNNING
            }
        }
    }

    private fun stopTimer() {
        runTimerOperation {
            when (timerCoordinator.stop()) {
                is StopTimerResult.Stopped -> null
                StopTimerResult.NoActiveTimer -> MainMessage.NO_ACTIVE_TIMER
                StopTimerResult.ActiveTimerChanged -> MainMessage.DATA_UNAVAILABLE
                is StopTimerResult.ClockChanged -> MainMessage.CLOCK_CHANGED
            }
        }
    }

    private fun runTimerOperation(operation: suspend () -> MainMessage?) {
        if (rawState.value.isTimerOperationInProgress) {
            return
        }
        viewModelScope.launch {
            rawState.update {
                it.copy(
                    isTimerOperationInProgress = true,
                    message = null,
                    openTaskMenuTaskId = null,
                )
            }
            val message =
                runCatching {
                    operation()
                }.getOrElse {
                    MainMessage.DATA_UNAVAILABLE
                }
            rawState.update {
                it.copy(
                    isTimerOperationInProgress = false,
                    message = message,
                )
            }
        }
    }

    private fun changeDisplayedDateBy(days: Long) {
        val changed =
            runCatching {
                rawState.value.displayedDate.plusDays(days)
            }.getOrNull() ?: return
        setDisplayedDate(changed)
    }

    private fun setDisplayedDate(
        date: LocalDate,
        dismissPicker: Boolean = false,
    ) {
        refreshClockContext()
        rawState.update {
            it.copy(
                displayedDate = date,
                isDatePickerVisible =
                    if (dismissPicker) {
                        false
                    } else {
                        it.isDatePickerVisible
                    },
                openTaskMenuTaskId = null,
                message = null,
            )
        }
    }

    private fun refreshLifecycleState() {
        viewModelScope.launch {
            lifecycleRefreshMutex.withLock {
                refreshClockContext()
                val normalization =
                    runCatching {
                        activeTimerNormalizer.normalize()
                    }.getOrElse {
                        rawState.update {
                            it.copy(message = MainMessage.DATA_UNAVAILABLE)
                        }
                        return@withLock
                    }
                if (normalization is NormalizeTimerResult.ClockChanged) {
                    rawState.update {
                        it.copy(message = MainMessage.CLOCK_CHANGED)
                    }
                }
                runCatching {
                    selectionCoordinator.reconcileForToday()
                }.onFailure {
                    rawState.update {
                        it.copy(message = MainMessage.DATA_UNAVAILABLE)
                    }
                }
            }
        }
    }

    private suspend fun reconcileChangedDateIfNeeded() {
        val today = currentDateProvider.today()
        val zoneId = zoneIdProvider.zoneId()
        if (
            today == rawState.value.today &&
            zoneId == rawState.value.effectiveZoneId
        ) {
            return
        }
        rawState.update {
            it.copy(
                today = today,
                effectiveZoneId = zoneId,
            )
        }
        lifecycleRefreshMutex.withLock {
            when (activeTimerNormalizer.normalize()) {
                is NormalizeTimerResult.ClockChanged ->
                    rawState.update {
                        it.copy(message = MainMessage.CLOCK_CHANGED)
                    }
                else -> Unit
            }
            selectionCoordinator.reconcileForToday()
        }
    }

    private fun refreshClockContext() {
        rawState.update {
            it.copy(
                today = currentDateProvider.today(),
                effectiveZoneId = zoneIdProvider.zoneId(),
            )
        }
    }

    private fun MainRawState.toUiState(): MainUiState {
        val loadedTasks =
            (tasks as? MainLoad.Value<List<TaskListItem>>)
                ?.value
                .orEmpty()
        val selected =
            (selectedTask as? MainLoad.Value<DailyTask?>)
                ?.value
        val active =
            (activeTimer as? MainLoad.Value<ActivePresentation?>)
                ?.value
        val activeSnapshot = active?.snapshot
        val liveTotal =
            activeSnapshot?.let { snapshot ->
                liveTimerSession.read(snapshot.interval.id)?.total
            }
        val selectedListItem =
            selected
                ?.takeIf { it.workDate == displayedDate }
                ?.let { task ->
                    loadedTasks.firstOrNull { it.task.id == task.id }
                }
        val displayedTimerDuration =
            when {
                activeSnapshot != null -> liveTotal ?: Duration.ZERO
                selectedListItem != null -> selectedListItem.completedDuration
                else -> Duration.ZERO
            }.nonNegative()
        val activeTaskId = activeSnapshot?.activeTimer?.taskId
        val taskItems =
            loadedTasks.map { item ->
                val isRunning = item.task.id == activeTaskId
                val isSelected =
                    if (activeTaskId != null) {
                        isRunning
                    } else {
                        item.task.id == selected?.id
                    }
                val total =
                    if (isRunning) {
                        liveTotal ?: item.completedDuration
                    } else {
                        item.completedDuration
                    }
                MainTaskItemUi(
                    id = item.task.id,
                    clientName = item.clientName,
                    description = item.task.description,
                    totalDuration = formatDuration(total.nonNegative()),
                    isClientArchived = !item.clientIsActive,
                    isSelected = isSelected,
                    isRunning = isRunning,
                    canSelect =
                        activeTaskId == null &&
                            !isTimerOperationInProgress,
                )
            }
        val activeLoadKnown = activeTimer is MainLoad.Value
        val exactTodaySelection =
            selected != null &&
                selected.workDate == today &&
                selected.zoneId == effectiveZoneId &&
                displayedDate == today
        val loadFailure =
            tasks is MainLoad.Failed ||
                selectedTask is MainLoad.Failed ||
                activeTimer is MainLoad.Failed
        return MainUiState(
            displayedDate = displayedDate,
            today = today,
            isToday = displayedDate == today,
            isLoading =
                tasks is MainLoad.Loading ||
                    selectedTask is MainLoad.Loading ||
                    activeTimer is MainLoad.Loading,
            hasTaskLoadError = tasks is MainLoad.Failed,
            tasks = taskItems,
            timerText = formatDuration(displayedTimerDuration),
            timerAction =
                if (activeTaskId == null) {
                    MainTimerAction.START
                } else {
                    MainTimerAction.STOP
                },
            canStart =
                activeLoadKnown &&
                    activeTaskId == null &&
                    exactTodaySelection &&
                    !isTimerOperationInProgress,
            canStop =
                activeTaskId != null &&
                    !isTimerOperationInProgress,
            isTimerOperationInProgress = isTimerOperationInProgress,
            runningTask =
                active?.taskWithClient?.let { taskWithClient ->
                    RunningTaskUi(
                        taskId = taskWithClient.task.id,
                        clientName = taskWithClient.client.name,
                        description = taskWithClient.task.description,
                        workDate = taskWithClient.task.workDate,
                    )
                },
            message =
                message
                    ?: if (loadFailure) {
                        MainMessage.DATA_UNAVAILABLE
                    } else {
                        null
                    },
            isDatePickerVisible = isDatePickerVisible,
            openTaskMenuTaskId = openTaskMenuTaskId,
            canExport = false,
        )
    }

    private fun Duration.nonNegative(): Duration =
        if (isNegative) {
            Duration.ZERO
        } else {
            this
        }

    private fun formatDuration(duration: Duration): String =
        runCatching {
            DurationMath.formatAccumulated(duration)
        }.getOrDefault(MainUiState.ZERO_DURATION)

    class Factory(
        private val container: ApplicationContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return MainViewModel(
                taskRepository = container.taskRepository,
                activeTimerRepository = container.activeTimerRepository,
                selectionCoordinator = container.selectionCoordinator,
                timerCoordinator = container.timerCoordinator,
                activeTimerNormalizer = container.activeTimerNormalizer,
                liveTimerSession = container.liveTimerSession,
                utcClock = container.utcClock,
                zoneIdProvider = container.zoneIdProvider,
                currentDateProvider = container.currentDateProvider,
            ) as T
        }
    }

    companion object {
        const val TIMER_REFRESH_MILLIS = 50L
    }
}
