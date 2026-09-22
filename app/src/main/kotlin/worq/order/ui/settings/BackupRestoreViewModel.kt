package worq.order.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.backup.PortableBackupCreationCoordinator
import worq.order.backup.PortableBackupCreationResult
import worq.order.backup.PortableBackupImportDocumentStager
import worq.order.backup.PortableBackupImportStageResult
import worq.order.backup.PortableBackupReplacementCoordinator
import worq.order.backup.PortableBackupReplacementResult
import worq.order.backup.PortableBackupStagedImport
import worq.order.data.ActiveTimerRepository
import worq.order.data.BackupRestoreStatus
import worq.order.data.BackupRestoreStatusRepository

sealed interface BackupRestoreEvent {
    data object CreateBackup : BackupRestoreEvent

    data class BackupDocumentSelected(
        val documentUri: String?,
    ) : BackupRestoreEvent

    data object ImportBackup : BackupRestoreEvent

    data class ImportDocumentSelected(
        val documentUri: String?,
    ) : BackupRestoreEvent

    data object ConfirmImport : BackupRestoreEvent

    data object DismissImportConfirmation : BackupRestoreEvent

    data object RestorePreviousState : BackupRestoreEvent

    data object ConfirmRestore : BackupRestoreEvent

    data object DismissRestoreConfirmation : BackupRestoreEvent

    data object DismissStatus : BackupRestoreEvent
}

sealed interface BackupRestoreEffect {
    data class LaunchCreateBackup(
        val suggestedFileName: String,
    ) : BackupRestoreEffect

    data object LaunchImportBackup : BackupRestoreEffect
}

private data class BackupRestoreEditorState(
    val operation: BackupRestoreOperation = BackupRestoreOperation.IDLE,
    val hasRestorePoint: Boolean = false,
    val statusOverride: BackupRestoreStatus? = null,
)

/**
 * Owns the Settings workflow only. Portable data creation and replacement remain inside their
 * dedicated coordinators; this ViewModel never parses or mutates authoritative data itself.
 */
class BackupRestoreViewModel internal constructor(
    private val activeTimerRepository: ActiveTimerRepository,
    private val statusRepository: BackupRestoreStatusRepository,
    private val workflow: BackupRestoreWorkflow,
) : ViewModel() {
    private val editorState = MutableStateFlow(BackupRestoreEditorState())
    private var pendingImport: PortableBackupStagedImport? = null
    private val effectChannel = Channel<BackupRestoreEffect>(Channel.BUFFERED)

    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<BackupRestoreUiState> =
        combine(
            activeTimerRepository.observeActiveTimer(),
            statusRepository.observeStatus(),
            editorState,
        ) { activeTimer, persistedStatus, editor ->
            BackupRestoreUiState(
                isInitializing = false,
                isTimerRunning = activeTimer != null,
                operation = editor.operation,
                hasRestorePoint = editor.hasRestorePoint,
                status =
                    (editor.statusOverride ?: persistedStatus)
                        ?.let(::BackupRestoreStatusUi),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BackupRestoreUiState(isInitializing = true),
        )

    init {
        refreshRestorePointAvailability()
    }

    fun onEvent(event: BackupRestoreEvent) {
        when (event) {
            BackupRestoreEvent.CreateBackup -> requestCreateBackup()
            is BackupRestoreEvent.BackupDocumentSelected -> createBackup(event.documentUri)
            BackupRestoreEvent.ImportBackup -> requestImportBackup()
            is BackupRestoreEvent.ImportDocumentSelected -> stageImport(event.documentUri)
            BackupRestoreEvent.ConfirmImport -> importStagedBackup()
            BackupRestoreEvent.DismissImportConfirmation -> discardStagedImport()
            BackupRestoreEvent.RestorePreviousState -> requestRestore()
            BackupRestoreEvent.ConfirmRestore -> restorePreviousState()
            BackupRestoreEvent.DismissRestoreConfirmation -> dismissRestore()
            BackupRestoreEvent.DismissStatus -> clearStatus()
        }
    }

    private fun requestCreateBackup() {
        if (uiState.value.isTimerRunning) {
            reportTimerBlocked(BackupRestoreStatus.BACKUP_TIMER_RUNNING)
            return
        }
        if (editorState.value.operation != BackupRestoreOperation.IDLE) return
        editorState.update {
            it.copy(operation = BackupRestoreOperation.CHOOSING_BACKUP_DESTINATION)
        }
        if (
            !effectChannel
                .trySend(
                    BackupRestoreEffect.LaunchCreateBackup(
                        workflow.suggestedFileName(),
                    ),
                ).isSuccess
        ) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            setStatus(BackupRestoreStatus.BACKUP_OUTPUT_FAILED)
        }
    }

    private fun createBackup(documentUri: String?) {
        if (editorState.value.operation != BackupRestoreOperation.CHOOSING_BACKUP_DESTINATION) return
        if (uiState.value.isTimerRunning) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            setStatus(BackupRestoreStatus.BACKUP_TIMER_RUNNING)
            return
        }
        if (documentUri == null) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            return
        }
        launchOperation(
            operation = BackupRestoreOperation.CREATING_BACKUP,
            expectedOperation = BackupRestoreOperation.CHOOSING_BACKUP_DESTINATION,
        ) {
            when (val result = workflow.create(documentUri)) {
                is PortableBackupCreationResult.Success ->
                    setStatus(BackupRestoreStatus.BACKUP_CREATED)
                PortableBackupCreationResult.TimerRunning ->
                    setStatus(BackupRestoreStatus.BACKUP_TIMER_RUNNING)
                PortableBackupCreationResult.InvalidLocalState ->
                    setStatus(BackupRestoreStatus.BACKUP_INVALID_LOCAL_STATE)
                is PortableBackupCreationResult.OutputFailed -> {
                    setStatus(
                        if (result.partialDocumentMayRemain) {
                            BackupRestoreStatus.BACKUP_OUTPUT_PARTIAL
                        } else {
                            BackupRestoreStatus.BACKUP_OUTPUT_FAILED
                        },
                    )
                }
            }
        }
    }

    private fun requestImportBackup() {
        if (uiState.value.isTimerRunning) {
            reportTimerBlocked(BackupRestoreStatus.IMPORT_TIMER_RUNNING)
            return
        }
        if (editorState.value.operation != BackupRestoreOperation.IDLE) return
        editorState.update {
            it.copy(operation = BackupRestoreOperation.CHOOSING_IMPORT_SOURCE)
        }
        if (!effectChannel.trySend(BackupRestoreEffect.LaunchImportBackup).isSuccess) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            setStatus(BackupRestoreStatus.IMPORT_STORAGE_FAILURE)
        }
    }

    private fun stageImport(documentUri: String?) {
        if (editorState.value.operation != BackupRestoreOperation.CHOOSING_IMPORT_SOURCE) return
        if (uiState.value.isTimerRunning) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            setStatus(BackupRestoreStatus.IMPORT_TIMER_RUNNING)
            return
        }
        if (documentUri == null) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
            return
        }
        launchOperation(
            operation = BackupRestoreOperation.STAGING_IMPORT,
            expectedOperation = BackupRestoreOperation.CHOOSING_IMPORT_SOURCE,
            resetToIdle = false,
        ) {
            when (val result = workflow.stageImport(documentUri)) {
                is PortableBackupImportStageResult.Ready -> {
                    pendingImport = result.stagedImport
                    editorState.update {
                        it.copy(operation = BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION)
                    }
                }
                PortableBackupImportStageResult.InvalidArchive -> {
                    editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
                    setStatus(BackupRestoreStatus.IMPORT_INVALID_ARCHIVE)
                }
                PortableBackupImportStageResult.StorageFailure -> {
                    editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
                    setStatus(BackupRestoreStatus.IMPORT_STORAGE_FAILURE)
                }
            }
        }
    }

    private fun importStagedBackup() {
        val stagedImport = pendingImport
        if (
            stagedImport == null ||
            editorState.value.operation != BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION
        ) {
            return
        }
        launchOperation(
            operation = BackupRestoreOperation.IMPORTING,
            expectedOperation = BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION,
        ) {
            val result = workflow.import(stagedImport)
            when (result) {
                PortableBackupReplacementResult.Replaced ->
                    setStatus(BackupRestoreStatus.IMPORT_SUCCEEDED)
                PortableBackupReplacementResult.TimerRunning ->
                    setStatus(BackupRestoreStatus.IMPORT_TIMER_RUNNING)
                PortableBackupReplacementResult.InvalidSource ->
                    setStatus(BackupRestoreStatus.IMPORT_INVALID_ARCHIVE)
                PortableBackupReplacementResult.StorageFailure ->
                    setStatus(BackupRestoreStatus.IMPORT_STORAGE_FAILURE)
                PortableBackupReplacementResult.RecoveryRequired ->
                    setStatus(BackupRestoreStatus.RECOVERY_REQUIRED)
                PortableBackupReplacementResult.RolledBack,
                PortableBackupReplacementResult.Restored,
                PortableBackupReplacementResult.NoRestorePoint,
                -> setStatus(BackupRestoreStatus.IMPORT_FAILED)
            }
            if (
                result == PortableBackupReplacementResult.TimerRunning ||
                result == PortableBackupReplacementResult.InvalidSource ||
                result == PortableBackupReplacementResult.StorageFailure
            ) {
                workflow.discardStagedImport(stagedImport)
            }
            pendingImport = null
            refreshRestorePointAvailability()
        }
    }

    private fun discardStagedImport() {
        val stagedImport = pendingImport
        pendingImport = null
        editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
        if (stagedImport != null) {
            viewModelScope.launch {
                runCatching { workflow.discardStagedImport(stagedImport) }
            }
        }
    }

    private fun requestRestore() {
        when {
            uiState.value.isTimerRunning -> setStatus(BackupRestoreStatus.RESTORE_TIMER_RUNNING)
            !uiState.value.canRestore -> setStatus(BackupRestoreStatus.RESTORE_UNAVAILABLE)
            else ->
                editorState.update {
                    it.copy(operation = BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION)
                }
        }
    }

    private fun restorePreviousState() {
        if (editorState.value.operation != BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION) return
        launchOperation(
            operation = BackupRestoreOperation.RESTORING,
            expectedOperation = BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION,
        ) {
            when (workflow.restore()) {
                PortableBackupReplacementResult.Restored ->
                    setStatus(BackupRestoreStatus.RESTORE_SUCCEEDED)
                PortableBackupReplacementResult.TimerRunning ->
                    setStatus(BackupRestoreStatus.RESTORE_TIMER_RUNNING)
                PortableBackupReplacementResult.NoRestorePoint,
                PortableBackupReplacementResult.InvalidSource,
                -> setStatus(BackupRestoreStatus.RESTORE_UNAVAILABLE)
                PortableBackupReplacementResult.RecoveryRequired ->
                    setStatus(BackupRestoreStatus.RECOVERY_REQUIRED)
                PortableBackupReplacementResult.StorageFailure,
                PortableBackupReplacementResult.RolledBack,
                PortableBackupReplacementResult.Replaced,
                -> setStatus(BackupRestoreStatus.RESTORE_FAILED)
            }
            refreshRestorePointAvailability()
        }
    }

    private fun dismissRestore() {
        if (editorState.value.operation == BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION) {
            editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
        }
    }

    private fun reportTimerBlocked(status: BackupRestoreStatus) {
        if (uiState.value.isTimerRunning) {
            setStatus(status)
        }
    }

    private fun clearStatus() {
        editorState.update { it.copy(statusOverride = null) }
        viewModelScope.launch {
            runCatching { statusRepository.clearStatus() }
        }
    }

    private fun setStatus(status: BackupRestoreStatus) {
        editorState.update { it.copy(statusOverride = status) }
        viewModelScope.launch {
            runCatching { statusRepository.setStatus(status) }
        }
    }

    private fun refreshRestorePointAvailability() {
        viewModelScope.launch {
            val hasRestorePoint = runCatching { workflow.hasRestorePoint() }.getOrDefault(false)
            editorState.update { it.copy(hasRestorePoint = hasRestorePoint) }
        }
    }

    private fun launchOperation(
        operation: BackupRestoreOperation,
        expectedOperation: BackupRestoreOperation = BackupRestoreOperation.IDLE,
        resetToIdle: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (editorState.value.operation != expectedOperation) return
        editorState.update { it.copy(operation = operation) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                when (operation) {
                    BackupRestoreOperation.CREATING_BACKUP ->
                        setStatus(BackupRestoreStatus.BACKUP_OUTPUT_FAILED)
                    BackupRestoreOperation.STAGING_IMPORT,
                    BackupRestoreOperation.IMPORTING,
                    -> setStatus(BackupRestoreStatus.IMPORT_FAILED)
                    BackupRestoreOperation.RESTORING ->
                        setStatus(BackupRestoreStatus.RESTORE_FAILED)
                    else -> setStatus(BackupRestoreStatus.RECOVERY_REQUIRED)
                }
            } finally {
                if (resetToIdle || editorState.value.operation == operation) {
                    editorState.update { it.copy(operation = BackupRestoreOperation.IDLE) }
                }
            }
        }
    }

    class Factory(
        private val activeTimerRepository: ActiveTimerRepository,
        private val statusRepository: BackupRestoreStatusRepository,
        private val creationCoordinator: PortableBackupCreationCoordinator,
        private val importDocumentStager: PortableBackupImportDocumentStager,
        private val replacementCoordinator: PortableBackupReplacementCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BackupRestoreViewModel::class.java))
            return BackupRestoreViewModel(
                activeTimerRepository = activeTimerRepository,
                statusRepository = statusRepository,
                workflow =
                    CoordinatorBackupRestoreWorkflow(
                        creationCoordinator = creationCoordinator,
                        importDocumentStager = importDocumentStager,
                        replacementCoordinator = replacementCoordinator,
                    ),
            ) as T
        }
    }
}
