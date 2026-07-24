package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class TaskWithClientEntity(
    @Embedded
    val task: DailyTaskEntity,
    @ColumnInfo(name = "joined_client_name")
    val clientName: String,
    @ColumnInfo(name = "joined_client_canonical_name")
    val clientCanonicalName: String,
    @ColumnInfo(name = "joined_client_is_active")
    val clientIsActive: Boolean,
    @ColumnInfo(name = "joined_client_created_at_epoch_ms")
    val clientCreatedAtEpochMs: Long,
    @ColumnInfo(name = "joined_client_updated_at_epoch_ms")
    val clientUpdatedAtEpochMs: Long,
    @ColumnInfo(name = "joined_client_archived_at_epoch_ms")
    val clientArchivedAtEpochMs: Long?,
)

data class TaskListItemEntity(
    @Embedded
    val task: DailyTaskEntity,
    @ColumnInfo(name = "joined_client_name")
    val clientName: String,
    @ColumnInfo(name = "joined_client_is_active")
    val clientIsActive: Boolean,
    @ColumnInfo(name = "completed_duration_ms")
    val completedDurationMs: Long,
)

data class TaskWithOrderedIntervalsEntity(
    val taskWithClient: TaskWithClientEntity,
    val intervals: List<WorkIntervalEntity>,
)

data class ActiveTimerTransactionEntity(
    val activeTimer: ActiveTimerEntity,
    val interval: WorkIntervalEntity,
)
