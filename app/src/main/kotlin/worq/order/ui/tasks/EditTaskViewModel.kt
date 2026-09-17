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
import worq.order.data.TagMutationResult
import worq.order.data.TagRepository
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
import worq.order.model.Tag
import worq.order.model.TagCategory
import worq.order.model.TaskTagSnapshot
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
    private val tagRepository: TagRepository,
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
                    ).withProjectedTagErrors()
                }
            is EditTaskEvent.EditHardwareSoftwarePurchases ->
                mutableUiState.update {
                    it.copy(
                        hardwareSoftwarePurchases = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    ).withProjectedTagErrors()
                }
            is EditTaskEvent.OpenTagPicker -> openTagPicker(event.field)
            EditTaskEvent.DismissTagPicker ->
                mutableUiState.update {
                    if (it.tagInlineEditor?.isSaving == true) it else it.copy(tagPicker = null, tagInlineEditor = null)
                }
            is EditTaskEvent.EditTagSearch ->
                mutableUiState.update { state ->
                    state.copy(
                        tagPicker =
                            state.tagPicker?.copy(searchQuery = event.query, error = null),
                    )
                }
            EditTaskEvent.ClearTagSearch ->
                mutableUiState.update { state ->
                    state.copy(tagPicker = state.tagPicker?.copy(searchQuery = "", error = null))
                }
            is EditTaskEvent.ToggleTagPickerItem -> toggleTagPickerItem(event.itemId)
            EditTaskEvent.ApplyTagPicker -> applyTagPicker()
            EditTaskEvent.OpenInlineTagCreate -> openInlineTagCreate()
            is EditTaskEvent.EditInlineTagText ->
                mutableUiState.update { state ->
                    state.copy(
                        tagInlineEditor =
                            state.tagInlineEditor?.copy(text = event.value, error = null),
                    )
                }
            EditTaskEvent.ConfirmInlineTagCreate -> confirmInlineTagCreate()
            EditTaskEvent.DismissInlineTagCreate ->
                mutableUiState.update { it.copy(tagInlineEditor = null) }
            is EditTaskEvent.RemoveAppliedTag -> removeAppliedTag(event.field, event.selectionId)
            is EditTaskEvent.UseUpdatedTagVersion ->
                useUpdatedTagVersion(event.field, event.selectionId)
            is EditTaskEvent.SelectWorkType ->
                mutableUiState.update {
                    it.copy(
                        workType = event.workType,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    )
                }
            is EditTaskEvent.SelectBillingStatus ->
                mutableUiState.update {
                    it.copy(
                        billingStatus = event.billingStatus,
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
            is EditTaskEvent.EditNotes ->
                mutableUiState.update {
                    it.copy(
                        notes = event.value,
                        metadataErrors = emptySet(),
                        hasUnsavedMetadataChanges = true,
                        message = null,
                    )
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
                            state.interval?.takeIf { it.id == event.intervalId },
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
        val observedTaskData =
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
            }
        val tagCatalogs =
            combine(
                tagRepository.observeTags(TagCategory.DESCRIPTION),
                tagRepository.observeTags(TagCategory.HARDWARE_SOFTWARE_PURCHASE),
            ) { descriptionTags, purchaseTags ->
                descriptionTags to purchaseTags
            }
        observationJob =
            combine(observedTaskData, tagCatalogs) { observed, (descriptionTags, purchaseTags) ->
                observed.copy(
                    descriptionTags = descriptionTags,
                    purchaseTags = purchaseTags,
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
                val descriptionCatalogTags = observed.descriptionTags.map(Tag::toTaskTagCatalogItem)
                val purchaseCatalogTags = observed.purchaseTags.map(Tag::toTaskTagCatalogItem)
                latestDetail = detail
                val clientItems = clients.map(Client::toClientItem)
                if (detail == null) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            taskMissing = true,
                            activeClients = clientItems,
                            activeConsultants = consultants,
                            descriptionCatalogTags = descriptionCatalogTags,
                            purchaseCatalogTags = purchaseCatalogTags,
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
                        descriptionCatalogTags = descriptionCatalogTags,
                        purchaseCatalogTags = purchaseCatalogTags,
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
                        descriptionTagSelections =
                            if (refreshMetadata) {
                                detail.tagSnapshots.toSelections(TaskTagField.DESCRIPTION)
                            } else {
                                state.descriptionTagSelections
                            },
                        purchaseTagSelections =
                            if (refreshMetadata) {
                                detail.tagSnapshots.toSelections(
                                    TaskTagField.HARDWARE_SOFTWARE_PURCHASES,
                                )
                            } else {
                                state.purchaseTagSelections
                            },
                        selectedConsultantId =
                            if (refreshMetadata) {
                                task.employeeId
                            } else {
                                state.selectedConsultantId
                            },
                        workType =
                            if (refreshMetadata) task.workType else state.workType,
                        billingStatus =
                            if (refreshMetadata) task.billingStatus else state.billingStatus,
                        mileage =
                            if (refreshMetadata) task.mileage.orEmpty() else state.mileage,
                        notes = if (refreshMetadata) task.notes else state.notes,
                        totalDuration = detail.completedDurationText(),
                        billingMinutes =
                            BillingMinutes.fromDuration(detail.completedDuration()),
                        interval = detail.intervals.singleOrNull()?.toItem(task.zoneId),
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
                billingStatus = state.billingStatus,
                mileage = state.mileage,
                notes = state.notes,
                descriptionTagSnapshots = state.descriptionTagSelections.toSnapshotDrafts(),
                hardwareSoftwarePurchaseTagSnapshots = state.purchaseTagSelections.toSnapshotDrafts(),
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
                        billingStatus = state.billingStatus,
                        mileage = state.mileage,
                        notes = state.notes,
                        descriptionTagSnapshots = state.descriptionTagSelections.toSnapshotDrafts(),
                        hardwareSoftwarePurchaseTagSnapshots =
                            state.purchaseTagSelections.toSnapshotDrafts(),
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

    private fun openTagPicker(field: TaskTagField) {
        val state = mutableUiState.value
        if (state.isRunning || state.isSavingMetadata) return
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
            state.copy(tagPicker = picker.copy(draftSelections = updatedSelections, error = null))
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
                    hasUnsavedMetadataChanges =
                        state.hasUnsavedMetadataChanges ||
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
                runCatching { tagRepository.createTag(editor.field.category(), editor.text) }
                    .getOrElse {
                        mutableUiState.update {
                            it.copy(
                                tagInlineEditor =
                                    editor.copy(error = TaskTagInlineEditorError.DATA_UNAVAILABLE),
                            )
                        }
                        return@launch
                    }
            when (result) {
                is TagMutationResult.Created -> selectInlineCreatedTag(editor.field, result.tag)
                is TagMutationResult.DuplicateNormalizedText -> {
                    val existing =
                        runCatching { tagRepository.readTag(result.conflictingTagId) }.getOrNull()
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
        val state = mutableUiState.value
        if (state.isRunning || state.isSavingMetadata) return
        mutableUiState.update {
            it
                .withSelections(field, it.selectionsFor(field).filterNot { selection -> selection.id == selectionId })
                .withProjectedTagErrors()
                .copy(hasUnsavedMetadataChanges = true, message = null)
        }
    }

    private fun useUpdatedTagVersion(
        field: TaskTagField,
        selectionId: String,
    ) {
        val state = mutableUiState.value
        if (state.isRunning || state.isSavingMetadata) return
        mutableUiState.update { current ->
            val replacement =
                current.selectionsFor(field).map { selection ->
                    if (selection.id == selectionId) {
                        selection.updatedCatalogText(current.catalogFor(field))
                            ?.let { latest -> selection.copy(text = latest) }
                            ?: selection
                    } else {
                        selection
                    }
                }
            current
                .withSelections(field, replacement)
                .withProjectedTagErrors()
                .copy(hasUnsavedMetadataChanges = true, message = null)
        }
    }

    private fun openAddInterval() {
        val state = mutableUiState.value
        val date = state.workDate ?: return
        if (state.isRunning) {
            mutableUiState.update { it.copy(message = EditTaskMessage.RUNNING_TASK) }
            return
        }
        if (state.interval != null) {
            mutableUiState.update {
                it.copy(message = EditTaskMessage.TASK_ALREADY_HAS_INTERVAL)
            }
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
                ManualIntervalOperationResult.TaskAlreadyHasInterval ->
                    showIntervalFailure(EditTaskMessage.TASK_ALREADY_HAS_INTERVAL)
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
                            ManualIntervalOperationResult.TaskAlreadyHasInterval ->
                                EditTaskMessage.TASK_ALREADY_HAS_INTERVAL
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
        private val tagRepository: TagRepository,
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
                tagRepository = tagRepository,
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
    val descriptionTags: List<Tag> = emptyList(),
    val purchaseTags: List<Tag> = emptyList(),
)

private fun List<TaskTagSnapshot>.toSelections(
    field: TaskTagField,
): List<TaskTagSelectionUi> =
    filter { snapshot -> snapshot.category == field.category() }
        .sortedBy(TaskTagSnapshot::selectionOrder)
        .map { snapshot ->
            TaskTagSelectionUi(
                id = "snapshot:${snapshot.id}",
                text = snapshot.text,
                sourceTagId = snapshot.sourceTagId,
            )
        }

private fun EditTaskUiState.selectionsFor(field: TaskTagField): List<TaskTagSelectionUi> =
    when (field) {
        TaskTagField.DESCRIPTION -> descriptionTagSelections
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> purchaseTagSelections
    }

private fun EditTaskUiState.catalogFor(field: TaskTagField): List<TaskTagCatalogItemUi> =
    when (field) {
        TaskTagField.DESCRIPTION -> descriptionCatalogTags
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> purchaseCatalogTags
    }

private fun EditTaskUiState.withSelections(
    field: TaskTagField,
    selections: List<TaskTagSelectionUi>,
): EditTaskUiState =
    when (field) {
        TaskTagField.DESCRIPTION -> copy(descriptionTagSelections = selections)
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> copy(purchaseTagSelections = selections)
    }

private fun EditTaskUiState.withCatalog(
    field: TaskTagField,
    catalog: List<TaskTagCatalogItemUi>,
): EditTaskUiState =
    when (field) {
        TaskTagField.DESCRIPTION -> copy(descriptionCatalogTags = catalog)
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> copy(purchaseCatalogTags = catalog)
    }

private fun EditTaskUiState.withProjectedTagErrors(): EditTaskUiState {
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

private fun EditTaskUiState.hasProjectedTextLimitError(field: TaskTagField): Boolean =
    when (field) {
        TaskTagField.DESCRIPTION ->
            worq.order.data.TaskMetadataValidationError.DESCRIPTION_TOO_LONG in metadataErrors
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES ->
            worq.order.data.TaskMetadataValidationError.PURCHASES_TOO_LONG in metadataErrors
    }

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
        startText = ClockTimeFormatter.format(start, zoneId),
        stopText = stop?.let { ClockTimeFormatter.format(it, zoneId) }.orEmpty(),
        isRunning = stop == null,
    )
}

private fun List<OffsetChoiceUi>.choiceFor(offset: ZoneOffset):
    OverlapOffsetChoice? =
    firstOrNull { it.offsetLabel == offset.id }?.choice
