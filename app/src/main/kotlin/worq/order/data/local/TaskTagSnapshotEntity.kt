package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A task-owned Tag text snapshot. [sourceTagId] deliberately has no foreign key so deleting a
 * reusable Tag never destroys or mutates historical task text.
 */
@Entity(
    tableName = "task_tag_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = DailyTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            name = "index_task_tag_snapshots_task_category_order",
            value = ["task_id", "category", "selection_order"],
            unique = true,
        ),
        Index(
            name = "index_task_tag_snapshots_source_tag_id",
            value = ["source_tag_id"],
        ),
    ],
)
data class TaskTagSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "text_snapshot")
    val textSnapshot: String,
    @ColumnInfo(name = "source_tag_id")
    val sourceTagId: String? = null,
    @ColumnInfo(name = "selection_order")
    val selectionOrder: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)
