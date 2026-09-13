package worq.order.data.local

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.model.BillingStatus
import worq.order.model.Client
import worq.order.model.DailyTask
import worq.order.model.Employee
import worq.order.model.TaskListItem
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType

internal fun ClientEntity.toModel(): Client =
    Client(
        id = id,
        name = name,
        canonicalName = canonicalName,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
        archivedAt = archivedAtEpochMs?.let(Instant::ofEpochMilli),
    )

internal fun EmployeeEntity.toModel(): Employee =
    Employee(
        id = id,
        name = name,
        canonicalName = canonicalName,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
        archivedAt = archivedAtEpochMs?.let(Instant::ofEpochMilli),
    )

internal fun DailyTaskEntity.toModel(): DailyTask =
    DailyTask(
        id = id,
        seriesId = seriesId,
        clientId = clientId,
        description = description,
        hardwareSoftwarePurchases = hardwareSoftwarePurchases,
        employeeId = employeeId,
        employeeNameSnapshot = employeeNameSnapshot,
        workType = WorkType.valueOf(workType),
        billingStatus = billingStatus?.let(BillingStatus::valueOf),
        mileage = mileage,
        workDate = LocalDate.ofEpochDay(workDateEpochDay),
        zoneId = ZoneId.of(zoneId),
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )

internal fun WorkIntervalEntity.toModel(): WorkInterval =
    WorkInterval(
        id = id,
        taskId = taskId,
        // `ordinal` is no longer persisted in schema 5. Retain this temporary presentation
        // adapter only until the schema-5 task/editor/export phase removes the old UI contract.
        ordinal = 1,
        start = Instant.ofEpochMilli(startEpochMs),
        stop = stopEpochMs?.let(Instant::ofEpochMilli),
        wasManuallyEdited = wasManuallyEdited,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )

internal fun ActiveTimerEntity.toModel(): ActiveTimer =
    ActiveTimer(
        intervalId = intervalId,
        taskId = taskId,
        boundaryZoneId = ZoneId.of(boundaryZoneId),
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )

internal fun TaskWithClientEntity.toModel(): TaskWithClient =
    TaskWithClient(
        task = task.toModel(),
        client =
            Client(
                id = task.clientId,
                name = clientName,
                canonicalName = clientCanonicalName,
                isActive = clientIsActive,
                createdAt = Instant.ofEpochMilli(clientCreatedAtEpochMs),
                updatedAt = Instant.ofEpochMilli(clientUpdatedAtEpochMs),
                archivedAt = clientArchivedAtEpochMs?.let(Instant::ofEpochMilli),
            ),
    )

internal fun TaskListItemEntity.toModel(): TaskListItem =
    TaskListItem(
        task = task.toModel(),
        clientName = clientName,
        clientIsActive = clientIsActive,
        completedDuration = Duration.ofMillis(completedDurationMs),
    )

internal fun TaskWithOrderedIntervalsEntity.toModel(): TaskWithIntervals =
    TaskWithIntervals(
        taskWithClient = taskWithClient.toModel(),
        intervals = intervals.map(WorkIntervalEntity::toModel),
    )

internal fun ActiveTimerTransactionEntity.toModel(): ActiveTimerSnapshot =
    ActiveTimerSnapshot(
        activeTimer = activeTimer.toModel(),
        interval = interval.toModel(),
    )
