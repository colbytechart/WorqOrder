package worq.order.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.ActiveTimerRepository
import worq.order.data.ClientRepository
import worq.order.data.EmployeeRepository
import worq.order.data.MileageNormalizer
import worq.order.data.TaskMetadataValidationResult
import worq.order.data.TaskMetadataValidator
import worq.order.data.TaskRepository
import worq.order.domain.DeleteTaskOperationResult
import worq.order.domain.BillingMinutes
import worq.order.domain.ManualIntervalOperationResult
import worq.order.domain.OverlapOffsetChoice
import worq.order.domain.TaskMutationCoordinator
import worq.order.domain.UpdateTaskOperationResult
import worq.order.model.Client
import worq.order.model.Employee
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.timer.DurationMath
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.employees.ConsultantItemUi
import worq.order.util.ClockTimeFormatter

class EditTaskViewModel(
    private val taskId: String,
    private val taskRepository: TaskRepository,
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val taskMutationCoordinator: TaskMutationCoordinator,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(EditTaskUiState(taskId = taskId))
    val uiState: StateFlow<EditTaskUiState> = mutableUiState
    private val mutableEffects = MutableSharedFlow<EditTaskEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()
    private var metadataInitialized = false
    private var observationJob: Job? = null
    private var latestDetail: TaskWithIntervals? = null

    init {
        observeData()
    }

    fun onEvent(event: EditTaskEvent) {
        when (event) {
            EditTaskEvent.Retry -> observeData()
            EditTaskEvent.OpenClientMenu ->
                mutableUiState.update {
                    it.copy(isClientMenuExpanded = it.activeClients.isNotEmpty())
                }
            EditTaskEvent.DismissClientMenu ->
                mutableUiState.update { it.copy(isClientMenuExpanded = false) }
            is EditTaskEvent.SelectClient ->
                mutableUiState.update {
                    if (it.activeClients.none { client -> client.id == event.clientId }) {
                        it
                    } else {
                        it.copy(
                            selectedClientId = event.clientId,
                            isClientMenuExpanded = false,
                            hasUnsavedMetadataChanges = true,
                            message = null,
                        )
                    }
                }
            EditTaskEvent.OpenConsultantMenu ->
                mutableUiState.update {
                    it.copy(
                        isConsultantMenuExpanded = it.activeConsultants.isNotEmpty(),
                    )
                }
            EditTaskEvent.DismissConsultantMenu ->
                mutableUiState.update { it.copy(isConsultantMenuExpanded = false) }
            is EditTaskEvent.SelectConsultant ->
                mutableUiState.update {
                    if (
                        it.activeConsultants.none { consultant ->
                            consultant.id == event.consultantId
                        }
                    ) {
                        it
                    } else {
                        it.copy(
                            selectedConsultantId = event.consultantId,
                            isConsultantMenuExpanded = false,
                            hasUnsavedMetadataChanges = true,
                            message = null,
                        )
                    }
                }
            is EditTaskEvent.EditDescription ->
                mutableUiState.update {
                    it.copy(
                        description = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    )
                }
            is EditTaskEvent.EditHardwareSoftwarePurchases ->
                mutableUiState.update {
                    it.copy(
                        hardwareSoftwarePurchases = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    )
                }
            is EditTaskEvent.SelectWorkType ->
                mutableUiState.update {
                    it.copy(
                        workType = event.workType,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    )
                }
            is EditTaskEvent.EditMileage ->
                if (MileageNormalizer.acceptsInput(event.value)) {
                    mutableUiState.update {
                        it.copy(
                            mileage = event.value,
                            metadataErrors = emptySet(),
                            hasUnsavedMetadataChanges = true,
                            message = null,
                        )
                    }
                }
            EditTaskEvent.SaveMetadata -> saveMetadata()
            EditTaskEvent.RequestClose -> requestClose()
            EditTaskEvent.ConfirmDiscard -> {
                mutableUiState.update { it.copy(showDiscardConfirmation = false) }
                mutableEffects.tryEmit(EditTaskEffect.NavigateBack)
            }
            EditTaskEvent.DismissDiscard ->
                mutableUiState.update { it.copy(showDiscardConfirmation = false) }
            EditTaskEvent.OpenAddInterval -> openAddInterval()
            is EditTaskEvent.OpenEditInterval -> openEditInterval(event.intervalId)
            EditTaskEvent.DismissIntervalEditor ->
                mutableUiState.update { it.copy(intervalEditor = null) }
            is EditTaskEvent.OpenTimePicker ->
                updateEditor {
                    it.copy(timePickerEndpoint = event.endpoint)
                }
            is EditTaskEvent.SetEditorTime ->
                setEditorTime(event.endpoint, event.hour, event.minute)
            is EditTaskEvent.SelectOffset ->
                updateEditor {
                    when (event.endpoint) {
                        IntervalEndpoint.START ->
                            it.copy(startOverlapChoice = event.choice)
                        IntervalEndpoint.STOP ->
                            it.copy(stopOverlapChoice = event.choice)
                    }
                }
            EditTaskEvent.DismissTimePicker ->
                updateEditor { it.copy(timePickerEndpoint = null) }
            EditTaskEvent.SaveInterval -> saveInterval()
            is EditTaskEvent.RequestDeleteInterval ->
                mutableUiState.update { state ->
                    state.copy(
                        intervalPendingDeletion =
                            state.intervals.firstOrNull { it.id == event.intervalId },
                    )
                }
            EditTaskEvent.ConfirmDeleteInterval -> deleteInterval()
            EditTaskEvent.DismissDeleteInterval ->
                mutableUiState.update { it.copy(intervalPendingDeletion = null) }
            EditTaskEvent.RequestDeleteTask ->
                mutableUiState.update { it.copy(showTaskDeleteConfirmation = true) }
            EditTaskEvent.ConfirmDeleteTask -> deleteTask()
            EditTaskEvent.DismissDeleteTask ->
                mutableUiState.update { it.copy(showTaskDeleteConfirmation = false) }
            EditTaskEvent.DismissMessage ->
                mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeData() {
        observationJob?.cancel()
        observationJob =
            combine(
                taskRepository.observeTaskWithIntervals(taskId),
                clientRepository.observeActiveClients(),
                employeeRepository.observeActiveEmployees(),
                activeTimerRepository.observeActiveTimer(),
            ) { detail, clients, employees, activeTimer ->
                ObservedEditTaskData(
                    detail = detail,
                    clients = clients,
                    employees = employees,
                    isRunning = activeTimer?.taskId == taskId,
                )
            }.onStart {
                mutableUiState.update {
                    it.copy(
                        isLoading = true,
                        hasLoadError = false,
                    )
                }
            }.onEach { observed ->
                val detail = observed.detail
                val clients = observed.clients
                val consultants = observed.employees.map(Employee::toConsultantItem)
                val isRunning = observed.isRunning
                latestDetail = detail
                val clientItems = clients.map(Client::toClientItem)
                if (detail == null) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            taskMissing = true,
                            activeClients = clientItems,
                            activeConsultants = consultants,
                            isRunning = isRunning,
                        )
                    }
                    return@onEach
                }
                val task = detail.taskWithClient.task
                val client = detail.taskWithClient.client
                mutableUiState.update { state ->
                    val refreshMetadata =
                        !metadataInitialized || !state.hasUnsavedMetadataChanges
                    if (refreshMetadata) {
                        metadataInitialized = true
                    }
                    state.copy(
                        isLoading = false,
                        hasLoadError = false,
                        taskMissing = false,
                        workDate = task.workDate,
                        zoneId = task.zoneId,
                        originalClientName = client.name,
                        originalConsultantName = task.employeeNameSnapshot,
                        activeClients = clientItems,
                        activeConsultants = consultants,
                        selectedClientId =
                            if (refreshMetadata) {
                                task.clientId
                            } else {
                                state.selectedClientId
                            },
                        description =
                            if (refreshMetadata) task.description else state.description,
                        hardwareSoftwarePurchases =
                            if (refreshMetadata) {
                                task.hardwareSoftwarePurchases
                            } else {
                                state.hardwareSoftwarePurchases
                            },
                        selectedConsultantId =
                            if (refreshMetadata) {
                                task.employeeId
                            } else {
                                state.selectedConsultantId
                            },
                        workType =
                            if (refreshMetadata) task.workType else state.workType,
                        mileage =
                            if (refreshMetadata) task.mileage.orEmpty() else state.mileage,
                        totalDuration = detail.completedDurationText(),
                        billingMinutes =
                            BillingMinutes.fromDuration(detail.completedDuration()),
                        intervals = detail.intervals.map { it.toItem(task.zoneId) },
                        isRunning = isRunning,
                        message =
                            if (isRunning && state.hasUnsavedMetadataChanges) {
                                EditTaskMessage.RUNNING_TASK
                            } else {
                                state.message
                            },
                    )
                }
            }.catch {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoadError = true,
                        message = EditTaskMessage.DATA_UNAVAILABLE,
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun saveMetadata() {
        val state = mutableUiState.value
        if (state.isSavingMetadata || state.isRunning) {
            mutableUiState.update { it.copy(message = EditTaskMessage.RUNNING_TASK) }
            return
        }
        val validation =
            TaskMetadataValidator.validate(
                description = state.description,
                hardwareSoftwarePurchases = state.hardwareSoftwarePurchases,
                workType = state.workType,
                mileage = state.mileage,
            )
        val errors =
            (validation as? TaskMetadataValidationResult.Invalid)
                ?.errors
                .orEmpty()
        val clientId = state.selectedClientId
        val consultantId = state.selectedConsultantId
        if (errors.isNotEmpty() || clientId == null || consultantId == null) {
            mutableUiState.update {
                it.copy(
                    metadataErrors = errors,
                    message =
                        if (clientId == null) {
                            EditTaskMessage.CLIENT_UNAVAILABLE
                        } else if (consultantId == null) {
                            EditTaskMessage.CONSULTANT_UNAVAILABLE
                        } else {
                            null
                        },
                )
            }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSavingMetadata = true, message = null) }
            val result =
                runCatching {
                    taskMutationCoordinator.updateTask(
                        taskId = taskId,
                        clientId = clientId,
                        description = state.description,
                        hardwareSoftwarePurchases =
                            state.hardwareSoftwarePurchases,
                        employeeId = consultantId,
                        workType = state.workType,
                        mileage = state.mileage,
                    )
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            message = EditTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            when (result) {
                is UpdateTaskOperationResult.Updated -> {
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            hasUnsavedMetadataChanges = false,
                            metadataErrors = emptySet(),
                        )
                    }
                    mutableEffects.tryEmit(EditTaskEffect.NavigateBack)
                }
                is UpdateTaskOperationResult.InvalidMetadata ->
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            metadataErrors = result.errors,
                        )
                    }
                UpdateTaskOperationResult.TaskNotFound ->
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            taskMissing = true,
                            message = EditTaskMessage.TASK_NOT_FOUND,
                        )
                    }
                UpdateTaskOperationResult.ClientUnavailable ->
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            selectedClientId = null,
                            message = EditTaskMessage.CLIENT_UNAVAILABLE,
                        )
                    }
                UpdateTaskOperationResult.ConsultantUnavailable ->
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            selectedConsultantId = null,
                            message = EditTaskMessage.CONSULTANT_UNAVAILABLE,
                        )
                    }
                UpdateTaskOperationResult.RunningTask ->
                    mutableUiState.update {
                        it.copy(
                            isSavingMetadata = false,
                            isRunning = true,
                            message = EditTaskMessage.RUNNING_TASK,
                        )
                    }
            }
        }
    }

    private fun openAddInterval() {
        val state = mutableUiState.value
        val date = state.workDate ?: return
        if (state.isRunning) {
            mutableUiState.update { it.copy(message = EditTaskMessage.RUNNING_TASK) }
            return
        }
        val editor =
            IntervalEditorUiState(
                startLocal = LocalDateTime.of(date, LocalTime.of(9, 0)),
                stopLocal = LocalDateTime.of(date, LocalTime.of(10, 0)),
            )
        mutableUiState.update {
            it.copy(intervalEditor = editor.withOffsetChoices(state.zoneId))
        }
    }

    private fun openEditInterval(intervalId: String) {
        val state = mutableUiState.value
        val detail = latestDetail ?: return
        val interval = detail.intervals.firstOrNull { it.id == intervalId } ?: return
        val stop = interval.stop
        if (stop == null || state.isRunning) {
            mutableUiState.update {
                it.copy(
                    message =
                        if (state.isRunning) {
                            EditTaskMessage.RUNNING_TASK
                        } else {
                            EditTaskMessage.RUNNING_INTERVAL
                        },
                )
            }
            return
        }
        val zone = detail.taskWithClient.task.zoneId
        val startZoned = interval.start.atZone(zone)
        val stopZoned = stop.atZone(zone)
        var editor =
            IntervalEditorUiState(
                intervalId = interval.id,
                startLocal = startZoned.toLocalDateTime(),
                stopLocal = stopZoned.toLocalDateTime(),
            ).withOffsetChoices(zone)
        editor =
            editor.copy(
                startOverlapChoice =
                    editor.startOffsetChoices.choiceFor(startZoned.offset),
                stopOverlapChoice =
                    editor.stopOffsetChoices.choiceFor(stopZoned.offset),
            )
        mutableUiState.update { it.copy(intervalEditor = editor) }
    }

    private fun setEditorTime(
        endpoint: IntervalEndpoint,
        hour: Int,
        minute: Int,
    ) {
        val zone = mutableUiState.value.zoneId
        updateEditor { current ->
            val localTime = LocalTime.of(hour, minute)
            val changed =
                when (endpoint) {
                    IntervalEndpoint.START ->
                        current.copy(
                            startLocal =
                                LocalDateTime.of(
                                    current.startLocal.toLocalDate(),
                                    localTime,
                                ),
                            startOverlapChoice = null,
                            timePickerEndpoint = null,
                            validationErrors = emptySet(),
                        )
                    IntervalEndpoint.STOP ->
                        current.copy(
                            stopLocal =
                                LocalDateTime.of(
                                    current.stopLocal.toLocalDate(),
                                    localTime,
                                ),
                            stopOverlapChoice = null,
                            timePickerEndpoint = null,
                            validationErrors = emptySet(),
                        )
                }
            changed.withOffsetChoices(zone)
        }
    }

    private fun saveInterval() {
        val editor = mutableUiState.value.intervalEditor ?: return
        if (editor.isSaving) {
            return
        }
        viewModelScope.launch {
            updateEditor { it.copy(isSaving = true, validationErrors = emptySet()) }
            val result =
                runCatching {
                    taskMutationCoordinator.saveManualInterval(
                        taskId = taskId,
                        startLocal = editor.startLocal,
                        stopLocal = editor.stopLocal,
                        editingIntervalId = editor.intervalId,
                        startOverlapChoice = editor.startOverlapChoice,
                        stopOverlapChoice = editor.stopOverlapChoice,
                    )
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            intervalEditor = null,
                            message = EditTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            when (result) {
                is ManualIntervalOperationResult.Saved ->
                    mutableUiState.update { it.copy(intervalEditor = null) }
                is ManualIntervalOperationResult.Invalid ->
                    updateEditor {
                        it.copy(
                            isSaving = false,
                            validationErrors = result.errors,
                        )
                    }
                ManualIntervalOperationResult.TaskNotFound ->
                    showIntervalFailure(EditTaskMessage.TASK_NOT_FOUND)
                ManualIntervalOperationResult.IntervalNotFound ->
                    showIntervalFailure(EditTaskMessage.INTERVAL_NOT_FOUND)
                ManualIntervalOperationResult.RunningTask ->
                    showIntervalFailure(EditTaskMessage.RUNNING_TASK)
                ManualIntervalOperationResult.RunningInterval ->
                    showIntervalFailure(EditTaskMessage.RUNNING_INTERVAL)
                ManualIntervalOperationResult.ConcurrentOverlap ->
                    showIntervalFailure(EditTaskMessage.INTERVAL_CHANGED)
                ManualIntervalOperationResult.Deleted ->
                    showIntervalFailure(EditTaskMessage.DATA_UNAVAILABLE)
            }
        }
    }

    private fun deleteInterval() {
        val interval = mutableUiState.value.intervalPendingDeletion ?: return
        viewModelScope.launch {
            val result =
                runCatching {
                    taskMutationCoordinator.deleteInterval(taskId, interval.id)
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            intervalPendingDeletion = null,
                            message = EditTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            mutableUiState.update {
                it.copy(
                    intervalPendingDeletion = null,
                    message =
                        when (result) {
                            ManualIntervalOperationResult.Deleted -> null
                            ManualIntervalOperationResult.TaskNotFound ->
                                EditTaskMessage.TASK_NOT_FOUND
                            ManualIntervalOperationResult.IntervalNotFound ->
                                EditTaskMessage.INTERVAL_NOT_FOUND
                            ManualIntervalOperationResult.RunningTask ->
                                EditTaskMessage.RUNNING_TASK
                            ManualIntervalOperationResult.RunningInterval ->
                                EditTaskMessage.RUNNING_INTERVAL
                            else -> EditTaskMessage.DATA_UNAVAILABLE
                        },
                )
            }
        }
    }

    private fun deleteTask() {
        viewModelScope.launch {
            val result =
                runCatching {
                    taskMutationCoordinator.deleteTask(taskId)
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            showTaskDeleteConfirmation = false,
                            message = EditTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            when (result) {
                DeleteTaskOperationResult.Deleted ->
                    mutableEffects.emit(EditTaskEffect.NavigateBack)
                DeleteTaskOperationResult.TaskNotFound -> {
                    mutableUiState.update {
                        it.copy(
                            showTaskDeleteConfirmation = false,
                            taskMissing = true,
                            message = EditTaskMessage.TASK_NOT_FOUND,
                        )
                    }
                }
                DeleteTaskOperationResult.RunningTask ->
                    mutableUiState.update {
                        it.copy(
                            showTaskDeleteConfirmation = false,
                            isRunning = true,
                            message = EditTaskMessage.RUNNING_TASK,
                        )
                    }
            }
        }
    }

    private fun requestClose() {
        val state = mutableUiState.value
        if (state.isSavingMetadata) {
            return
        }
        if (state.hasUnsavedMetadataChanges) {
            mutableUiState.update { it.copy(showDiscardConfirmation = true) }
        } else {
            mutableEffects.tryEmit(EditTaskEffect.NavigateBack)
        }
    }

    private fun showIntervalFailure(message: EditTaskMessage) {
        mutableUiState.update {
            it.copy(
                intervalEditor = null,
                message = message,
            )
        }
    }

    private fun updateEditor(
        transform: (IntervalEditorUiState) -> IntervalEditorUiState,
    ) {
        mutableUiState.update { state ->
            state.copy(intervalEditor = state.intervalEditor?.let(transform))
        }
    }

    private fun IntervalEditorUiState.withOffsetChoices(
        zoneId: java.time.ZoneId?,
    ): IntervalEditorUiState {
        if (zoneId == null) {
            return this
        }
        return copy(
            startOffsetChoices = offsetChoices(startLocal, zoneId),
            stopOffsetChoices = offsetChoices(stopLocal, zoneId),
        )
    }

    private fun offsetChoices(
        localDateTime: LocalDateTime,
        zoneId: java.time.ZoneId,
    ): List<OffsetChoiceUi> {
        val offsets = zoneId.rules.getValidOffsets(localDateTime)
        if (offsets.size < 2) {
            return emptyList()
        }
        return listOf(
            OffsetChoiceUi(
                choice = OverlapOffsetChoice.EARLIER_OFFSET,
                offsetLabel = offsets.first().id,
            ),
            OffsetChoiceUi(
                choice = OverlapOffsetChoice.LATER_OFFSET,
                offsetLabel = offsets.last().id,
            ),
        )
    }

    class Factory(
        private val taskId: String,
        private val taskRepository: TaskRepository,
        private val clientRepository: ClientRepository,
        private val employeeRepository: EmployeeRepository,
        private val activeTimerRepository: ActiveTimerRepository,
        private val taskMutationCoordinator: TaskMutationCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EditTaskViewModel::class.java))
            return EditTaskViewModel(
                taskId = taskId,
                taskRepository = taskRepository,
                clientRepository = clientRepository,
                employeeRepository = employeeRepository,
                activeTimerRepository = activeTimerRepository,
                taskMutationCoordinator = taskMutationCoordinator,
            ) as T
        }
    }
}

private fun Client.toClientItem(): ClientItemUi =
    ClientItemUi(id = id, name = name)

private fun Employee.toConsultantItem(): ConsultantItemUi =
    ConsultantItemUi(id = id, name = name)

private data class ObservedEditTaskData(
    val detail: TaskWithIntervals?,
    val clients: List<Client>,
    val employees: List<Employee>,
    val isRunning: Boolean,
)

private fun TaskWithIntervals.completedDuration(): Duration =
    intervals
        .mapNotNull { interval ->
            interval.stop?.let { Duration.between(interval.start, it) }
        }.fold(Duration.ZERO, Duration::plus)

private fun TaskWithIntervals.completedDurationText(): String {
    return DurationMath.formatAccumulated(completedDuration())
}

private fun WorkInterval.toItem(zoneId: java.time.ZoneId): IntervalItemUi {
    return IntervalItemUi(
        id = id,
        ordinal = ordinal,
        startText = ClockTimeFormatter.format(start, zoneId),
        stopText = stop?.let { ClockTimeFormatter.format(it, zoneId) }.orEmpty(),
        isRunning = stop == null,
    )
}

private fun List<OffsetChoiceUi>.choiceFor(offset: ZoneOffset):
    OverlapOffsetChoice? =
    firstOrNull { it.offsetLabel == offset.id }?.choice
