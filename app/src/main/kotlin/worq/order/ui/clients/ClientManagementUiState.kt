package worq.order.ui.clients

data class ClientItemUi(
    val id: String,
    val name: String,
)

data class ArchiveClientConfirmation(
    val clientId: String,
    val clientName: String,
    val isArchiving: Boolean = false,
)

enum class ClientManagementMessage {
    DATA_UNAVAILABLE,
    CLIENT_NOT_FOUND,
    RESTORE_NAME_CONFLICT,
}

data class ClientManagementUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val activeClients: List<ClientItemUi> = emptyList(),
    val archivedClients: List<ClientItemUi> = emptyList(),
    val editor: ClientEditorUiState? = null,
    val archiveConfirmation: ArchiveClientConfirmation? = null,
    val restoreOffer: ArchivedClientRestoreOffer? = null,
    val pendingClientId: String? = null,
    val message: ClientManagementMessage? = null,
)

sealed interface ClientManagementEvent {
    data object Retry : ClientManagementEvent

    data object OpenAddClient : ClientManagementEvent

    data class OpenRenameClient(
        val clientId: String,
    ) : ClientManagementEvent

    data class EditName(
        val name: String,
    ) : ClientManagementEvent

    data object ConfirmEditor : ClientManagementEvent

    data object DismissEditor : ClientManagementEvent

    data class RequestArchive(
        val clientId: String,
    ) : ClientManagementEvent

    data object ConfirmArchive : ClientManagementEvent

    data object DismissArchive : ClientManagementEvent

    data class RestoreClient(
        val clientId: String,
    ) : ClientManagementEvent

    data object ConfirmRestoreOffer : ClientManagementEvent

    data object DismissRestoreOffer : ClientManagementEvent

    data object DismissMessage : ClientManagementEvent
}
