package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "active_timer",
    foreignKeys = [
        ForeignKey(
            entity = WorkIntervalEntity::class,
            parentColumns = ["id", "task_id"],
            childColumns = ["interval_id", "task_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            name = "index_active_timer_interval_task",
            value = ["interval_id", "task_id"],
            unique = true,
        ),
        Index(
            name = "index_active_timer_task_id",
            value = ["task_id"],
        ),
    ],
)
data class ActiveTimerEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "interval_id")
    val intervalId: String,
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "boundary_zone_id")
    val boundaryZoneId: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
