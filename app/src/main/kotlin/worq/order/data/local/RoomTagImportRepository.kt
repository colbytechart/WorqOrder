package worq.order.data.local

import worq.order.data.EntityIdGenerator
import worq.order.data.TagImportApplyResult
import worq.order.data.TagImportCandidate
import worq.order.data.TagImportRepository
import worq.order.model.TagCategory
import worq.order.timer.UtcClock

class RoomTagImportRepository(
    private val tagDao: TagDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : TagImportRepository {
    override suspend fun applyImport(
        category: TagCategory,
        candidates: List<TagImportCandidate>,
    ): TagImportApplyResult {
        val result =
            tagDao.applyTagImport(
                category = category.name,
                candidates =
                    candidates.map { candidate ->
                        TagImportEntityCandidate(
                            id = idGenerator.newId(),
                            text = candidate.displayText,
                            normalizedText = candidate.normalizedText,
                        )
                    },
                importedAtEpochMs = clock.now().toEpochMilli(),
            )
        return TagImportApplyResult(
            addedCount = result.addedCount,
            skippedExistingCount = result.skippedExistingCount,
        )
    }
}
