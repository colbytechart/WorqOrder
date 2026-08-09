package worq.order.data.local

import worq.order.data.ClientImportApplyResult
import worq.order.data.ClientImportCandidate
import worq.order.data.ClientImportRepository
import worq.order.data.EntityIdGenerator
import worq.order.timer.UtcClock

class RoomClientImportRepository(
    private val clientDao: ClientDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : ClientImportRepository {
    override suspend fun applyImport(
        candidates: List<ClientImportCandidate>,
    ): ClientImportApplyResult {
        val result =
            clientDao.applyClientImport(
                candidates =
                    candidates.map { candidate ->
                        ClientImportEntityCandidate(
                            id = idGenerator.newId(),
                            displayName = candidate.displayName,
                            canonicalName = candidate.canonicalName,
                        )
                    },
                importedAtEpochMs = clock.now().toEpochMilli(),
            )
        return ClientImportApplyResult(
            addedCount = result.addedCount,
            restoredCount = result.restoredCount,
            skippedActiveCount = result.skippedActiveCount,
        )
    }
}
