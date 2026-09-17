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
import worq.order.data.TagMutationResult
import worq.order.data.TagRepository
import worq.order.data.TaskMetadataValidationResult
import worq.order.data.TaskMetadataValidator
import worq.order.domain.CreateTaskOperationResult
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.model.Client
import worq.order.model.Tag
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
    private val tagRepository: TagRepository,
    workDate: LocalDate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CreateTaskUiState(workDate = workDate))
    val uiState: StateFlow<CreateTaskUiState> = mutableUiState
    private val mutableEffects = MutableSharedFlow<CreateTaskEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()
    private var clientObservationJob: Job? = null
    private var consultantObservationJob: Job? = null
    private var tagObservationJob: Job? = null
    private var createInFlight = false

    init {
        observeActiveClients()
        observeSelectedConsultant()
        observeTagCatalogs()
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
                    ).withProjectedTagErrors()
                }
            is CreateTaskEvent.EditHardwareSoftwarePurchases ->
                mutableUiState.update {
                    it.copy(
                        hardwareSoftwarePurchases = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    ).withProjectedTagErrors()
                }
            is CreateTaskEvent.OpenTagPicker -> openTagPicker(event.field)
            CreateTaskEvent.DismissTagPicker ->
                mutableUiState.update {
                    if (it.tagInlineEditor?.isSaving == true) it else it.copy(tagPicker = null, tagInlineEditor = null)
                }
            is CreateTaskEvent.EditTagSearch ->
                mutableUiState.update { state ->
                    state.copy(
                        tagPicker =
                            state.tagPicker?.copy(
                                searchQuery = event.query,
                                error = null,
                            ),
                    )
                }
            CreateTaskEvent.ClearTagSearch ->
                mutableUiState.update { state ->
                    state.copy(
                        tagPicker = state.tagPicker?.copy(searchQuery = "", error = null),
                    )
                }
            is CreateTaskEvent.ToggleTagPickerItem -> toggleTagPickerItem(event.itemId)
            CreateTaskEvent.ApplyTagPicker -> applyTagPicker()
            CreateTaskEvent.OpenInlineTagCreate -> openInlineTagCreate()
            is CreateTaskEvent.EditInlineTagText ->
                mutableUiState.update { state ->
                    state.copy(
                        tagInlineEditor =
                            state.tagInlineEditor?.copy(
                                text = event.value,
                                error = null,
                            ),
                    )
                }
            CreateTaskEvent.ConfirmInlineTagCreate -> confirmInlineTagCreate()
            CreateTaskEvent.DismissInlineTagCreate ->
                mutableUiState.update { it.copy(tagInlineEditor = null) }
            is CreateTaskEvent.RemoveAppliedTag -> removeAppliedTag(event.field, event.selectionId)
            is CreateTaskEvent.UseUpdatedTagVersion ->
                useUpdatedTagVersion(event.field, event.selectionId)
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
            is CreateTaskEvent.EditNotes ->
                mutableUiState.update {
                    it.copy(
                        notes = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedTaskChanges = true,
                        message = null,
                    )
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

    private fun observeTagCatalogs() {
        tagObservationJob?.cancel()
        tagObservationJob =
            combine(
                tagRepository.observeTags(TaskTagField.DESCRIPTION.category()),
                tagRepository.observeTags(TaskTagField.HARDWARE_SOFTWARE_PURCHASES.category()),
            ) { descriptionTags, purchaseTags ->
                descriptionTags to purchaseTags
            }.onEach { (descriptionTags, purchaseTags) ->
                mutableUiState.update {
                    it.copy(
                        descriptionCatalogTags = descriptionTags.map(Tag::toTaskTagCatalogItem),
                        purchaseCatalogTags = purchaseTags.map(Tag::toTaskTagCatalogItem),
                    )
                }
            }.catch {
                mutableUiState.update {
                    it.copy(message = CreateTaskMessage.TAG_DATA_UNAVAILABLE)
                }
            }.launchIn(viewModelScope)
    }

    private fun openTagPicker(field: TaskTagField) {
        if (mutableUiState.value.isSavingTask) return
        mutableUiState.update {
            it.copy(
                tagPicker =
                    TaskTagPickerUiState(
                        field = field,
                        draftSelections = it.selectionsFor(field),
                    ),
                tagInlineEditor = null,
                message = null,
            )
        }
    }

    private fun toggleTagPickerItem(itemId: String) {
        mutableUiState.update { state ->
            val picker = state.tagPicker ?: return@update state
            val existing =
                picker.draftSelections.firstOrNull { selection ->
                    selection.id == itemId || selection.sourceTagId == itemId
                }
            val updatedSelections =
                if (existing != null) {
                    picker.draftSelections.filterNot { it.id == existing.id }
                } else {
                    state.catalogFor(picker.field)
                        .firstOrNull { catalogTag -> catalogTag.id == itemId }
                        ?.let { catalogTag ->
                            picker.draftSelections + newTaskTagSelection(catalogTag)
                        }
                        ?: picker.draftSelections
                }
            state.copy(
                tagPicker = picker.copy(draftSelections = updatedSelections, error = null),
            )
        }
    }

    private fun applyTagPicker() {
        mutableUiState.update { state ->
            val picker = state.tagPicker ?: return@update state
            val originalSelections = state.selectionsFor(picker.field)
            val candidate =
                state
                    .withSelections(picker.field, picker.draftSelections)
                    .withProjectedTagErrors()
            if (candidate.hasProjectedTextLimitError(picker.field)) {
                state.copy(
                    tagPicker = picker.copy(error = TaskTagPickerError.COMPOSED_TEXT_TOO_LONG),
                )
            } else {
                candidate.copy(
                    tagPicker = null,
                    tagInlineEditor = null,
                    hasUnsavedTaskChanges =
                        state.hasUnsavedTaskChanges ||
                            picker.draftSelections != originalSelections,
                    message = null,
                )
            }
        }
    }

    private fun openInlineTagCreate() {
        val picker = mutableUiState.value.tagPicker ?: return
        mutableUiState.update {
            it.copy(tagInlineEditor = TaskTagInlineEditorUiState(field = picker.field))
        }
    }

    private fun confirmInlineTagCreate() {
        val editor = mutableUiState.value.tagInlineEditor ?: return
        if (editor.isSaving) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(tagInlineEditor = editor.copy(isSaving = true, error = null))
            }
            val result =
                runCatching {
                    tagRepository.createTag(editor.field.category(), editor.text)
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            tagInlineEditor = editor.copy(error = TaskTagInlineEditorError.DATA_UNAVAILABLE),
                        )
                    }
                    return@launch
                }
            when (result) {
                is TagMutationResult.Created -> selectInlineCreatedTag(editor.field, result.tag)
                is TagMutationResult.DuplicateNormalizedText -> {
                    val existing = runCatching { tagRepository.readTag(result.conflictingTagId) }.getOrNull()
                    if (existing == null) {
                        mutableUiState.update {
                            it.copy(
                                tagInlineEditor =
                                    editor.copy(error = TaskTagInlineEditorError.DATA_UNAVAILABLE),
                            )
                        }
                    } else {
                        selectInlineCreatedTag(editor.field, existing)
                    }
                }
                is TagMutationResult.InvalidText ->
                    mutableUiState.update {
                        it.copy(
                            tagInlineEditor =
                                editor.copy(
                                    error =
                                        when (result.reason) {
                                            worq.order.data.TagTextValidationError.BLANK ->
                                                TaskTagInlineEditorError.BLANK
                                            worq.order.data.TagTextValidationError.TOO_LONG ->
                                                TaskTagInlineEditorError.TOO_LONG
                                        },
                                ),
                        )
                    }
                TagMutationResult.NotFound,
                TagMutationResult.Deleted,
                is TagMutationResult.Updated,
                ->
                    mutableUiState.update {
                        it.copy(
                            tagInlineEditor =
                                editor.copy(error = TaskTagInlineEditorError.DATA_UNAVAILABLE),
                        )
                    }
            }
        }
    }

    private fun selectInlineCreatedTag(
        field: TaskTagField,
        tag: Tag,
    ) {
        mutableUiState.update { state ->
            val picker = state.tagPicker?.takeIf { it.field == field } ?: return@update state
            val catalogTag = tag.toTaskTagCatalogItem()
            val updatedSelections =
                if (picker.draftSelections.any { it.sourceTagId == tag.id }) {
                    picker.draftSelections
                } else {
                    picker.draftSelections + newTaskTagSelection(catalogTag)
                }
            state
                .withCatalog(field, state.catalogFor(field).upsert(catalogTag))
                .copy(
                    tagPicker = picker.copy(draftSelections = updatedSelections, searchQuery = "", error = null),
                    tagInlineEditor = null,
                )
        }
    }

    private fun removeAppliedTag(
        field: TaskTagField,
        selectionId: String,
    ) {
        mutableUiState.update { state ->
            state
                .withSelections(
                    field,
                    state.selectionsFor(field).filterNot { it.id == selectionId },
                ).withProjectedTagErrors()
                .copy(hasUnsavedTaskChanges = true, message = null)
        }
    }

    private fun useUpdatedTagVersion(
        field: TaskTagField,
        selectionId: String,
    ) {
        mutableUiState.update { state ->
            val replacement =
                state.selectionsFor(field).map { selection ->
                    if (selection.id == selectionId) {
                        selection.updatedCatalogText(state.catalogFor(field))?.let { latestText ->
                            selection.copy(text = latestText)
                        } ?: selection
                    } else {
                        selection
                    }
                }
            state
                .withSelections(field, replacement)
                .withProjectedTagErrors()
                .copy(hasUnsavedTaskChanges = true, message = null)
        }
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
                notes = state.notes,
                descriptionTagSnapshots = state.descriptionTagSelections.toSnapshotDrafts(),
                hardwareSoftwarePurchaseTagSnapshots = state.purchaseTagSelections.toSnapshotDrafts(),
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
                        notes = state.notes,
                        descriptionTagSnapshots = state.descriptionTagSelections.toSnapshotDrafts(),
                        hardwareSoftwarePurchaseTagSnapshots =
                            state.purchaseTagSelections.toSnapshotDrafts(),
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
        private val tagRepository: TagRepository,
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
                tagRepository = tagRepository,
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

private fun CreateTaskUiState.selectionsFor(field: TaskTagField): List<TaskTagSelectionUi> =
    when (field) {
        TaskTagField.DESCRIPTION -> descriptionTagSelections
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> purchaseTagSelections
    }

private fun CreateTaskUiState.catalogFor(field: TaskTagField): List<TaskTagCatalogItemUi> =
    when (field) {
        TaskTagField.DESCRIPTION -> descriptionCatalogTags
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> purchaseCatalogTags
    }

private fun CreateTaskUiState.withSelections(
    field: TaskTagField,
    selections: List<TaskTagSelectionUi>,
): CreateTaskUiState =
    when (field) {
        TaskTagField.DESCRIPTION -> copy(descriptionTagSelections = selections)
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> copy(purchaseTagSelections = selections)
    }

private fun CreateTaskUiState.withCatalog(
    field: TaskTagField,
    catalog: List<TaskTagCatalogItemUi>,
): CreateTaskUiState =
    when (field) {
        TaskTagField.DESCRIPTION -> copy(descriptionCatalogTags = catalog)
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> copy(purchaseCatalogTags = catalog)
    }

private fun CreateTaskUiState.withProjectedTagErrors(): CreateTaskUiState {
    val retainedErrors =
        metadataErrors -
            setOf(
                worq.order.data.TaskMetadataValidationError.DESCRIPTION_TOO_LONG,
                worq.order.data.TaskMetadataValidationError.PURCHASES_TOO_LONG,
            )
    return copy(
        metadataErrors =
            retainedErrors +
                projectedTagTextErrors(
                    description = description,
                    descriptionSelections = descriptionTagSelections,
                    purchases = hardwareSoftwarePurchases,
                    purchaseSelections = purchaseTagSelections,
                ),
    )
}

private fun CreateTaskUiState.hasProjectedTextLimitError(field: TaskTagField): Boolean =
    when (field) {
        TaskTagField.DESCRIPTION ->
            worq.order.data.TaskMetadataValidationError.DESCRIPTION_TOO_LONG in metadataErrors
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES ->
            worq.order.data.TaskMetadataValidationError.PURCHASES_TOO_LONG in metadataErrors
    }
