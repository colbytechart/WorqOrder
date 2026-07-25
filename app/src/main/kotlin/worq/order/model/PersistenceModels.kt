package worq.order.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Client(
    val id: String,
    val name: String,
    val canonicalName: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
)

data class DailyTask(
    val id: String,
    val seriesId: String,
    val clientId: String,
    val description: String,
    val hardwareSoftwarePurchases: String = "",
    val workDate: LocalDate,
    val zoneId: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkInterval(
    val id: String,
    val taskId: String,
    val ordinal: Int,
    val start: Instant,
    val stop: Instant?,
    val wasManuallyEdited: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ActiveTimer(
    val intervalId: String,
    val taskId: String,
    val boundaryZoneId: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TaskWithClient(
    val task: DailyTask,
    val client: Client,
)

data class TaskListItem(
    val task: DailyTask,
    val clientName: String,
    val clientIsActive: Boolean,
    val completedDuration: Duration,
)

data class TaskWithIntervals(
    val taskWithClient: TaskWithClient,
    val intervals: List<WorkInterval>,
)

data class ActiveTimerSnapshot(
    val activeTimer: ActiveTimer,
    val interval: WorkInterval,
)
