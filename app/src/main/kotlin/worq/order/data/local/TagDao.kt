package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
}
