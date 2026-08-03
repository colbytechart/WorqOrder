package worq.order.ui.employees

import worq.order.data.EmployeeNameValidationError

data class ConsultantItemUi(
    val id: String,
    val name: String,
)

enum class ConsultantEditorMode {
    ADD,
    RENAME,
}

enum class ConsultantNameFieldError {
    BLANK,
    TOO_LONG,
    DUPLICATE_ACTIVE,
}

data class ConsultantEditorUiState(
    val mode: ConsultantEditorMode,
    val consultantId: String? = null,
    val name: String = "",
    val fieldError: ConsultantNameFieldError? = null,
    val isSaving: Boolean = false,
)

data class ArchiveConsultantConfirmation(
    val consultantId: String,
    val consultantName: String,
    val wasSelected: Boolean,
    val isArchiving: Boolean = false,
)

data class ArchivedConsultantRestoreOffer(
    val consultantId: String,
    val consultantName: String,
    val isRestoring: Boolean = false,
)

enum class ConsultantSettingsMessage {
    DATA_UNAVAILABLE,
    CONSULTANT_NOT_FOUND,
    CONSULTANT_ARCHIVED,
    RESTORE_NAME_CONFLICT,
}

data class ConsultantSettingsUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val activeConsultants: List<ConsultantItemUi> = emptyList(),
    val archivedConsultants: List<ConsultantItemUi> = emptyList(),
    val selectedConsultantId: String? = null,
    val isSelectionMenuExpanded: Boolean = false,
    val isSavingSelection: Boolean = false,
    val editor: ConsultantEditorUiState? = null,
    val archiveConfirmation: ArchiveConsultantConfirmation? = null,
    val restoreOffer: ArchivedConsultantRestoreOffer? = null,
    val pendingConsultantId: String? = null,
    val message: ConsultantSettingsMessage? = null,
) {
    val selectedConsultant: ConsultantItemUi?
        get() = activeConsultants.firstOrNull { it.id == selectedConsultantId }
}

sealed interface ConsultantSettingsEvent {
    data object Retry : ConsultantSettingsEvent

    data object OpenSelectionMenu : ConsultantSettingsEvent

    data object DismissSelectionMenu : ConsultantSettingsEvent

    data class SelectConsultant(
        val consultantId: String,
    ) : ConsultantSettingsEvent

    data object OpenAddConsultant : ConsultantSettingsEvent

    data class OpenRenameConsultant(
        val consultantId: String,
    ) : ConsultantSettingsEvent

    data class EditName(
        val name: String,
    ) : ConsultantSettingsEvent

    data object ConfirmEditor : ConsultantSettingsEvent

    data object DismissEditor : ConsultantSettingsEvent

    data class RequestArchive(
        val consultantId: String,
    ) : ConsultantSettingsEvent

    data object ConfirmArchive : ConsultantSettingsEvent

    data object DismissArchive : ConsultantSettingsEvent

    data class RestoreConsultant(
        val consultantId: String,
    ) : ConsultantSettingsEvent

    data object ConfirmRestoreOffer : ConsultantSettingsEvent

    data object DismissRestoreOffer : ConsultantSettingsEvent

    data object DismissMessage : ConsultantSettingsEvent
}

internal fun EmployeeNameValidationError.toFieldError(): ConsultantNameFieldError =
    when (this) {
        EmployeeNameValidationError.BLANK -> ConsultantNameFieldError.BLANK
        EmployeeNameValidationError.TOO_LONG -> ConsultantNameFieldError.TOO_LONG
    }
