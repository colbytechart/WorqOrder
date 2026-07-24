package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_intervals",
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
            name = "index_work_intervals_task_ordinal",
            value = ["task_id", "ordinal"],
            unique = true,
        ),
        Index(
            name = "index_work_intervals_task_start",
            value = ["task_id", "start_epoch_ms", "ordinal", "id"],
        ),
        Index(
            name = "index_work_intervals_active_slot",
            value = ["active_slot"],
            unique = true,
        ),
        Index(
            name = "index_work_intervals_id_task",
            value = ["id", "task_id"],
            unique = true,
        ),
    ],
)
data class WorkIntervalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "start_epoch_ms")
    val startEpochMs: Long,
    @ColumnInfo(name = "stop_epoch_ms")
    val stopEpochMs: Long?,
    /**
     * Null for completed intervals and [ACTIVE_SLOT] for the sole open interval.
     *
     * SQLite unique indexes allow multiple null values, so this column structurally prevents a
     * second open interval without requiring a custom partial index.
     */
    @ColumnInfo(name = "active_slot")
    val activeSlot: Int?,
    @ColumnInfo(name = "was_manually_edited")
    val wasManuallyEdited: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val ACTIVE_SLOT = 1
    }
}
