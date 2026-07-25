package worq.order.ui.tasks

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
import worq.order.ui.clients.ArchivedClientRestoreOffer
import worq.order.ui.clients.ClientEditorMode
import worq.order.ui.clients.ClientEditorUiState
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.clients.ClientNameFieldError
import worq.order.ui.clients.toFieldError

class CreateTaskViewModel(
    private val clientRepository: ClientRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = mutableUiState
    private var clientObservationJob: Job? = null

    init {
        observeActiveClients()
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
            CreateTaskEvent.OpenAddClient ->
                mutableUiState.update {
                    it.copy(
                        isClientMenuExpanded = false,
                        addClientEditor =
                            ClientEditorUiState(mode = ClientEditorMode.ADD),
                        message = null,
                    )
                }
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

    private fun selectClient(clientId: String) {
        if (mutableUiState.value.activeClients.none { it.id == clientId }) {
            return
        }
        mutableUiState.update {
            it.copy(
                selectedClientId = clientId,
                isClientMenuExpanded = false,
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

    class Factory(
        private val clientRepository: ClientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateTaskViewModel::class.java))
            return CreateTaskViewModel(clientRepository) as T
        }
    }
}

private fun Client.toUi() =
    ClientItemUi(
        id = id,
        name = name,
    )
