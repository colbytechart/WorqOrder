package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

enum class TaskCreationWriteStatus {
    CREATED,
    CLIENT_UNAVAILABLE,
    EMPLOYEE_UNAVAILABLE,
}

data class TaskCreationEntityResult(
    val status: TaskCreationWriteStatus,
    val task: DailyTaskEntity? = null,
)

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

data class StartTimerTransactionEntity(
    val startedTask: DailyTaskEntity,
    val activeTimer: ActiveTimerEntity,
    val interval: WorkIntervalEntity,
    val repeatedTaskCreated: Boolean,
)

data class TimerContinuationEntityInput(
    val boundaryEpochMs: Long,
    val workDateEpochDay: Long,
    val zoneId: String,
    val proposedTaskId: String,
    val proposedIntervalId: String,
)

enum class TaskMetadataWriteStatus {
    UPDATED,
    TASK_NOT_FOUND,
    CLIENT_UNAVAILABLE,
    EMPLOYEE_UNAVAILABLE,
    RUNNING_TASK,
}

data class TaskMetadataWriteEntityResult(
    val status: TaskMetadataWriteStatus,
    val task: DailyTaskEntity? = null,
)

enum class TaskDeleteStatus {
    DELETED,
    TASK_NOT_FOUND,
    RUNNING_TASK,
}

enum class ManualIntervalWriteStatus {
    SAVED,
    DELETED,
    TASK_NOT_FOUND,
    TASK_ALREADY_HAS_INTERVAL,
    INTERVAL_NOT_FOUND,
    RUNNING_TASK,
    RUNNING_INTERVAL,
    OVERLAP,
}

data class ManualIntervalWriteEntityResult(
    val status: ManualIntervalWriteStatus,
    val interval: WorkIntervalEntity? = null,
)
