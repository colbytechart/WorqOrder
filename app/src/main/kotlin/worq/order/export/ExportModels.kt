package worq.order.export

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType
import worq.order.model.BillingStatus
import worq.order.domain.BillingMinutes
import worq.order.timer.DurationMath
import worq.order.util.ClockTimeFormatter

object ExportSchema {
    const val VERSION = 4

    val headers: List<String> =
        listOf(
            "Start date",
            "End date",
            "Consultant",
            "Client",
            "Description",
            "Expense",
            "Work type",
            "Billing Status",
            "Mileage",
            "Interval number",
            "Start time",
            "Stop time",
            "Interval duration",
            "Time spent",
            "Billing minutes",
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
        val exportDate = ExportValueFormatter.date(task.workDate)
        return ExportRow(
            values =
                listOf(
                    exportDate,
                    exportDate,
                    task.employeeNameSnapshot,
                    client.name,
                    task.description,
                    task.hardwareSoftwarePurchases,
                    ExportValueFormatter.workType(task.workType),
                    ExportValueFormatter.billingStatus(task.billingStatus),
                    task.mileage.orEmpty(),
                    interval?.ordinal?.toString().orEmpty(),
                    interval?.start?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    stop?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    intervalDuration?.let(ExportValueFormatter::duration).orEmpty(),
                    ExportValueFormatter.duration(taskTotal),
                    BillingMinutes.fromDuration(taskTotal).toString(),
                ),
        )
    }
}

object ExportValueFormatter {
    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.ROOT)

    fun date(date: LocalDate): String = dateFormatter.format(date)

    fun localTime(
        instant: Instant,
        zoneId: ZoneId,
    ): String = ClockTimeFormatter.format(instant, zoneId)

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

    fun workType(workType: WorkType): String =
        when (workType) {
            WorkType.ON_SITE -> "On-Site"
            WorkType.IN_OFFICE -> "In-Office"
            WorkType.UNSPECIFIED -> ""
        }

    fun billingStatus(billingStatus: BillingStatus?): String =
        when (billingStatus) {
            BillingStatus.BILLABLE -> "Billable"
            BillingStatus.DO_NOT_BILL -> "Do not bill"
            BillingStatus.DO_NOT_CHARGE -> "Do not charge"
            null -> ""
        }
}
