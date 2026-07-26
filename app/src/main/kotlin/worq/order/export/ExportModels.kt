package worq.order.export

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.timer.DurationMath

object ExportSchema {
    const val VERSION = 1

    val headers: List<String> =
        listOf(
            "Schema Version",
            "Exported At UTC",
            "Work Date",
            "Time Zone",
            "Client ID",
            "Client Name",
            "Task ID",
            "Task Series ID",
            "Description",
            "Hardware / Software Purchases",
            "Interval ID",
            "Interval Number",
            "Interval State",
            "Start Local",
            "Stop Local",
            "Start UTC",
            "Stop UTC",
            "Interval Duration Milliseconds",
            "Interval Duration Formatted",
            "Task Total Duration Milliseconds",
            "Task Total Duration Formatted",
            "Task Created UTC",
            "Task Updated UTC",
            "Interval Manually Edited",
        )
}

data class ExportRow(
    val values: List<String>,
) {
    init {
        require(values.size == ExportSchema.headers.size) {
            "Export row has ${values.size} values; expected ${ExportSchema.headers.size}"
        }
    }
}

data class ExportSnapshot(
    val workDate: LocalDate,
    val exportedAt: Instant,
    val rows: List<ExportRow>,
)

class ExportRowBuilder {
    fun build(
        workDate: LocalDate,
        exportedAt: Instant,
        tasks: List<TaskWithIntervals>,
    ): ExportSnapshot {
        val sortedTasks =
            tasks
                .onEach {
                    require(it.taskWithClient.task.workDate == workDate) {
                        "Task ${it.taskWithClient.task.id} does not belong to $workDate"
                    }
                }.sortedWith(
                    compareBy<TaskWithIntervals> {
                        it.taskWithClient.task.createdAt
                    }.thenBy {
                        it.taskWithClient.task.id
                    },
                )
        val rows =
            sortedTasks.flatMap { detail ->
                val intervals =
                    detail.intervals.sortedWith(
                        compareBy<WorkInterval> { it.start }
                            .thenBy { it.ordinal }
                            .thenBy { it.id },
                    )
                val total =
                    DurationMath.taskTotal(
                        intervals = intervals,
                        activeEvaluationInstant =
                            exportedAt.takeIf {
                                intervals.any { interval -> interval.stop == null }
                            },
                    )
                if (intervals.isEmpty()) {
                    listOf(
                        detail.row(
                            exportedAt = exportedAt,
                            interval = null,
                            intervalDuration = null,
                            taskTotal = total,
                        ),
                    )
                } else {
                    intervals.map { interval ->
                        val duration =
                            interval.stop?.let { stop ->
                                DurationMath.nonNegativeBetween(interval.start, stop)
                            } ?: DurationMath.activeInterval(
                                start = interval.start,
                                evaluationInstant = exportedAt,
                            )
                        detail.row(
                            exportedAt = exportedAt,
                            interval = interval,
                            intervalDuration = duration,
                            taskTotal = total,
                        )
                    }
                }
            }
        return ExportSnapshot(
            workDate = workDate,
            exportedAt = exportedAt,
            rows = rows,
        )
    }

    private fun TaskWithIntervals.row(
        exportedAt: Instant,
        interval: WorkInterval?,
        intervalDuration: Duration?,
        taskTotal: Duration,
    ): ExportRow {
        val task = taskWithClient.task
        val client = taskWithClient.client
        val stop = interval?.stop
        return ExportRow(
            values =
                listOf(
                    ExportSchema.VERSION.toString(),
                    exportedAt.toString(),
                    task.workDate.toString(),
                    task.zoneId.id,
                    client.id,
                    client.name,
                    task.id,
                    task.seriesId,
                    task.description,
                    task.hardwareSoftwarePurchases,
                    interval?.id.orEmpty(),
                    interval?.ordinal?.toString().orEmpty(),
                    when {
                        interval == null -> "NO_INTERVAL"
                        stop == null -> "RUNNING"
                        else -> "COMPLETED"
                    },
                    interval?.start?.let {
                        DateTimeFormatter.ISO_ZONED_DATE_TIME.format(
                            it.atZone(task.zoneId),
                        )
                    }.orEmpty(),
                    stop?.let {
                        DateTimeFormatter.ISO_ZONED_DATE_TIME.format(
                            it.atZone(task.zoneId),
                        )
                    }.orEmpty(),
                    interval?.start?.toString().orEmpty(),
                    stop?.toString().orEmpty(),
                    intervalDuration?.toMillis()?.toString().orEmpty(),
                    intervalDuration?.let(DurationMath::formatAccumulated).orEmpty(),
                    taskTotal.toMillis().toString(),
                    DurationMath.formatAccumulated(taskTotal),
                    task.createdAt.toString(),
                    task.updatedAt.toString(),
                    interval?.wasManuallyEdited?.toString().orEmpty(),
                ),
        )
    }
}
