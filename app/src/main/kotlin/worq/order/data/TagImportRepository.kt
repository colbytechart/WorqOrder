package worq.order.data

import worq.order.model.TagCategory

data class TagImportCandidate(
    val displayText: String,
    val normalizedText: String,
)

data class TagImportApplyResult(
    val addedCount: Int,
    val skippedExistingCount: Int,
)

interface TagImportRepository {
    /** Applies a fully validated, category-scoped unique batch in one Room transaction. */
    suspend fun applyImport(
        category: TagCategory,
        candidates: List<TagImportCandidate>,
    ): TagImportApplyResult
}
