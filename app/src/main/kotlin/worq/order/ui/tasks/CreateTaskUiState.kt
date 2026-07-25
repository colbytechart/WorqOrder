package worq.order.ui.tasks

import java.time.LocalDate
import worq.order.data.TaskMetadataValidationError
import worq.order.ui.clients.ArchivedClientRestoreOffer
import worq.order.ui.clients.ClientEditorUiState
import worq.order.ui.clients.ClientItemUi

enum class CreateTaskMessage {
    DATA_UNAVAILABLE,
    CLIENT_NOT_FOUND,
    RESTORE_NAME_CONFLICT,
    CLIENT_REQUIRED,
    CLIENT_ARCHIVED,
}

data class CreateTaskUiState(
    val workDate: LocalDate = LocalDate.ofEpochDay(0),
    val isLoadingClients: Boolean = true,
    val hasClientLoadError: Boolean = false,
    val activeClients: List<ClientItemUi> = emptyList(),
    val selectedClientId: String? = null,
    val description: String = "",
    val hardwareSoftwarePurchases: String = "",
    val metadataErrors: Set<TaskMetadataValidationError> = emptySet(),
    val isSavingTask: Boolean = false,
    val hasUnsavedTaskChanges: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val isClientMenuExpanded: Boolean = false,
    val addClientEditor: ClientEditorUiState? = null,
    val restoreOffer: ArchivedClientRestoreOffer? = null,
    val message: CreateTaskMessage? = null,
) {
    val selectedClient: ClientItemUi?
        get() = activeClients.firstOrNull { it.id == selectedClientId }
}

sealed interface CreateTaskEvent {
    data object RetryClients : CreateTaskEvent

    data object OpenClientMenu : CreateTaskEvent

    data object DismissClientMenu : CreateTaskEvent

    data class SelectClient(
        val clientId: String,
    ) : CreateTaskEvent

    data class EditDescription(
        val description: String,
    ) : CreateTaskEvent

    data class EditHardwareSoftwarePurchases(
        val value: String,
    ) : CreateTaskEvent

    data object CreateTask : CreateTaskEvent

    data object RequestClose : CreateTaskEvent

    data object ConfirmDiscard : CreateTaskEvent

    data object DismissDiscard : CreateTaskEvent

    data object OpenAddClient : CreateTaskEvent

    data class EditNewClientName(
        val name: String,
    ) : CreateTaskEvent

    data object ConfirmAddClient : CreateTaskEvent

    data object DismissAddClient : CreateTaskEvent

    data object ConfirmRestoreOffer : CreateTaskEvent

    data object DismissRestoreOffer : CreateTaskEvent

    data object DismissMessage : CreateTaskEvent
}

sealed interface CreateTaskEffect {
    data object NavigateBack : CreateTaskEffect
}
