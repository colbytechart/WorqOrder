package worq.order.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
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
import worq.order.data.ExportAttemptOutcome
import worq.order.data.ExportDestination
import worq.order.data.ExportErrorCategory
import worq.order.data.LastExportAttempt
import worq.order.data.LandscapeHandedness
import worq.order.data.GoogleConnectionRepository
import worq.order.data.SettingsRepository
import worq.order.data.TaskRepository
import worq.order.domain.SelectTaskResult
import worq.order.domain.SelectionCoordinator
import worq.order.domain.DeleteTaskOperationResult
import worq.order.domain.TaskMutationCoordinator
import worq.order.export.CsvExportCoordinator
import worq.order.export.PrepareCsvExportResult
import worq.order.export.PrepareXlsxExportResult
import worq.order.export.PreparedCsvExport
import worq.order.export.PreparedXlsxExport
import worq.order.export.XlsxExportCoordinator
import worq.order.export.csv.DocumentOutputDestination
import worq.order.export.csv.DocumentWriteResult
import worq.order.export.google.GoogleSheetsExportFailure
import worq.order.export.google.GoogleSheetsExportOperationResult
import worq.order.export.xlsx.BinaryDocumentOutputDestination
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.DailyTask
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.timer.CurrentDateProvider
import worq.order.timer.DurationMath
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.StartTimerResult
import worq.order.timer.StopTimerResult
import worq.order.timer.TimerCoordinator
import worq.order.timer.TimerRecoveryCoordinator
import worq.order.timer.TimerRecoveryResult
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
    val taskPendingDeletionId: String? = null,
    val exportDestination: ExportDestination = ExportDestination.CSV,
    val landscapeHandedness: LandscapeHandedness =
        LandscapeHandedness.RIGHT_HANDED,
    val googleExportState: MainGoogleExportState =
        MainGoogleExportState.SETUP_REQUIRED,
    val exportProgress: MainExportProgress? = null,
    val exportFeedback: MainExportFeedback? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val taskRepository: TaskRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val selectionCoordinator: SelectionCoordinator,
    private val timerCoordinator: TimerCoordinator,
    private val timerRecoveryCoordinator: TimerRecoveryCoordinator,
    private val liveTimerSession: LiveTimerSession,
    private val utcClock: UtcClock,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val currentDateProvider: CurrentDateProvider,
    private val taskMutationCoordinator: TaskMutationCoordinator,
    private val settingsRepository: SettingsRepository,
    private val googleConnectionRepository: GoogleConnectionRepository,
    private val csvExportCoordinator: CsvExportCoordinator,
    private val xlsxExportCoordinator: XlsxExportCoordinator,
    private val documentOutputDestination: DocumentOutputDestination,
    private val binaryDocumentOutputDestination: BinaryDocumentOutputDestination,
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
    private var pendingCsvExport: PreparedCsvExport? = null
    private var pendingXlsxExport: PreparedXlsxExport? = null
    private var pendingGoogleExportDate: LocalDate? = null

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
        observeSettings()
        observeGoogleConnection()
        observeEffectiveZoneId()
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
            MainEvent.Export -> export()
            is MainEvent.CsvDocumentSelected ->
                handleCsvDocumentSelected(event.documentUri)
            is MainEvent.XlsxDocumentSelected ->
                handleXlsxDocumentSelected(event.documentUri)
            MainEvent.DismissExportFeedback ->
                rawState.update { it.copy(exportFeedback = null) }
            is MainEvent.OpenTaskMenu ->
                rawState.update {
                    it.copy(
                        openTaskMenuTaskId = event.taskId,
                        message = null,
                    )
                }
            MainEvent.CloseTaskMenu ->
                rawState.update { it.copy(openTaskMenuTaskId = null) }
            is MainEvent.EditTask -> {
                rawState.update { it.copy(openTaskMenuTaskId = null) }
                mutableEffects.tryEmit(MainEffect.NavigateToEditTask(event.taskId))
            }
            is MainEvent.RequestDeleteTask ->
                rawState.update {
                    it.copy(
                        openTaskMenuTaskId = null,
                        taskPendingDeletionId = event.taskId,
                        message = null,
                    )
                }
            MainEvent.ConfirmDeleteTask -> deletePendingTask()
            MainEvent.DismissDeleteTask ->
                rawState.update { it.copy(taskPendingDeletionId = null) }
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
                .map<List<TaskListItem>, MainLoad<List<TaskListItem>>> { tasks ->
                    MainLoad.Value(
                        tasks.sortedWith(
                            compareByDescending<TaskListItem> { it.task.createdAt }
                                .thenByDescending { it.task.id },
                        ),
                    )
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
                    .mapLatest {
                        val snapshot =
                            activeTimerRepository.readActiveTimerSnapshot()
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

    private fun observeSettings() {
        settingsRepository
            .observeSettings()
            .map { settings ->
                settings.defaultExportDestination to
                    settings.landscapeHandedness
            }
            .distinctUntilChanged()
            .onEach { (destination, handedness) ->
                rawState.update {
                    it.copy(
                        exportDestination = destination,
                        landscapeHandedness = handedness,
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun observeGoogleConnection() {
        googleConnectionRepository
            .observeConnection()
            .map { connection ->
                when {
                    connection.isConnected ->
                        MainGoogleExportState.CONNECTED
                    connection.hasAccountHint &&
                        connection.hasSpreadsheetMetadata ->
                        MainGoogleExportState.AUTHORIZATION_REQUIRED
                    else -> MainGoogleExportState.SETUP_REQUIRED
                }
            }.distinctUntilChanged()
            .onEach { googleExportState ->
                rawState.update {
                    it.copy(googleExportState = googleExportState)
                }
            }.launchIn(viewModelScope)
    }

    private fun observeEffectiveZoneId() {
        zoneIdProvider
            .observeZoneId()
            .distinctUntilChanged()
            .onEach { zoneId ->
                updateEffectiveZone(zoneId)
            }.launchIn(viewModelScope)
    }

    private fun updateEffectiveZone(zoneId: ZoneId) {
        val newToday = utcClock.now().atZone(zoneId).toLocalDate()
        rawState.update { state ->
            state.copy(
                displayedDate =
                    if (state.displayedDate == state.today) {
                        newToday
                    } else {
                        state.displayedDate
                    },
                today = newToday,
                effectiveZoneId = zoneId,
            )
        }
        refreshLifecycleState()
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
                zoneIdProvider.awaitZoneId()
                refreshClockContext()
                val recovery =
                    try {
                        timerRecoveryCoordinator.recover()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        rawState.update {
                            it.copy(message = MainMessage.DATA_UNAVAILABLE)
                        }
                        return@withLock
                    }
                when (recovery) {
                    is TimerRecoveryResult.ClockChanged ->
                        rawState.update {
                            it.copy(message = MainMessage.CLOCK_CHANGED)
                        }
                    is TimerRecoveryResult.ActiveTimerChanged ->
                        rawState.update {
                            it.copy(message = MainMessage.DATA_UNAVAILABLE)
                        }
                    is TimerRecoveryResult.Recovered -> Unit
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
                displayedDate =
                    if (it.displayedDate == it.today) {
                        today
                    } else {
                        it.displayedDate
                    },
                today = today,
                effectiveZoneId = zoneId,
            )
        }
        try {
            lifecycleRefreshMutex.withLock {
                when (timerRecoveryCoordinator.recover()) {
                    is TimerRecoveryResult.ClockChanged ->
                        rawState.update {
                            it.copy(message = MainMessage.CLOCK_CHANGED)
                        }
                    is TimerRecoveryResult.ActiveTimerChanged ->
                        rawState.update {
                            it.copy(message = MainMessage.DATA_UNAVAILABLE)
                        }
                    is TimerRecoveryResult.Recovered -> Unit
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            rawState.update {
                it.copy(message = MainMessage.DATA_UNAVAILABLE)
            }
        }
    }

    private fun refreshClockContext() {
        rawState.update {
            val today = currentDateProvider.today()
            it.copy(
                displayedDate =
                    if (it.displayedDate == it.today) {
                        today
                    } else {
                        it.displayedDate
                    },
                today = today,
                effectiveZoneId = zoneIdProvider.zoneId(),
            )
        }
    }

    private fun deletePendingTask() {
        val taskId = rawState.value.taskPendingDeletionId ?: return
        viewModelScope.launch {
            val result =
                runCatching {
                    taskMutationCoordinator.deleteTask(taskId)
                }.getOrElse {
                    rawState.update {
                        it.copy(
                            taskPendingDeletionId = null,
                            message = MainMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            rawState.update {
                it.copy(
                    taskPendingDeletionId = null,
                    message =
                        when (result) {
                            DeleteTaskOperationResult.Deleted -> null
                            DeleteTaskOperationResult.TaskNotFound ->
                                MainMessage.TASK_NOT_FOUND
                            DeleteTaskOperationResult.RunningTask ->
                                MainMessage.RUNNING_TASK_LOCKED
                        },
                )
            }
        }
    }

    private fun export() {
        val state = rawState.value
        val activeTimer =
            (state.activeTimer as? MainLoad.Value<ActivePresentation?>)
                ?.value
        if (
            state.exportProgress != null ||
            state.tasks !is MainLoad.Value ||
            state.isTimerOperationInProgress ||
            activeTimer != null
        ) {
            return
        }
        if (state.exportDestination == ExportDestination.GOOGLE_SHEETS) {
            if (state.googleExportState == MainGoogleExportState.CONNECTED) {
                val workDate = state.displayedDate
                pendingGoogleExportDate = workDate
                rawState.update {
                    it.copy(
                        exportProgress = MainExportProgress.PREPARING,
                        exportFeedback = null,
                        message = null,
                    )
                }
                viewModelScope.launch {
                    mutableEffects.emit(
                        MainEffect.ExportToGoogleSheets(workDate),
                    )
                }
            } else {
                mutableEffects.tryEmit(MainEffect.NavigateToGoogleSheetsSettings)
            }
            return
        }
        val workDate = state.displayedDate
        if (state.exportDestination == ExportDestination.XLSX) {
            prepareXlsxExport(workDate)
            return
        }
        viewModelScope.launch {
            rawState.update {
                it.copy(
                    exportProgress = MainExportProgress.PREPARING,
                    exportFeedback = null,
                )
            }
            val result =
                runCatching {
                    csvExportCoordinator.prepare(workDate)
                }.getOrElse {
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.PREPARATION_FAILED,
                        category = ExportErrorCategory.PREPARATION,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.CSV,
                    )
                    return@launch
                }
            when (result) {
                is PrepareCsvExportResult.Ready -> {
                    pendingCsvExport = result.export
                    rawState.update {
                        it.copy(
                            exportProgress =
                                MainExportProgress.CHOOSING_DESTINATION,
                        )
                    }
                    mutableEffects.emit(
                        MainEffect.LaunchCsvDocument(
                            result.export.suggestedFileName,
                        ),
                    )
                }
                PrepareCsvExportResult.ClockChanged ->
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.CLOCK_CHANGED,
                        category = ExportErrorCategory.CLOCK_CHANGED,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.CSV,
                    )
                PrepareCsvExportResult.ActiveTimerChanged ->
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.ACTIVE_TIMER_CHANGED,
                        category = ExportErrorCategory.ACTIVE_TIMER_CHANGED,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.CSV,
                    )
            }
        }
    }

    private fun prepareXlsxExport(workDate: LocalDate) {
        viewModelScope.launch {
            rawState.update {
                it.copy(
                    exportProgress = MainExportProgress.PREPARING,
                    exportFeedback = null,
                )
            }
            val result =
                runCatching {
                    xlsxExportCoordinator.prepare(workDate)
                }.getOrElse {
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.PREPARATION_FAILED,
                        category = ExportErrorCategory.PREPARATION,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.XLSX,
                    )
                    return@launch
                }
            when (result) {
                is PrepareXlsxExportResult.Ready -> {
                    pendingXlsxExport = result.export
                    rawState.update {
                        it.copy(
                            exportProgress =
                                MainExportProgress.CHOOSING_DESTINATION,
                        )
                    }
                    mutableEffects.emit(
                        MainEffect.LaunchXlsxDocument(
                            result.export.suggestedFileName,
                        ),
                    )
                }
                PrepareXlsxExportResult.ClockChanged ->
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.CLOCK_CHANGED,
                        category = ExportErrorCategory.CLOCK_CHANGED,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.XLSX,
                    )
                PrepareXlsxExportResult.ActiveTimerChanged ->
                    finishExportFailure(
                        workDate = workDate,
                        outcome = MainExportOutcome.ACTIVE_TIMER_CHANGED,
                        category = ExportErrorCategory.ACTIVE_TIMER_CHANGED,
                        attemptedAt = utcClock.now(),
                        destination = ExportDestination.XLSX,
                    )
            }
        }
    }

    fun onGoogleSheetsExportResult(
        result: GoogleSheetsExportOperationResult,
    ) {
        val requestedDate = pendingGoogleExportDate ?: return
        pendingGoogleExportDate = null
        when (result) {
            is GoogleSheetsExportOperationResult.Success -> {
                val receipt = result.receipt
                if (receipt.workDate != requestedDate) {
                    finishGoogleExport(
                        workDate = requestedDate,
                        outcome = MainExportOutcome.MALFORMED_RESPONSE,
                        category =
                            ExportErrorCategory.GOOGLE_MALFORMED_RESPONSE,
                    )
                } else {
                    finishGoogleExport(
                        workDate = receipt.workDate,
                        outcome = MainExportOutcome.SUCCESS,
                        attemptedAt = receipt.exportedAt,
                        tabName = receipt.tabName,
                    )
                }
            }
            GoogleSheetsExportOperationResult.Canceled -> {
                rawState.update {
                    it.copy(
                        exportProgress = null,
                        exportFeedback = null,
                    )
                }
                recordExportAttempt(
                    workDate = requestedDate,
                    attemptedAt = utcClock.now(),
                    destination = ExportDestination.GOOGLE_SHEETS,
                    outcome = ExportAttemptOutcome.CANCELED,
                )
            }
            GoogleSheetsExportOperationResult.SetupRequired -> {
                finishGoogleExport(
                    workDate = requestedDate,
                    outcome = MainExportOutcome.AUTHORIZATION_REQUIRED,
                    category = ExportErrorCategory.GOOGLE_SETUP_REQUIRED,
                )
                mutableEffects.tryEmit(MainEffect.NavigateToGoogleSheetsSettings)
            }
            GoogleSheetsExportOperationResult.AuthorizationRequired ->
                finishGoogleExport(
                    workDate = requestedDate,
                    outcome = MainExportOutcome.AUTHORIZATION_REQUIRED,
                    category = ExportErrorCategory.GOOGLE_AUTHORIZATION,
                )
            GoogleSheetsExportOperationResult.ClockChanged ->
                finishGoogleExport(
                    workDate = requestedDate,
                    outcome = MainExportOutcome.CLOCK_CHANGED,
                    category = ExportErrorCategory.CLOCK_CHANGED,
                )
            GoogleSheetsExportOperationResult.ActiveTimerChanged ->
                finishGoogleExport(
                    workDate = requestedDate,
                    outcome = MainExportOutcome.ACTIVE_TIMER_CHANGED,
                    category = ExportErrorCategory.ACTIVE_TIMER_CHANGED,
                )
            is GoogleSheetsExportOperationResult.Failed -> {
                val (outcome, category) =
                    result.reason.toMainExportFailure()
                finishGoogleExport(
                    workDate = requestedDate,
                    outcome = outcome,
                    category = category,
                    tabName = result.tabName,
                )
            }
        }
    }

    private fun GoogleSheetsExportFailure.toMainExportFailure():
        Pair<MainExportOutcome, ExportErrorCategory> =
        when (this) {
            GoogleSheetsExportFailure.LOCAL_STORAGE ->
                MainExportOutcome.PREPARATION_FAILED to
                    ExportErrorCategory.PREPARATION
            GoogleSheetsExportFailure.PLAY_SERVICES_UNAVAILABLE ->
                MainExportOutcome.PLAY_SERVICES_UNAVAILABLE to
                    ExportErrorCategory.GOOGLE_PLAY_SERVICES
            GoogleSheetsExportFailure.OFFLINE ->
                MainExportOutcome.OFFLINE to
                    ExportErrorCategory.GOOGLE_OFFLINE
            GoogleSheetsExportFailure.TIMEOUT ->
                MainExportOutcome.TIMEOUT to
                    ExportErrorCategory.GOOGLE_TIMEOUT
            GoogleSheetsExportFailure.NOT_FOUND_OR_NOT_GRANTED ->
                MainExportOutcome.NOT_FOUND_OR_NOT_GRANTED to
                    ExportErrorCategory.GOOGLE_NOT_FOUND
            GoogleSheetsExportFailure.PERMISSION_DENIED ->
                MainExportOutcome.PERMISSION_DENIED to
                    ExportErrorCategory.GOOGLE_PERMISSION
            GoogleSheetsExportFailure.RATE_LIMITED ->
                MainExportOutcome.RATE_LIMITED to
                    ExportErrorCategory.GOOGLE_RATE_LIMIT
            GoogleSheetsExportFailure.SERVER_FAILURE ->
                MainExportOutcome.SERVER_FAILURE to
                    ExportErrorCategory.GOOGLE_SERVER
            GoogleSheetsExportFailure.MALFORMED_RESPONSE ->
                MainExportOutcome.MALFORMED_RESPONSE to
                    ExportErrorCategory.GOOGLE_MALFORMED_RESPONSE
            GoogleSheetsExportFailure.TAB_NAME_CONFLICT ->
                MainExportOutcome.TAB_NAME_CONFLICT to
                    ExportErrorCategory.GOOGLE_CONFLICT
            GoogleSheetsExportFailure.SCHEMA_CONFLICT ->
                MainExportOutcome.SCHEMA_CONFLICT to
                    ExportErrorCategory.GOOGLE_SCHEMA
            GoogleSheetsExportFailure.AMBIGUOUS_REMOTE_RESULT ->
                MainExportOutcome.AMBIGUOUS_REMOTE_RESULT to
                    ExportErrorCategory.GOOGLE_AMBIGUOUS_RESULT
        }

    private fun finishGoogleExport(
        workDate: LocalDate,
        outcome: MainExportOutcome,
        attemptOutcome: ExportAttemptOutcome =
            if (outcome == MainExportOutcome.SUCCESS) {
                ExportAttemptOutcome.SUCCESS
            } else {
                ExportAttemptOutcome.FAILED
            },
        category: ExportErrorCategory? = null,
        attemptedAt: java.time.Instant = utcClock.now(),
        tabName: String? = null,
    ) {
        rawState.update {
            it.copy(
                exportProgress = null,
                exportFeedback =
                    MainExportFeedback(
                        workDate = workDate,
                        outcome = outcome,
                        destination = ExportDestination.GOOGLE_SHEETS,
                        tabName = tabName,
                    ),
            )
        }
        recordExportAttempt(
            workDate = workDate,
            attemptedAt = attemptedAt,
            destination = ExportDestination.GOOGLE_SHEETS,
            outcome = attemptOutcome,
            errorCategory = category,
        )
    }

    private fun handleCsvDocumentSelected(documentUri: String?) {
        val export = pendingCsvExport ?: return
        if (rawState.value.exportProgress != MainExportProgress.CHOOSING_DESTINATION) {
            return
        }
        if (documentUri == null) {
            pendingCsvExport = null
            rawState.update {
                it.copy(
                    exportProgress = null,
                    exportFeedback = null,
                )
            }
            recordExportAttempt(
                export = export,
                outcome = ExportAttemptOutcome.CANCELED,
            )
            return
        }
        viewModelScope.launch {
            rawState.update {
                it.copy(exportProgress = MainExportProgress.WRITING)
            }
            val result =
                runCatching {
                    documentOutputDestination.write(
                        documentUri = documentUri,
                        contents = export.contents,
                    )
                }.getOrElse {
                    DocumentWriteResult.Failed(
                        partialDocumentMayRemain = true,
                    )
                }
            pendingCsvExport = null
            when (result) {
                DocumentWriteResult.Success -> {
                    rawState.update {
                        it.copy(
                            exportProgress = null,
                            exportFeedback =
                                MainExportFeedback(
                                    workDate = export.workDate,
                                    outcome = MainExportOutcome.SUCCESS,
                                ),
                        )
                    }
                    recordExportAttempt(
                        export = export,
                        outcome = ExportAttemptOutcome.SUCCESS,
                    )
                }
                is DocumentWriteResult.Failed -> {
                    val partial = result.partialDocumentMayRemain
                    rawState.update {
                        it.copy(
                            exportProgress = null,
                            exportFeedback =
                                MainExportFeedback(
                                    workDate = export.workDate,
                                    outcome =
                                        if (partial) {
                                            MainExportOutcome
                                                .PARTIAL_OUTPUT_MAY_REMAIN
                                        } else {
                                            MainExportOutcome.OUTPUT_FAILED
                                        },
                                ),
                        )
                    }
                    recordExportAttempt(
                        export = export,
                        outcome = ExportAttemptOutcome.FAILED,
                        errorCategory =
                            if (partial) {
                                ExportErrorCategory.PARTIAL_OUTPUT
                            } else {
                                ExportErrorCategory.OUTPUT
                            },
                    )
                }
            }
        }
    }

    private fun handleXlsxDocumentSelected(documentUri: String?) {
        val export = pendingXlsxExport ?: return
        if (rawState.value.exportProgress != MainExportProgress.CHOOSING_DESTINATION) {
            return
        }
        if (documentUri == null) {
            pendingXlsxExport = null
            rawState.update {
                it.copy(
                    exportProgress = null,
                    exportFeedback = null,
                )
            }
            recordExportAttempt(
                export = export,
                outcome = ExportAttemptOutcome.CANCELED,
            )
            return
        }
        viewModelScope.launch {
            rawState.update {
                it.copy(exportProgress = MainExportProgress.WRITING)
            }
            val result =
                runCatching {
                    binaryDocumentOutputDestination.write(
                        documentUri = documentUri,
                        contents = export.contents,
                    )
                }.getOrElse {
                    DocumentWriteResult.Failed(
                        partialDocumentMayRemain = true,
                    )
                }
            pendingXlsxExport = null
            when (result) {
                DocumentWriteResult.Success -> {
                    rawState.update {
                        it.copy(
                            exportProgress = null,
                            exportFeedback =
                                MainExportFeedback(
                                    workDate = export.workDate,
                                    outcome = MainExportOutcome.SUCCESS,
                                    destination = ExportDestination.XLSX,
                                ),
                        )
                    }
                    recordExportAttempt(
                        export = export,
                        outcome = ExportAttemptOutcome.SUCCESS,
                    )
                }
                is DocumentWriteResult.Failed -> {
                    val partial = result.partialDocumentMayRemain
                    rawState.update {
                        it.copy(
                            exportProgress = null,
                            exportFeedback =
                                MainExportFeedback(
                                    workDate = export.workDate,
                                    outcome =
                                        if (partial) {
                                            MainExportOutcome
                                                .PARTIAL_OUTPUT_MAY_REMAIN
                                        } else {
                                            MainExportOutcome.OUTPUT_FAILED
                                        },
                                    destination = ExportDestination.XLSX,
                                ),
                        )
                    }
                    recordExportAttempt(
                        export = export,
                        outcome = ExportAttemptOutcome.FAILED,
                        errorCategory =
                            if (partial) {
                                ExportErrorCategory.PARTIAL_OUTPUT
                            } else {
                                ExportErrorCategory.OUTPUT
                            },
                    )
                }
            }
        }
    }

    private suspend fun finishExportFailure(
        workDate: LocalDate,
        outcome: MainExportOutcome,
        category: ExportErrorCategory,
        attemptedAt: java.time.Instant,
        destination: ExportDestination,
    ) {
        when (destination) {
            ExportDestination.CSV -> pendingCsvExport = null
            ExportDestination.XLSX -> pendingXlsxExport = null
            ExportDestination.GOOGLE_SHEETS -> Unit
        }
        rawState.update {
            it.copy(
                exportProgress = null,
                exportFeedback =
                    MainExportFeedback(
                        workDate = workDate,
                        outcome = outcome,
                        destination = destination,
                    ),
            )
        }
        runCatching {
            settingsRepository.recordLastExportAttempt(
                LastExportAttempt(
                    destination = destination,
                    workDate = workDate,
                    attemptedAt = attemptedAt,
                    outcome = ExportAttemptOutcome.FAILED,
                    errorCategory = category,
                ),
            )
        }
    }

    private fun recordExportAttempt(
        export: PreparedCsvExport,
        outcome: ExportAttemptOutcome,
        errorCategory: ExportErrorCategory? = null,
    ) =
        recordExportAttempt(
            workDate = export.workDate,
            attemptedAt = export.exportedAt,
            destination = ExportDestination.CSV,
            outcome = outcome,
            errorCategory = errorCategory,
        )

    private fun recordExportAttempt(
        export: PreparedXlsxExport,
        outcome: ExportAttemptOutcome,
        errorCategory: ExportErrorCategory? = null,
    ) =
        recordExportAttempt(
            workDate = export.workDate,
            attemptedAt = export.exportedAt,
            destination = ExportDestination.XLSX,
            outcome = outcome,
            errorCategory = errorCategory,
        )

    private fun recordExportAttempt(
        workDate: LocalDate,
        attemptedAt: java.time.Instant,
        destination: ExportDestination,
        outcome: ExportAttemptOutcome,
        errorCategory: ExportErrorCategory? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.recordLastExportAttempt(
                    LastExportAttempt(
                        destination = destination,
                        workDate = workDate,
                        attemptedAt = attemptedAt,
                        outcome = outcome,
                        errorCategory = errorCategory,
                    ),
                )
            }
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
                    canModify = !isRunning && !isTimerOperationInProgress,
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
            taskPendingDeletion =
                taskItems.firstOrNull { it.id == taskPendingDeletionId },
            canExport =
                tasks is MainLoad.Value &&
                    activeLoadKnown &&
                    activeTaskId == null &&
                    !isTimerOperationInProgress &&
                    exportProgress == null,
            exportDestination = exportDestination,
            landscapeHandedness = landscapeHandedness,
            googleExportState = googleExportState,
            exportProgress = exportProgress,
            exportFeedback = exportFeedback,
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
                timerRecoveryCoordinator = container.timerRecoveryCoordinator,
                liveTimerSession = container.liveTimerSession,
                utcClock = container.utcClock,
                zoneIdProvider = container.zoneIdProvider,
                currentDateProvider = container.currentDateProvider,
                taskMutationCoordinator = container.taskMutationCoordinator,
                settingsRepository = container.settingsRepository,
                googleConnectionRepository =
                    container.googleConnectionRepository,
                csvExportCoordinator = container.csvExportCoordinator,
                xlsxExportCoordinator = container.xlsxExportCoordinator,
                documentOutputDestination = container.documentOutputDestination,
                binaryDocumentOutputDestination =
                    container.binaryDocumentOutputDestination,
            ) as T
        }
    }

    companion object {
        const val TIMER_REFRESH_MILLIS = 200L
    }
}
