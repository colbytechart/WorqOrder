package worq.order.data

import kotlinx.coroutines.flow.Flow
import worq.order.model.Client

sealed interface ClientMutationResult {
    data class Success(
        val client: Client,
    ) : ClientMutationResult

    data class MatchingArchivedClient(
        val client: Client,
    ) : ClientMutationResult

    data class InvalidName(
        val reason: ClientNameValidationError,
    ) : ClientMutationResult

    data class DuplicateActiveName(
        val conflictingClientId: String?,
    ) : ClientMutationResult

    data object NotFound : ClientMutationResult
}

interface ClientRepository {
    fun observeActiveClients(): Flow<List<Client>>

    fun observeAllClients(): Flow<List<Client>>

    suspend fun addClient(name: String): ClientMutationResult

    suspend fun renameClient(
        clientId: String,
        name: String,
    ): ClientMutationResult

    suspend fun archiveClient(clientId: String): ClientMutationResult

    suspend fun restoreClient(clientId: String): ClientMutationResult
}
