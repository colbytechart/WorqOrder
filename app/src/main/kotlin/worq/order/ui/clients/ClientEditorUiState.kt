package worq.order.ui.clients

import worq.order.data.ClientNameValidationError

enum class ClientEditorMode {
    ADD,
    RENAME,
}

enum class ClientNameFieldError {
    BLANK,
    TOO_LONG,
    DUPLICATE_ACTIVE,
}

data class ClientEditorUiState(
    val mode: ClientEditorMode,
    val clientId: String? = null,
    val name: String = "",
    val fieldError: ClientNameFieldError? = null,
    val isSaving: Boolean = false,
)

data class ArchivedClientRestoreOffer(
    val clientId: String,
    val clientName: String,
    val isRestoring: Boolean = false,
)

internal fun ClientNameValidationError.toFieldError(): ClientNameFieldError =
    when (this) {
        ClientNameValidationError.BLANK -> ClientNameFieldError.BLANK
        ClientNameValidationError.TOO_LONG -> ClientNameFieldError.TOO_LONG
    }
