package worq.order.data.local

import android.database.sqlite.SQLiteConstraintException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.EntityIdGenerator
import worq.order.data.TagMutationResult
import worq.order.data.TagRepository
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationResult
import worq.order.model.Tag
import worq.order.model.TagCategory
import worq.order.timer.UtcClock

class RoomTagRepository(
    private val tagDao: TagDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : TagRepository {
    override fun observeTags(
        category: TagCategory,
        searchQuery: String,
    ): Flow<List<Tag>> {
        val normalizedQuery = TagTextNormalizer.collapseWhitespace(searchQuery).lowercase(Locale.ROOT)
        return tagDao.observeTagsForCategory(category.name).map { tags ->
            tags.asSequence()
                .filter { tag ->
                    normalizedQuery.isEmpty() ||
                        tag.text.lowercase(Locale.ROOT).contains(normalizedQuery)
                }
                .map(TagEntity::toModel)
                .toList()
        }
    }

    override suspend fun readTag(tagId: String): Tag? = tagDao.readTag(tagId)?.toModel()

    override suspend fun createTag(
        category: TagCategory,
        text: String,
    ): TagMutationResult {
        val normalized = validate(text) ?: return invalid(text)
        tagDao.findNormalizedTextConflict(category.name, normalized.normalizedText, null)?.let {
            return TagMutationResult.DuplicateNormalizedText(it.id)
        }
        val nowEpochMs = clock.now().toEpochMilli()
        val entity =
            TagEntity(
                id = idGenerator.newId(),
                category = category.name,
                text = normalized.displayText,
                normalizedText = normalized.normalizedText,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            )
        return try {
            tagDao.insertTag(entity)
            TagMutationResult.Created(entity.toModel())
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(category, normalized.normalizedText, error)
        }
    }

    override suspend fun updateTag(
        tagId: String,
        text: String,
    ): TagMutationResult {
        val current = tagDao.readTag(tagId) ?: return TagMutationResult.NotFound
        val normalized = validate(text) ?: return invalid(text)
        tagDao
            .findNormalizedTextConflict(
                category = current.category,
                normalizedText = normalized.normalizedText,
                excludingTagId = tagId,
            )?.let { return TagMutationResult.DuplicateNormalizedText(it.id) }
        return try {
            if (
                tagDao.updateTag(
                    tagId = tagId,
                    text = normalized.displayText,
                    normalizedText = normalized.normalizedText,
                    updatedAtEpochMs = clock.now().toEpochMilli(),
                ) == 0
            ) {
                TagMutationResult.NotFound
            } else {
                TagMutationResult.Updated(requireNotNull(tagDao.readTag(tagId)).toModel())
            }
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(TagCategory.valueOf(current.category), normalized.normalizedText, error)
        }
    }

    override suspend fun deleteTag(tagId: String): TagMutationResult =
        if (tagDao.deleteTag(tagId) == 1) TagMutationResult.Deleted else TagMutationResult.NotFound

    private fun validate(text: String) =
        (TagTextNormalizer.validate(text) as? TagTextValidationResult.Valid)?.text

    private fun invalid(text: String): TagMutationResult.InvalidText =
        TagMutationResult.InvalidText(
            (TagTextNormalizer.validate(text) as TagTextValidationResult.Invalid).error,
        )

    private suspend fun duplicateAfterConstraint(
        category: TagCategory,
        normalizedText: String,
        error: SQLiteConstraintException,
    ): TagMutationResult {
        val conflict = tagDao.findNormalizedTextConflict(category.name, normalizedText, null)
        if (conflict != null) return TagMutationResult.DuplicateNormalizedText(conflict.id)
        throw error
    }
}
