package worq.order.export

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.timer.DurationMath

object ExportSchema {
    const val VERSION = 2

    val headers: List<String> =
        listOf(
            "Work Date",
            "Client Name",
            "Description",
            "Hardware / Software Purchases",
            "Interval Number",
            "Start Local",
            "Stop Local",
            "Interval Duration Formatted",
            "Task Total Duration Formatted",
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
    val schemaVersion: Int,
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
                            interval = interval,
                            intervalDuration = duration,
                            taskTotal = total,
                        )
                    }
                }
            }
        return ExportSnapshot(
            schemaVersion = ExportSchema.VERSION,
            workDate = workDate,
            exportedAt = exportedAt,
            rows = rows,
        )
    }

    private fun TaskWithIntervals.row(
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
                    task.workDate.toString(),
                    client.name,
                    task.description,
                    task.hardwareSoftwarePurchases,
                    interval?.ordinal?.toString().orEmpty(),
                    interval?.start?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    stop?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    intervalDuration?.let(ExportValueFormatter::duration).orEmpty(),
                    ExportValueFormatter.duration(taskTotal),
                ),
        )
    }
}

object ExportValueFormatter {
    private val localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun localTime(
        instant: Instant,
        zoneId: ZoneId,
    ): String = localTimeFormatter.format(instant.atZone(zoneId))

    fun duration(duration: Duration): String {
        require(!duration.isNegative) { "Export duration must not be negative" }
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds,
        )
    }
}
