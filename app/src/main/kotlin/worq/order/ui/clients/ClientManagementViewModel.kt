package worq.order.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.ClientMutationResult
import worq.order.data.ClientNameValidationResult
import worq.order.data.ClientNameNormalizer
import worq.order.data.ClientRepository
import worq.order.model.Client

class ClientManagementViewModel(
    private val clientRepository: ClientRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ClientManagementUiState())
    val uiState: StateFlow<ClientManagementUiState> = mutableUiState
    private var clientObservationJob: Job? = null

    init {
        observeClients()
    }

    fun onEvent(event: ClientManagementEvent) {
        when (event) {
            ClientManagementEvent.Retry -> observeClients()
            ClientManagementEvent.OpenAddClient -> openAddClient()
            is ClientManagementEvent.OpenRenameClient -> openRenameClient(event.clientId)
            is ClientManagementEvent.EditName ->
                mutableUiState.update { state ->
                    state.copy(
                        editor =
                            state.editor?.copy(
                                name = event.name,
                                fieldError = null,
                            ),
                    )
                }
            ClientManagementEvent.ConfirmEditor -> confirmEditor()
            ClientManagementEvent.DismissEditor ->
                mutableUiState.update { it.copy(editor = null) }
            is ClientManagementEvent.RequestArchive -> requestArchive(event.clientId)
            ClientManagementEvent.ConfirmArchive -> confirmArchive()
            ClientManagementEvent.DismissArchive ->
                mutableUiState.update { it.copy(archiveConfirmation = null) }
            is ClientManagementEvent.RestoreClient -> restoreClient(event.clientId)
            ClientManagementEvent.ConfirmRestoreOffer -> confirmRestoreOffer()
            ClientManagementEvent.DismissRestoreOffer ->
                mutableUiState.update { it.copy(restoreOffer = null) }
            ClientManagementEvent.DismissMessage ->
                mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeClients() {
        clientObservationJob?.cancel()
        clientObservationJob =
            clientRepository
                .observeAllClients()
                .onStart {
                    mutableUiState.update {
                        it.copy(
                            isLoading = true,
                            hasLoadError = false,
                        )
                    }
                }.onEach { clients ->
                    mutableUiState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasLoadError = false,
                            activeClients =
                                clients
                                    .filter(Client::isActive)
                                    .map(Client::toUi),
                            archivedClients =
                                clients
                                    .filterNot(Client::isActive)
                                    .map(Client::toUi),
                        )
                    }
                }.catch {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadError = true,
                        )
                    }
                }.launchIn(viewModelScope)
    }

    private fun openAddClient() {
        if (mutableUiState.value.pendingClientId != null) {
            return
        }
        mutableUiState.update {
            it.copy(
                editor = ClientEditorUiState(mode = ClientEditorMode.ADD),
                message = null,
            )
        }
    }

    private fun openRenameClient(clientId: String) {
        if (mutableUiState.value.pendingClientId != null) {
            return
        }
        val client =
            mutableUiState.value.activeClients.firstOrNull { it.id == clientId }
                ?: return
        mutableUiState.update {
            it.copy(
                editor =
                    ClientEditorUiState(
                        mode = ClientEditorMode.RENAME,
                        clientId = client.id,
                        name = client.name,
                    ),
                message = null,
            )
        }
    }

    private fun confirmEditor() {
        val editor = mutableUiState.value.editor ?: return
        if (editor.isSaving) {
            return
        }
        when (val validation = ClientNameNormalizer.validate(editor.name)) {
            is ClientNameValidationResult.Invalid -> {
                mutableUiState.update {
                    it.copy(
                        editor =
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
                it.copy(editor = editor.copy(isSaving = true))
            }
            val result =
                runCatching {
                    when (editor.mode) {
                        ClientEditorMode.ADD ->
                            clientRepository.addClient(editor.name)
                        ClientEditorMode.RENAME ->
                            clientRepository.renameClient(
                                clientId = requireNotNull(editor.clientId),
                                name = editor.name,
                            )
                    }
                }.getOrElse {
                    mutableUiState.update {
                        it.copy(
                            editor = editor.copy(isSaving = false),
                            message = ClientManagementMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            handleEditorResult(editor, result)
        }
    }

    private fun handleEditorResult(
        editor: ClientEditorUiState,
        result: ClientMutationResult,
    ) {
        when (result) {
            is ClientMutationResult.Success ->
                mutableUiState.update {
                    it.copy(
                        editor = null,
                        message = null,
                    )
                }
            is ClientMutationResult.MatchingArchivedClient ->
                mutableUiState.update {
                    it.copy(
                        editor = null,
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
                        editor =
                            editor.copy(
                                fieldError = result.reason.toFieldError(),
                                isSaving = false,
                            ),
                    )
                }
            is ClientMutationResult.DuplicateActiveName ->
                mutableUiState.update {
                    it.copy(
                        editor =
                            editor.copy(
                                fieldError = ClientNameFieldError.DUPLICATE_ACTIVE,
                                isSaving = false,
                            ),
                    )
                }
            ClientMutationResult.NotFound ->
                mutableUiState.update {
                    it.copy(
                        editor = null,
                        message = ClientManagementMessage.CLIENT_NOT_FOUND,
                    )
                }
        }
    }

    private fun requestArchive(clientId: String) {
        if (mutableUiState.value.pendingClientId != null) {
            return
        }
        val client =
            mutableUiState.value.activeClients.firstOrNull { it.id == clientId }
                ?: return
        mutableUiState.update {
            it.copy(
                archiveConfirmation =
                    ArchiveClientConfirmation(
                        clientId = client.id,
                        clientName = client.name,
                    ),
                message = null,
            )
        }
    }

    private fun confirmArchive() {
        val confirmation = mutableUiState.value.archiveConfirmation ?: return
        if (confirmation.isArchiving) {
            return
        }
        mutableUiState.update {
            it.copy(
                archiveConfirmation = confirmation.copy(isArchiving = true),
            )
        }
        mutateClient(confirmation.clientId) {
            clientRepository.archiveClient(confirmation.clientId)
        }
    }

    private fun restoreClient(clientId: String) {
        if (mutableUiState.value.pendingClientId != null) {
            return
        }
        mutateClient(clientId) {
            clientRepository.restoreClient(clientId)
        }
    }

    private fun confirmRestoreOffer() {
        val offer = mutableUiState.value.restoreOffer ?: return
        if (offer.isRestoring) {
            return
        }
        mutableUiState.update {
            it.copy(restoreOffer = offer.copy(isRestoring = true))
        }
        mutateClient(offer.clientId) {
            clientRepository.restoreClient(offer.clientId)
        }
    }

    private fun mutateClient(
        clientId: String,
        mutation: suspend () -> ClientMutationResult,
    ) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    pendingClientId = clientId,
                    message = null,
                )
            }
            val result =
                runCatching { mutation() }
                    .getOrElse {
                        mutableUiState.update { state ->
                            state.copy(
                                archiveConfirmation = null,
                                restoreOffer = null,
                                pendingClientId = null,
                                message = ClientManagementMessage.DATA_UNAVAILABLE,
                            )
                        }
                        return@launch
                    }
            mutableUiState.update { state ->
                when (result) {
                    is ClientMutationResult.Success ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingClientId = null,
                            message = null,
                        )
                    is ClientMutationResult.DuplicateActiveName ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingClientId = null,
                            message = ClientManagementMessage.RESTORE_NAME_CONFLICT,
                        )
                    ClientMutationResult.NotFound ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingClientId = null,
                            message = ClientManagementMessage.CLIENT_NOT_FOUND,
                        )
                    is ClientMutationResult.InvalidName,
                    is ClientMutationResult.MatchingArchivedClient,
                    ->
                        state.copy(
                            archiveConfirmation = null,
                            restoreOffer = null,
                            pendingClientId = null,
                            message = ClientManagementMessage.DATA_UNAVAILABLE,
                        )
                }
            }
        }
    }

    class Factory(
        private val clientRepository: ClientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ClientManagementViewModel::class.java))
            return ClientManagementViewModel(clientRepository) as T
        }
    }
}

private fun Client.toUi() =
    ClientItemUi(
        id = id,
        name = name,
    )
