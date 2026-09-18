package worq.order.ui.tasks

import java.time.LocalDate
import worq.order.data.TaskMetadataValidationError
import worq.order.ui.clients.ArchivedClientRestoreOffer
import worq.order.ui.clients.ClientEditorUiState
import worq.order.ui.clients.ClientItemUi
import worq.order.model.WorkType
import worq.order.model.BillingStatus

enum class CreateTaskMessage {
    DATA_UNAVAILABLE,
    CLIENT_NOT_FOUND,
    RESTORE_NAME_CONFLICT,
    CLIENT_REQUIRED,
    CLIENT_ARCHIVED,
    CONSULTANT_REQUIRED,
    CONSULTANT_ARCHIVED,
    TAG_DATA_UNAVAILABLE,
}

data class CreateTaskUiState(
    val workDate: LocalDate = LocalDate.ofEpochDay(0),
    val isLoadingClients: Boolean = true,
    val hasClientLoadError: Boolean = false,
    val activeClients: List<ClientItemUi> = emptyList(),
    val selectedClientId: String? = null,
    val isLoadingConsultant: Boolean = true,
    val hasConsultantLoadError: Boolean = false,
    val selectedConsultantId: String? = null,
    val selectedConsultantName: String? = null,
    val description: String = "",
    val hardwareSoftwarePurchases: String = "",
    val descriptionTagSelections: List<TaskTagSelectionUi> = emptyList(),
    val purchaseTagSelections: List<TaskTagSelectionUi> = emptyList(),
    val descriptionCatalogTags: List<TaskTagCatalogItemUi> = emptyList(),
    val purchaseCatalogTags: List<TaskTagCatalogItemUi> = emptyList(),
    val tagPicker: TaskTagPickerUiState? = null,
    val tagInlineEditor: TaskTagInlineEditorUiState? = null,
    val workType: WorkType = WorkType.ON_SITE,
    val billingStatus: BillingStatus = BillingStatus.BILLABLE,
    val mileage: String = "",
    val notes: String = "",
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

    data class OpenTagPicker(
        val field: TaskTagField,
    ) : CreateTaskEvent

    data object DismissTagPicker : CreateTaskEvent

    data class EditTagSearch(
        val query: String,
    ) : CreateTaskEvent

    data object ClearTagSearch : CreateTaskEvent

    data class ToggleTagPickerItem(
        val itemId: String,
    ) : CreateTaskEvent

    data object SelectAllVisibleTagPickerItems : CreateTaskEvent

    data object DeselectAllVisibleTagPickerItems : CreateTaskEvent

    data object ApplyTagPicker : CreateTaskEvent

    data object OpenInlineTagCreate : CreateTaskEvent

    data class EditInlineTagText(
        val value: String,
    ) : CreateTaskEvent

    data object ConfirmInlineTagCreate : CreateTaskEvent

    data object DismissInlineTagCreate : CreateTaskEvent

    data class RemoveAppliedTag(
        val field: TaskTagField,
        val selectionId: String,
    ) : CreateTaskEvent

    data class UseUpdatedTagVersion(
        val field: TaskTagField,
        val selectionId: String,
    ) : CreateTaskEvent

    data class SelectWorkType(
        val workType: WorkType,
    ) : CreateTaskEvent

    data class SelectBillingStatus(
        val billingStatus: BillingStatus,
    ) : CreateTaskEvent

    data class EditMileage(
        val value: String,
    ) : CreateTaskEvent

    data class EditNotes(
        val value: String,
    ) : CreateTaskEvent

    data object CreateTask : CreateTaskEvent

    data object RequestClose : CreateTaskEvent

    data object ConfirmDiscard : CreateTaskEvent

    data object DismissDiscard : CreateTaskEvent

    data object OpenAddClient : CreateTaskEvent

    data object OpenConsultantSettings : CreateTaskEvent

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

    data object NavigateToSettings : CreateTaskEffect
}
