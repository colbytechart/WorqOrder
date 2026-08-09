package worq.order.ui.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.EmployeeMutationResult
import worq.order.data.EmployeeNameNormalizer
import worq.order.data.EmployeeNameValidationResult
import worq.order.data.EmployeeRepository
import worq.order.data.SettingsRepository
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.domain.ConsultantSelectionResult
import worq.order.model.Employee

class ConsultantSettingsViewModel(
    private val employeeRepository: EmployeeRepository,
    private val settingsRepository: SettingsRepository,
    private val selectionCoordinator: ConsultantSelectionCoordinator,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ConsultantSettingsUiState())
    val uiState: StateFlow<ConsultantSettingsUiState> = mutableUiState
    private var observationJob: Job? = null

    init {
        observeDirectory()
        viewModelScope.launch {
            runCatching { selectionCoordinator.reconcileSelection() }
        }
    }

    fun onEvent(event: ConsultantSettingsEvent) {
        when (event) {
            ConsultantSettingsEvent.Retry -> observeDirectory()
            ConsultantSettingsEvent.OpenSelectionMenu ->
                mutableUiState.update {
                    it.copy(
                        isSelectionMenuExpanded = it.activeConsultants.isNotEmpty(),
                        message = null,
                    )
                }
            ConsultantSettingsEvent.DismissSelectionMenu ->
                mutableUiState.update { it.copy(isSelectionMenuExpanded = false) }
            is ConsultantSettingsEvent.SelectConsultant -> selectConsultant(event.consultantId)
            ConsultantSettingsEvent.OpenAddConsultant -> openAddConsultant()
            is ConsultantSettingsEvent.OpenRenameConsultant ->
                openRenameConsultant(event.consultantId)
            is ConsultantSettingsEvent.EditName ->
                mutableUiState.update { state ->
                    state.copy(
                        editor = state.editor?.copy(name = event.name, fieldError = null),
                    )
                }
            ConsultantSettingsEvent.ConfirmEditor -> confirmEditor()
            ConsultantSettingsEvent.DismissEditor ->
                mutableUiState.update { it.copy(editor = null) }
            is ConsultantSettingsEvent.RequestArchive -> requestArchive(event.consultantId)
            ConsultantSettingsEvent.ConfirmArchive -> confirmArchive()
            ConsultantSettingsEvent.DismissArchive ->
                mutableUiState.update { it.copy(archiveConfirmation = null) }
            is ConsultantSettingsEvent.RestoreConsultant -> restoreConsultant(event.consultantId)
            ConsultantSettingsEvent.ConfirmRestoreOffer -> confirmRestoreOffer()
            ConsultantSettingsEvent.DismissRestoreOffer ->
                mutableUiState.update { it.copy(restoreOffer = null) }
            ConsultantSettingsEvent.DismissMessage ->
                mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeDirectory() {
        observationJob?.cancel()
        observationJob =
            combine(
                employeeRepository.observeAllEmployees(),
                settingsRepository.observeSettings(),
            ) { employees, settings -> employees to settings.selectedEmployeeId }
                .onStart {
                    mutableUiState.update { it.copy(isLoading = true, hasLoadError = false) }
                }.onEach { (employees, selectedId) ->
                    val active = employees.filter(Employee::isActive).map(Employee::toUi)
                    val retainedSelection = selectedId?.takeIf { id -> active.any { it.id == id } }
                    mutableUiState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasLoadError = false,
                            activeConsultants = active,
                            archivedConsultants =
                                employees.filterNot(Employee::isActive).map(Employee::toUi),
                            selectedConsultantId = retainedSelection,
                        )
                    }
                    if (selectedId != null && retainedSelection == null) {
                        viewModelScope.launch {
                            runCatching { selectionCoordinator.reconcileSelection() }
                        }
                    }
                }.catch {
                    mutableUiState.update {
                        it.copy(isLoading = false, hasLoadError = true)
                    }
                }.launchIn(viewModelScope)
    }

    private fun selectConsultant(consultantId: String) {
        val state = mutableUiState.value
        if (state.isSavingSelection || state.pendingConsultantId != null) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSavingSelection = true,
                    isSelectionMenuExpanded = false,
                    message = null,
                )
            }
            val result =
                runCatching { selectionCoordinator.select(consultantId) }
                    .getOrElse {
                        mutableUiState.update {
                            it.copy(
                                isSavingSelection = false,
                                message = ConsultantSettingsMessage.DATA_UNAVAILABLE,
                            )
                        }
                        return@launch
                    }
            mutableUiState.update {
                when (result) {
                    is ConsultantSelectionResult.Selected ->
                        it.copy(isSavingSelection = false, message = null)
                    ConsultantSelectionResult.NotFound ->
                        it.copy(
                            isSavingSelection = false,
                            message = ConsultantSettingsMessage.CONSULTANT_NOT_FOUND,
                        )
                    ConsultantSelectionResult.Archived ->
                        it.copy(
                            isSavingSelection = false,
                            message = ConsultantSettingsMessage.CONSULTANT_ARCHIVED,
                        )
                }
            }
        }
    }

    private fun openAddConsultant() {
        if (isMutationPending()) return
        mutableUiState.update {
            it.copy(
                editor = ConsultantEditorUiState(ConsultantEditorMode.ADD),
                message = null,
            )
        }
    }

    private fun openRenameConsultant(consultantId: String) {
        if (isMutationPending()) return
        val consultant = mutableUiState.value.activeConsultants.firstOrNull {
            it.id == consultantId
        } ?: return
        mutableUiState.update {
            it.copy(
                editor =
                    ConsultantEditorUiState(
                        mode = ConsultantEditorMode.RENAME,
                        consultantId = consultant.id,
                        name = consultant.name,
                    ),
                message = null,
            )
        }
    }

    private fun confirmEditor() {
        val editor = mutableUiState.value.editor ?: return
        if (editor.isSaving || isMutationPending()) return
        when (val validation = EmployeeNameNormalizer.validate(editor.name)) {
            is EmployeeNameValidationResult.Invalid -> {
                mutableUiState.update {
                    it.copy(editor = editor.copy(fieldError = validation.error.toFieldError()))
                }
                return
            }
            is EmployeeNameValidationResult.Valid -> Unit
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(editor = editor.copy(isSaving = true)) }
            val result =
                runCatching {
                    when (editor.mode) {
                        ConsultantEditorMode.ADD -> employeeRepository.addEmployee(editor.name)
                        ConsultantEditorMode.RENAME ->
                            employeeRepository.renameEmployee(
                                requireNotNull(editor.consultantId),
                                editor.name,
                            )
                    }
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            editor = editor.copy(isSaving = false),
                            message = ConsultantSettingsMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            handleEditorResult(editor, result)
        }
    }

    private fun handleEditorResult(
        editor: ConsultantEditorUiState,
        result: EmployeeMutationResult,
    ) {
        mutableUiState.update { state ->
            when (result) {
                is EmployeeMutationResult.Success -> state.copy(editor = null, message = null)
                is EmployeeMutationResult.MatchingArchivedEmployee ->
                    state.copy(
                        editor = null,
                        restoreOffer =
                            ArchivedConsultantRestoreOffer(
                                consultantId = result.employee.id,
                                consultantName = result.employee.name,
                            ),
                    )
                is EmployeeMutationResult.InvalidName ->
                    state.copy(
                        editor =
                            editor.copy(
                                fieldError = result.reason.toFieldError(),
                                isSaving = false,
                            ),
                    )
                is EmployeeMutationResult.DuplicateActiveName ->
                    state.copy(
                        editor =
                            editor.copy(
                                fieldError = ConsultantNameFieldError.DUPLICATE_ACTIVE,
                                isSaving = false,
                            ),
                    )
                EmployeeMutationResult.NotFound ->
                    state.copy(
                        editor = null,
                        message = ConsultantSettingsMessage.CONSULTANT_NOT_FOUND,
                    )
            }
        }
    }

    private fun requestArchive(consultantId: String) {
        if (isMutationPending()) return
        val state = mutableUiState.value
        val consultant = state.activeConsultants.firstOrNull { it.id == consultantId } ?: return
        mutableUiState.update {
            it.copy(
                archiveConfirmation =
                    ArchiveConsultantConfirmation(
                        consultantId = consultant.id,
                        consultantName = consultant.name,
                        wasSelected = consultant.id == state.selectedConsultantId,
                    ),
                message = null,
            )
        }
    }

    private fun confirmArchive() {
        val confirmation = mutableUiState.value.archiveConfirmation ?: return
        if (confirmation.isArchiving) return
        mutableUiState.update {
            it.copy(archiveConfirmation = confirmation.copy(isArchiving = true))
        }
        mutateConsultant(confirmation.consultantId) {
            selectionCoordinator.archive(confirmation.consultantId)
        }
    }

    private fun restoreConsultant(consultantId: String) {
        if (isMutationPending()) return
        mutateConsultant(consultantId) {
            employeeRepository.restoreEmployee(consultantId)
        }
    }

    private fun confirmRestoreOffer() {
        val offer = mutableUiState.value.restoreOffer ?: return
        if (offer.isRestoring) return
        mutableUiState.update { it.copy(restoreOffer = offer.copy(isRestoring = true)) }
        mutateConsultant(offer.consultantId) {
            employeeRepository.restoreEmployee(offer.consultantId)
        }
    }

    private fun mutateConsultant(
        consultantId: String,
        mutation: suspend () -> EmployeeMutationResult,
    ) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(pendingConsultantId = consultantId, message = null) }
            val result =
                runCatching { mutation() }
                    .getOrElse {
                        mutableUiState.update {
                            it.copy(
                                archiveConfirmation = null,
                                restoreOffer = null,
                                pendingConsultantId = null,
                                message = ConsultantSettingsMessage.DATA_UNAVAILABLE,
                            )
                        }
                        return@launch
                    }
            mutableUiState.update { state ->
                when (result) {
                    is EmployeeMutationResult.Success ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingConsultantId = null,
                            message = null,
                        )
                    is EmployeeMutationResult.DuplicateActiveName ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingConsultantId = null,
                            message = ConsultantSettingsMessage.RESTORE_NAME_CONFLICT,
                        )
                    EmployeeMutationResult.NotFound ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingConsultantId = null,
                            message = ConsultantSettingsMessage.CONSULTANT_NOT_FOUND,
                        )
                    is EmployeeMutationResult.InvalidName,
                    is EmployeeMutationResult.MatchingArchivedEmployee,
                    ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingConsultantId = null,
                            message = ConsultantSettingsMessage.DATA_UNAVAILABLE,
                        )
                }
            }
        }
    }

    private fun isMutationPending(): Boolean =
        mutableUiState.value.pendingConsultantId != null ||
            mutableUiState.value.isSavingSelection

    class Factory(
        private val employeeRepository: EmployeeRepository,
        private val settingsRepository: SettingsRepository,
        private val selectionCoordinator: ConsultantSelectionCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConsultantSettingsViewModel::class.java))
            return ConsultantSettingsViewModel(
                employeeRepository = employeeRepository,
                settingsRepository = settingsRepository,
                selectionCoordinator = selectionCoordinator,
            ) as T
        }
    }
}

private fun Employee.toUi(): ConsultantItemUi = ConsultantItemUi(id = id, name = name)
