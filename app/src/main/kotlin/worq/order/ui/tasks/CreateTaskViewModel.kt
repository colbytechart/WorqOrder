package worq.order.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
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
import worq.order.data.ClientMutationResult
import worq.order.data.ClientNameValidationResult
import worq.order.data.ClientNameNormalizer
import worq.order.data.ClientRepository
import worq.order.data.EmployeeRepository
import worq.order.data.MileageNormalizer
import worq.order.data.SettingsRepository
import worq.order.data.TaskMetadataValidationResult
import worq.order.data.TaskMetadataValidator
import worq.order.domain.CreateTaskOperationResult
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.model.Client
import worq.order.ui.clients.ArchivedClientRestoreOffer
import worq.order.ui.clients.ClientEditorMode
import worq.order.ui.clients.ClientEditorUiState
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.clients.ClientNameFieldError
import worq.order.ui.clients.toFieldError

class CreateTaskViewModel(
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository,
    private val settingsRepository: SettingsRepository,
    private val consultantSelectionCoordinator: ConsultantSelectionCoordinator,
    private val taskMutationCoordinator: TaskMutationCoordinator,
    workDate: LocalDate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CreateTaskUiState(workDate = workDate))
    val uiState: StateFlow<CreateTaskUiState> = mutableUiState
    private val mutableEffects = MutableSharedFlow<CreateTaskEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()
    private var clientObservationJob: Job? = null
    private var consultantObservationJob: Job? = null
    private var createInFlight = false

    init {
        observeActiveClients()
        observeSelectedConsultant()
    }

    fun onEvent(event: CreateTaskEvent) {
        when (event) {
            CreateTaskEvent.RetryClients -> observeActiveClients()
            CreateTaskEvent.OpenClientMenu ->
                mutableUiState.update {
                    it.copy(
                        isClientMenuExpanded = it.activeClients.isNotEmpty(),
                        message = null,
                    )
                }
            CreateTaskEvent.DismissClientMenu ->
                mutableUiState.update { it.copy(isClientMenuExpanded = false) }
            is CreateTaskEvent.SelectClient ->
                selectClient(event.clientId)
            is CreateTaskEvent.EditDescription ->
                mutableUiState.update {
                    it.copy(
                        description = event.description,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    )
                }
            is CreateTaskEvent.EditHardwareSoftwarePurchases ->
                mutableUiState.update {
                    it.copy(
                        hardwareSoftwarePurchases = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    )
                }
            is CreateTaskEvent.SelectWorkType ->
                mutableUiState.update {
                    it.copy(
                        workType = event.workType,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    )
                }
            is CreateTaskEvent.SelectBillingStatus ->
                mutableUiState.update {
                    it.copy(
                        billingStatus = event.billingStatus,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    )
                }
            is CreateTaskEvent.EditMileage ->
                if (MileageNormalizer.acceptsInput(event.value)) {
                    mutableUiState.update {
                        it.copy(
                            mileage = event.value,
                            metadataErrors = emptySet(),
                            hasUnsavedTaskChanges = true,
                            message = null,
                        )
                    }
                }
            CreateTaskEvent.CreateTask -> createTask()
            CreateTaskEvent.RequestClose -> requestClose()
            CreateTaskEvent.ConfirmDiscard -> {
                mutableUiState.update { it.copy(showDiscardConfirmation = false) }
                mutableEffects.tryEmit(CreateTaskEffect.NavigateBack)
            }
            CreateTaskEvent.DismissDiscard ->
                mutableUiState.update { it.copy(showDiscardConfirmation = false) }
            CreateTaskEvent.OpenAddClient ->
                mutableUiState.update {
                    it.copy(
                        isClientMenuExpanded = false,
                        addClientEditor =
                            ClientEditorUiState(mode = ClientEditorMode.ADD),
                        message = null,
                    )
                }
            CreateTaskEvent.OpenConsultantSettings ->
                mutableEffects.tryEmit(CreateTaskEffect.NavigateToSettings)
            is CreateTaskEvent.EditNewClientName ->
                mutableUiState.update { state ->
                    state.copy(
                        addClientEditor =
                            state.addClientEditor?.copy(
                                name = event.name,
                                fieldError = null,
                            ),
                    )
                }
            CreateTaskEvent.ConfirmAddClient -> confirmAddClient()
            CreateTaskEvent.DismissAddClient ->
                mutableUiState.update { it.copy(addClientEditor = null) }
            CreateTaskEvent.ConfirmRestoreOffer -> confirmRestoreOffer()
            CreateTaskEvent.DismissRestoreOffer ->
                mutableUiState.update { it.copy(restoreOffer = null) }
            CreateTaskEvent.DismissMessage ->
                mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeActiveClients() {
        clientObservationJob?.cancel()
        clientObservationJob =
            clientRepository
                .observeActiveClients()
                .onStart {
                    mutableUiState.update {
                        it.copy(
                            isLoadingClients = true,
                            hasClientLoadError = false,
                        )
                    }
                }.onEach { clients ->
                    mutableUiState.update { state ->
                        val activeClients = clients.map(Client::toUi)
                        val retainedSelection =
                            state.selectedClientId
                                ?.takeIf { selectedId ->
                                    activeClients.any { it.id == selectedId }
                                }
                        state.copy(
                            isLoadingClients = false,
                            hasClientLoadError = false,
                            activeClients = activeClients,
                            selectedClientId = retainedSelection,
                            message =
                                if (
                                    state.selectedClientId != null &&
                                    retainedSelection == null
                                ) {
                                    CreateTaskMessage.CLIENT_ARCHIVED
                                } else {
                                    state.message
                                },
                        )
                    }
                }.catch {
                    mutableUiState.update {
                        it.copy(
                            isLoadingClients = false,
                            hasClientLoadError = true,
                        )
                    }
                }.launchIn(viewModelScope)
    }

    private fun observeSelectedConsultant() {
        consultantObservationJob?.cancel()
        consultantObservationJob =
            combine(
                employeeRepository.observeActiveEmployees(),
                settingsRepository.observeSettings(),
            ) { employees, settings ->
                employees to settings.selectedEmployeeId
            }.onStart {
                mutableUiState.update {
                    it.copy(
                        isLoadingConsultant = true,
                        hasConsultantLoadError = false,
                    )
                }
            }.onEach { (employees, selectedId) ->
                val selected = employees.firstOrNull { it.id == selectedId }
                mutableUiState.update { state ->
                    state.copy(
                        isLoadingConsultant = false,
                        hasConsultantLoadError = false,
                        selectedConsultantId = selected?.id,
                        selectedConsultantName = selected?.name,
                        message =
                            if (selectedId != null && selected == null) {
                                CreateTaskMessage.CONSULTANT_ARCHIVED
                            } else {
                                state.message
                            },
                    )
                }
                if (selectedId != null && selected == null) {
                    viewModelScope.launch {
                        runCatching { consultantSelectionCoordinator.reconcileSelection() }
                    }
                }
            }.catch {
                mutableUiState.update {
                    it.copy(
                        isLoadingConsultant = false,
                        hasConsultantLoadError = true,
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun selectClient(clientId: String) {
        if (mutableUiState.value.activeClients.none { it.id == clientId }) {
            return
        }
        mutableUiState.update {
            it.copy(
                selectedClientId = clientId,
                isClientMenuExpanded = false,
                hasUnsavedTaskChanges = true,
                message = null,
            )
        }
    }

    private fun confirmAddClient() {
        val editor = mutableUiState.value.addClientEditor ?: return
        if (editor.isSaving) {
            return
        }
        when (val validation = ClientNameNormalizer.validate(editor.name)) {
            is ClientNameValidationResult.Invalid -> {
                mutableUiState.update {
                    it.copy(
                        addClientEditor =
                            editor.copy(
                                fieldError = validation.error.toFieldError(),
                            ),
                    )
                }
                return
            }
            is ClientNameValidationResult.Valid -> Unit
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(addClientEditor = editor.copy(isSaving = true))
            }
            val result =
                runCatching {
                    clientRepository.addClient(editor.name)
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            addClientEditor = editor.copy(isSaving = false),
                            message = CreateTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            when (result) {
                is ClientMutationResult.Success ->
                    mutableUiState.update {
                        it.copy(
                            selectedClientId = result.client.id,
                            addClientEditor = null,
                            message = null,
                        )
                    }
                is ClientMutationResult.MatchingArchivedClient ->
                    mutableUiState.update {
                        it.copy(
                            addClientEditor = null,
                            restoreOffer =
                                ArchivedClientRestoreOffer(
                                    clientId = result.client.id,
                                    clientName = result.client.name,
                                ),
                        )
                    }
                is ClientMutationResult.InvalidName ->
                    mutableUiState.update {
                        it.copy(
                            addClientEditor =
                                editor.copy(
                                    fieldError = result.reason.toFieldError(),
                                    isSaving = false,
                                ),
                        )
                    }
                is ClientMutationResult.DuplicateActiveName ->
                    mutableUiState.update {
                        it.copy(
                            addClientEditor =
                                editor.copy(
                                    fieldError = ClientNameFieldError.DUPLICATE_ACTIVE,
                                    isSaving = false,
                                ),
                        )
                    }
                ClientMutationResult.NotFound ->
                    mutableUiState.update {
                        it.copy(
                            addClientEditor = null,
                            message = CreateTaskMessage.CLIENT_NOT_FOUND,
                        )
                    }
            }
        }
    }

    private fun confirmRestoreOffer() {
        val offer = mutableUiState.value.restoreOffer ?: return
        if (offer.isRestoring) {
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(restoreOffer = offer.copy(isRestoring = true))
            }
            val result =
                runCatching {
                    clientRepository.restoreClient(offer.clientId)
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            restoreOffer = null,
                            message = CreateTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            mutableUiState.update { state ->
                when (result) {
                    is ClientMutationResult.Success ->
                        state.copy(
                            selectedClientId = result.client.id,
                            restoreOffer = null,
                            message = null,
                        )
                    is ClientMutationResult.DuplicateActiveName ->
                        state.copy(
                            restoreOffer = null,
                            message = CreateTaskMessage.RESTORE_NAME_CONFLICT,
                        )
                    ClientMutationResult.NotFound ->
                        state.copy(
                            restoreOffer = null,
                            message = CreateTaskMessage.CLIENT_NOT_FOUND,
                        )
                    is ClientMutationResult.InvalidName,
                    is ClientMutationResult.MatchingArchivedClient,
                    ->
                        state.copy(
                            restoreOffer = null,
                            message = CreateTaskMessage.DATA_UNAVAILABLE,
                        )
                }
            }
        }
    }

    private fun createTask() {
        val state = mutableUiState.value
        if (state.isSavingTask || createInFlight) {
            return
        }
        val clientId = state.selectedClientId
        val consultantId = state.selectedConsultantId
        val validation =
            TaskMetadataValidator.validate(
                description = state.description,
                hardwareSoftwarePurchases = state.hardwareSoftwarePurchases,
                workType = state.workType,
                billingStatus = state.billingStatus,
                mileage = state.mileage,
            )
        val metadataErrors =
            (validation as? TaskMetadataValidationResult.Invalid)
                ?.errors
                .orEmpty()
        if (clientId == null || consultantId == null || metadataErrors.isNotEmpty()) {
            mutableUiState.update {
                it.copy(
                    metadataErrors = metadataErrors,
                    message =
                        if (clientId == null) {
                            CreateTaskMessage.CLIENT_REQUIRED
                        } else if (consultantId == null) {
                            CreateTaskMessage.CONSULTANT_REQUIRED
                        } else {
                            null
                        },
                )
            }
            return
        }
        createInFlight = true
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSavingTask = true,
                    message = null,
                )
            }
            val result =
                runCatching {
                    taskMutationCoordinator.createTask(
                        clientId = clientId,
                        description = state.description,
                        hardwareSoftwarePurchases =
                            state.hardwareSoftwarePurchases,
                        workDate = state.workDate,
                        employeeId = consultantId,
                        workType = state.workType,
                        billingStatus = state.billingStatus,
                        mileage = state.mileage,
                    )
                }.getOrElse {
                    createInFlight = false
                    mutableUiState.update {
                        it.copy(
                            isSavingTask = false,
                            message = CreateTaskMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            when (result) {
                is CreateTaskOperationResult.Created -> {
                    createInFlight = false
                    mutableUiState.update {
                        it.copy(
                            isSavingTask = false,
                            hasUnsavedTaskChanges = false,
                        )
                    }
                    mutableEffects.emit(CreateTaskEffect.NavigateBack)
                }
                is CreateTaskOperationResult.InvalidMetadata -> {
                    createInFlight = false
                    mutableUiState.update {
                        it.copy(
                            isSavingTask = false,
                            metadataErrors = result.errors,
                        )
                    }
                }
                CreateTaskOperationResult.ClientUnavailable -> {
                    createInFlight = false
                    mutableUiState.update {
                        it.copy(
                            isSavingTask = false,
                            selectedClientId = null,
                            message = CreateTaskMessage.CLIENT_ARCHIVED,
                        )
                    }
                }
                CreateTaskOperationResult.ConsultantUnavailable -> {
                    createInFlight = false
                    runCatching { consultantSelectionCoordinator.reconcileSelection() }
                    mutableUiState.update {
                        it.copy(
                            isSavingTask = false,
                            selectedConsultantId = null,
                            selectedConsultantName = null,
                            message = CreateTaskMessage.CONSULTANT_ARCHIVED,
                        )
                    }
                }
            }
        }
    }

    private fun requestClose() {
        val state = mutableUiState.value
        if (state.isSavingTask) {
            return
        }
        if (state.hasUnsavedTaskChanges) {
            mutableUiState.update { it.copy(showDiscardConfirmation = true) }
        } else {
            mutableEffects.tryEmit(CreateTaskEffect.NavigateBack)
        }
    }

    class Factory(
        private val clientRepository: ClientRepository,
        private val employeeRepository: EmployeeRepository,
        private val settingsRepository: SettingsRepository,
        private val consultantSelectionCoordinator: ConsultantSelectionCoordinator,
        private val taskMutationCoordinator: TaskMutationCoordinator,
        private val workDate: LocalDate,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateTaskViewModel::class.java))
            return CreateTaskViewModel(
                clientRepository = clientRepository,
                employeeRepository = employeeRepository,
                settingsRepository = settingsRepository,
                consultantSelectionCoordinator = consultantSelectionCoordinator,
                taskMutationCoordinator = taskMutationCoordinator,
                workDate = workDate,
            ) as T
        }
    }
}

private fun Client.toUi() =
    ClientItemUi(
        id = id,
        name = name,
    )
