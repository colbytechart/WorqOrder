package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A reusable catalog record. Catalog records never own task history. */
@Entity(
    tableName = "tags",
    indices = [
        Index(
            name = "index_tags_category_normalized_text",
            value = ["category", "normalized_text"],
            unique = true,
        ),
        Index(
            name = "index_tags_category_sort",
            value = ["category", "text", "id"],
        ),
    ],
)
data class TagEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
