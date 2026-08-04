package worq.order.ui.tasks

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import worq.order.data.TaskMetadataValidationError
import worq.order.domain.ManualIntervalValidationError
import worq.order.domain.OverlapOffsetChoice
import worq.order.ui.clients.ClientItemUi
import worq.order.model.WorkType
import worq.order.ui.employees.ConsultantItemUi

enum class IntervalEndpoint {
    START,
    STOP,
}

data class OffsetChoiceUi(
    val choice: OverlapOffsetChoice,
    val offsetLabel: String,
)

data class IntervalItemUi(
    val id: String,
    val ordinal: Int,
    val startText: String,
    val stopText: String,
    val durationText: String,
    val isRunning: Boolean,
)

data class IntervalEditorUiState(
    val intervalId: String? = null,
    val startLocal: LocalDateTime,
    val stopLocal: LocalDateTime,
    val startOverlapChoice: OverlapOffsetChoice? = null,
    val stopOverlapChoice: OverlapOffsetChoice? = null,
    val startOffsetChoices: List<OffsetChoiceUi> = emptyList(),
    val stopOffsetChoices: List<OffsetChoiceUi> = emptyList(),
    val timePickerEndpoint: IntervalEndpoint? = null,
    val validationErrors: Set<ManualIntervalValidationError> = emptySet(),
    val isSaving: Boolean = false,
)

enum class EditTaskMessage {
    DATA_UNAVAILABLE,
    TASK_NOT_FOUND,
    CLIENT_UNAVAILABLE,
    CONSULTANT_UNAVAILABLE,
    RUNNING_TASK,
    INTERVAL_NOT_FOUND,
    RUNNING_INTERVAL,
    INTERVAL_CHANGED,
}

data class EditTaskUiState(
    val taskId: String,
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val taskMissing: Boolean = false,
    val workDate: LocalDate? = null,
    val zoneId: ZoneId? = null,
    val originalClientName: String = "",
    val activeClients: List<ClientItemUi> = emptyList(),
    val selectedClientId: String? = null,
    val isClientMenuExpanded: Boolean = false,
    val originalConsultantName: String = "",
    val activeConsultants: List<ConsultantItemUi> = emptyList(),
    val selectedConsultantId: String? = null,
    val isConsultantMenuExpanded: Boolean = false,
    val description: String = "",
    val hardwareSoftwarePurchases: String = "",
    val workType: WorkType = WorkType.UNSPECIFIED,
    val mileage: String = "",
    val metadataErrors: Set<TaskMetadataValidationError> = emptySet(),
    val totalDuration: String = "00:00:00",
    val billingMinutes: Long = 0,
    val intervals: List<IntervalItemUi> = emptyList(),
    val isRunning: Boolean = false,
    val isSavingMetadata: Boolean = false,
    val hasUnsavedMetadataChanges: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val showTaskDeleteConfirmation: Boolean = false,
    val intervalPendingDeletion: IntervalItemUi? = null,
    val intervalEditor: IntervalEditorUiState? = null,
    val message: EditTaskMessage? = null,
) {
    val selectedClientName: String?
        get() =
            activeClients
                .firstOrNull { it.id == selectedClientId }
                ?.name
                ?: originalClientName.takeIf { it.isNotBlank() }

    val selectedConsultantName: String?
        get() =
            activeConsultants
                .firstOrNull { it.id == selectedConsultantId }
                ?.name
                ?: originalConsultantName.takeIf { it.isNotBlank() }
}

sealed interface EditTaskEvent {
    data object Retry : EditTaskEvent

    data object OpenClientMenu : EditTaskEvent

    data object DismissClientMenu : EditTaskEvent

    data class SelectClient(
        val clientId: String,
    ) : EditTaskEvent

    data object OpenConsultantMenu : EditTaskEvent

    data object DismissConsultantMenu : EditTaskEvent

    data class SelectConsultant(
        val consultantId: String,
    ) : EditTaskEvent

    data class EditDescription(
        val value: String,
    ) : EditTaskEvent

    data class EditHardwareSoftwarePurchases(
        val value: String,
    ) : EditTaskEvent

    data class SelectWorkType(
        val workType: WorkType,
    ) : EditTaskEvent

    data class EditMileage(
        val value: String,
    ) : EditTaskEvent

    data object SaveMetadata : EditTaskEvent

    data object RequestClose : EditTaskEvent

    data object ConfirmDiscard : EditTaskEvent

    data object DismissDiscard : EditTaskEvent

    data object OpenAddInterval : EditTaskEvent

    data class OpenEditInterval(
        val intervalId: String,
    ) : EditTaskEvent

    data object DismissIntervalEditor : EditTaskEvent

    data class OpenTimePicker(
        val endpoint: IntervalEndpoint,
    ) : EditTaskEvent

    data class SetEditorTime(
        val endpoint: IntervalEndpoint,
        val hour: Int,
        val minute: Int,
    ) : EditTaskEvent

    data class SelectOffset(
        val endpoint: IntervalEndpoint,
        val choice: OverlapOffsetChoice,
    ) : EditTaskEvent

    data object DismissTimePicker : EditTaskEvent

    data object SaveInterval : EditTaskEvent

    data class RequestDeleteInterval(
        val intervalId: String,
    ) : EditTaskEvent

    data object ConfirmDeleteInterval : EditTaskEvent

    data object DismissDeleteInterval : EditTaskEvent

    data object RequestDeleteTask : EditTaskEvent

    data object ConfirmDeleteTask : EditTaskEvent

    data object DismissDeleteTask : EditTaskEvent

    data object DismissMessage : EditTaskEvent
}

sealed interface EditTaskEffect {
    data object NavigateBack : EditTaskEffect
}
