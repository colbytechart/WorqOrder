package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TagImportEntityCandidate(
    val id: String,
    val text: String,
    val normalizedText: String,
)

data class TagImportEntityResult(
    val addedCount: Int,
    val skippedExistingCount: Int,
)

@Dao
abstract class TagDao {
    @Query(
        """
        SELECT *
        FROM tags
        WHERE category = :category
        ORDER BY text COLLATE NOCASE ASC, text ASC, id ASC
        """,
    )
    abstract fun observeTagsForCategory(category: String): Flow<List<TagEntity>>

    /** Stable read order for a portable logical snapshot. */
    @Query("SELECT * FROM tags ORDER BY id ASC")
    abstract suspend fun readAllTagsForPortableBackup(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :tagId LIMIT 1")
    abstract suspend fun readTag(tagId: String): TagEntity?

    @Query(
        """
        SELECT *
        FROM tags
        WHERE category = :category
          AND normalized_text = :normalizedText
          AND (:excludingTagId IS NULL OR id != :excludingTagId)
        LIMIT 1
        """,
    )
    abstract suspend fun findNormalizedTextConflict(
        category: String,
        normalizedText: String,
        excludingTagId: String?,
    ): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTag(tag: TagEntity)

    @Query(
        """
        UPDATE tags
        SET text = :text,
            normalized_text = :normalizedText,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :tagId
        """,
    )
    abstract suspend fun updateTag(
        tagId: String,
        text: String,
        normalizedText: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query("DELETE FROM tags WHERE id = :tagId")
    abstract suspend fun deleteTag(tagId: String): Int

    @Transaction
    open suspend fun applyTagImport(
        category: String,
        candidates: List<TagImportEntityCandidate>,
        importedAtEpochMs: Long,
    ): TagImportEntityResult {
        var addedCount = 0
        var skippedExistingCount = 0
        candidates.forEach { candidate ->
            if (
                findNormalizedTextConflict(
                    category = category,
                    normalizedText = candidate.normalizedText,
                    excludingTagId = null,
                ) != null
            ) {
                skippedExistingCount += 1
            } else {
                insertTag(
                    TagEntity(
                        id = candidate.id,
                        category = category,
                        text = candidate.text,
                        normalizedText = candidate.normalizedText,
                        createdAtEpochMs = importedAtEpochMs,
                        updatedAtEpochMs = importedAtEpochMs,
                    ),
                )
                addedCount += 1
            }
        }
        return TagImportEntityResult(
            addedCount = addedCount,
            skippedExistingCount = skippedExistingCount,
        )
    }
}
