package worq.order.data.local

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.ClientMutationResult
import worq.order.data.ClientNameValidationResult
import worq.order.data.ClientNameNormalizer
import worq.order.data.ClientRepository
import worq.order.data.EntityIdGenerator
import worq.order.model.Client
import worq.order.timer.UtcClock

class RoomClientRepository(
    private val clientDao: ClientDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : ClientRepository {
    override fun observeActiveClients(): Flow<List<Client>> =
        clientDao.observeActiveClients().map { clients ->
            clients.map(ClientEntity::toModel)
        }

    override fun observeAllClients(): Flow<List<Client>> =
        clientDao.observeAllClients().map { clients ->
            clients.map(ClientEntity::toModel)
        }

    override suspend fun addClient(name: String): ClientMutationResult {
        val normalized = validateName(name) ?: return invalidName(name)
        findConflict(normalized.canonicalName)?.let { conflict ->
            return ClientMutationResult.DuplicateActiveName(conflict.id)
        }
        clientDao
            .findArchivedClientByNormalizedName(normalized.canonicalName)
            ?.let { archived ->
                return ClientMutationResult.MatchingArchivedClient(archived.toModel())
            }

        val nowEpochMs = clock.now().toEpochMilli()
        val entity =
            ClientEntity(
                id = idGenerator.newId(),
                name = normalized.displayName,
                canonicalName = normalized.canonicalName,
                activeNameKey = normalized.canonicalName,
                isActive = true,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                archivedAtEpochMs = null,
            )

        return try {
            clientDao.addClient(entity)
            ClientMutationResult.Success(entity.toModel())
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(normalized.canonicalName, error)
        }
    }

    override suspend fun renameClient(
        clientId: String,
        name: String,
    ): ClientMutationResult {
        val current = clientDao.readClient(clientId) ?: return ClientMutationResult.NotFound
        val normalized = validateName(name) ?: return invalidName(name)
        if (current.isActive) {
            findConflict(normalized.canonicalName, excludingClientId = clientId)?.let { conflict ->
                return ClientMutationResult.DuplicateActiveName(conflict.id)
            }
        }

        return try {
            val changed =
                clientDao.renameClient(
                    clientId = clientId,
                    name = normalized.displayName,
                    canonicalName = normalized.canonicalName,
                    updatedAtEpochMs = clock.now().toEpochMilli(),
                )
            if (changed == 0) {
                ClientMutationResult.NotFound
            } else {
                ClientMutationResult.Success(
                    requireNotNull(clientDao.readClient(clientId)).toModel(),
                )
            }
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(normalized.canonicalName, error)
        }
    }

    override suspend fun archiveClient(clientId: String): ClientMutationResult {
        val current = clientDao.readClient(clientId) ?: return ClientMutationResult.NotFound
        if (!current.isActive) {
            return ClientMutationResult.Success(current.toModel())
        }
        clientDao.archiveClient(
            clientId = clientId,
            archivedAtEpochMs = clock.now().toEpochMilli(),
        )
        return ClientMutationResult.Success(
            requireNotNull(clientDao.readClient(clientId)).toModel(),
        )
    }

    override suspend fun restoreClient(clientId: String): ClientMutationResult {
        val current = clientDao.readClient(clientId) ?: return ClientMutationResult.NotFound
        if (current.isActive) {
            return ClientMutationResult.Success(current.toModel())
        }
        findConflict(current.canonicalName, excludingClientId = clientId)?.let { conflict ->
            return ClientMutationResult.DuplicateActiveName(conflict.id)
        }

        return try {
            clientDao.restoreClient(
                clientId = clientId,
                restoredAtEpochMs = clock.now().toEpochMilli(),
            )
            ClientMutationResult.Success(
                requireNotNull(clientDao.readClient(clientId)).toModel(),
            )
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(current.canonicalName, error)
        }
    }

    private fun validateName(name: String) =
        (ClientNameNormalizer.validate(name) as? ClientNameValidationResult.Valid)?.name

    private fun invalidName(name: String): ClientMutationResult.InvalidName {
        val invalid = ClientNameNormalizer.validate(name) as ClientNameValidationResult.Invalid
        return ClientMutationResult.InvalidName(invalid.error)
    }

    private suspend fun findConflict(
        canonicalName: String,
        excludingClientId: String? = null,
    ) = clientDao.findNormalizedClientNameConflict(canonicalName, excludingClientId)

    private suspend fun duplicateAfterConstraint(
        canonicalName: String,
        error: SQLiteConstraintException,
    ): ClientMutationResult {
        val conflict = findConflict(canonicalName)
        if (conflict != null) {
            return ClientMutationResult.DuplicateActiveName(conflict.id)
        }
        throw error
    }
}
