package worq.order.testing

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import worq.order.data.ClientMutationResult
import worq.order.data.ClientNameValidationResult
import worq.order.data.ClientNameNormalizer
import worq.order.data.ClientRepository
import worq.order.model.Client

class FakeClientRepository(
    initialClients: List<Client> = emptyList(),
) : ClientRepository {
    private val clients = MutableStateFlow(initialClients)
    private var nextId = initialClients.size

    val currentClients: List<Client>
        get() = clients.value

    override fun observeActiveClients(): Flow<List<Client>> =
        clients.map { current ->
            current
                .filter(Client::isActive)
                .sortedWith(CLIENT_COMPARATOR)
        }

    override fun observeAllClients(): Flow<List<Client>> =
        clients.map { current ->
            current.sortedWith(
                compareByDescending<Client>(Client::isActive)
                    .then(CLIENT_COMPARATOR),
            )
        }

    override suspend fun addClient(name: String): ClientMutationResult {
        val normalized =
            when (val validation = ClientNameNormalizer.validate(name)) {
                is ClientNameValidationResult.Valid -> validation.name
                is ClientNameValidationResult.Invalid ->
                    return ClientMutationResult.InvalidName(validation.error)
            }
        activeConflict(normalized.canonicalName)?.let {
            return ClientMutationResult.DuplicateActiveName(it.id)
        }
        clients.value
            .firstOrNull {
                !it.isActive && it.canonicalName == normalized.canonicalName
            }?.let {
                return ClientMutationResult.MatchingArchivedClient(it)
            }

        nextId += 1
        val client =
            client(
                id = "client-$nextId",
                name = normalized.displayName,
                canonicalName = normalized.canonicalName,
            )
        clients.update { it + client }
        return ClientMutationResult.Success(client)
    }

    override suspend fun renameClient(
        clientId: String,
        name: String,
    ): ClientMutationResult {
        val current =
            clients.value.firstOrNull { it.id == clientId }
                ?: return ClientMutationResult.NotFound
        val normalized =
            when (val validation = ClientNameNormalizer.validate(name)) {
                is ClientNameValidationResult.Valid -> validation.name
                is ClientNameValidationResult.Invalid ->
                    return ClientMutationResult.InvalidName(validation.error)
            }
        if (current.isActive) {
            activeConflict(
                canonicalName = normalized.canonicalName,
                excludingClientId = clientId,
            )?.let {
                return ClientMutationResult.DuplicateActiveName(it.id)
            }
        }
        val changed =
            current.copy(
                name = normalized.displayName,
                canonicalName = normalized.canonicalName,
                updatedAt = NOW,
            )
        clients.update { values ->
            values.map { if (it.id == clientId) changed else it }
        }
        return ClientMutationResult.Success(changed)
    }

    override suspend fun archiveClient(clientId: String): ClientMutationResult {
        val current =
            clients.value.firstOrNull { it.id == clientId }
                ?: return ClientMutationResult.NotFound
        val changed =
            current.copy(
                isActive = false,
                updatedAt = NOW,
                archivedAt = NOW,
            )
        clients.update { values ->
            values.map { if (it.id == clientId) changed else it }
        }
        return ClientMutationResult.Success(changed)
    }

    override suspend fun restoreClient(clientId: String): ClientMutationResult {
        val current =
            clients.value.firstOrNull { it.id == clientId }
                ?: return ClientMutationResult.NotFound
        activeConflict(
            canonicalName = current.canonicalName,
            excludingClientId = clientId,
        )?.let {
            return ClientMutationResult.DuplicateActiveName(it.id)
        }
        val changed =
            current.copy(
                isActive = true,
                updatedAt = NOW,
                archivedAt = null,
            )
        clients.update { values ->
            values.map { if (it.id == clientId) changed else it }
        }
        return ClientMutationResult.Success(changed)
    }

    private fun activeConflict(
        canonicalName: String,
        excludingClientId: String? = null,
    ) = clients.value.firstOrNull {
        it.isActive &&
            it.canonicalName == canonicalName &&
            it.id != excludingClientId
    }

    companion object {
        private val NOW = Instant.parse("2026-07-24T12:00:00Z")
        private val CLIENT_COMPARATOR =
            compareBy<Client>(
                { it.name.lowercase() },
                Client::name,
                Client::id,
            )

        fun client(
            id: String,
            name: String,
            canonicalName: String = name.lowercase(),
            isActive: Boolean = true,
        ) = Client(
            id = id,
            name = name,
            canonicalName = canonicalName,
            isActive = isActive,
            createdAt = NOW,
            updatedAt = NOW,
            archivedAt = if (isActive) null else NOW,
        )
    }
}
