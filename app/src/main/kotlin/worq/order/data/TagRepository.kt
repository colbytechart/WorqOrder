package worq.order.data

import kotlinx.coroutines.flow.Flow
import worq.order.model.Tag
import worq.order.model.TagCategory

sealed interface TagMutationResult {
    data class Created(
        val tag: Tag,
    ) : TagMutationResult

    data class Updated(
        val tag: Tag,
    ) : TagMutationResult

    data object Deleted : TagMutationResult

    data object NotFound : TagMutationResult

    data class InvalidText(
        val reason: TagTextValidationError,
    ) : TagMutationResult

    data class DuplicateNormalizedText(
        val conflictingTagId: String,
    ) : TagMutationResult
}

interface TagRepository {
    fun observeTags(
        category: TagCategory,
        searchQuery: String = "",
    ): Flow<List<Tag>>

    suspend fun readTag(tagId: String): Tag?

    suspend fun createTag(
        category: TagCategory,
        text: String,
    ): TagMutationResult

    suspend fun updateTag(
        tagId: String,
        text: String,
    ): TagMutationResult

    /** Deletes only the catalog record; task-owned snapshots intentionally survive. */
    suspend fun deleteTag(tagId: String): TagMutationResult
}
