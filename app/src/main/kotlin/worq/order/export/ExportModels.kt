package worq.order.export

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import worq.order.data.MAX_COMPOSED_TASK_TEXT_CODE_POINTS
import worq.order.data.TaskTextComposer
import worq.order.domain.BillingMinutes
import worq.order.model.BillingStatus
import worq.order.model.TagCategory
import worq.order.model.TaskTagSnapshot
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType
import worq.order.timer.DurationMath
import worq.order.util.ClockTimeFormatter

object ExportSchema {
    const val VERSION = 6

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
            "Start time",
            "Stop time",
            "Time spent",
            "Billing minutes",
            "Notes",
        )
}

data class ExportRow(
    val values: List<String>,
    /** Stable local identity for Google row reconciliation; never a visible export column. */
    val sourceTaskId: String? = null,
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
            sortedTasks.map { detail ->
                require(detail.intervals.size <= 1) {
                    "Schema 6 permits at most one interval per task"
                }
                val interval = detail.intervals.singleOrNull()
                val total =
                    DurationMath.taskTotal(
                        intervals = detail.intervals,
                        activeEvaluationInstant =
                            interval
                                ?.takeIf { it.stop == null }
                                ?.let { exportedAt },
                    )
                detail.row(
                    interval = interval,
                    taskTotal = total,
                )
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
        taskTotal: Duration,
    ): ExportRow {
        val task = taskWithClient.task
        val client = taskWithClient.client
        val stop = interval?.stop
        val exportDate = ExportValueFormatter.date(task.workDate)
        val description =
            composeTaskExportText(
                manualText = task.description,
                category = TagCategory.DESCRIPTION,
            )
        val expense =
            composeTaskExportText(
                manualText = task.hardwareSoftwarePurchases,
                category = TagCategory.HARDWARE_SOFTWARE_PURCHASE,
            )
        return ExportRow(
            sourceTaskId = task.id,
            values =
                listOf(
                    exportDate,
                    exportDate,
                    task.employeeNameSnapshot,
                    client.name,
                    description,
                    expense,
                    ExportValueFormatter.workType(task.workType),
                    ExportValueFormatter.billingStatus(task.billingStatus),
                    task.mileage.orEmpty(),
                    interval?.start?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    stop?.let {
                        ExportValueFormatter.localTime(it, task.zoneId)
                    }.orEmpty(),
                    ExportValueFormatter.duration(taskTotal),
                    BillingMinutes.fromDuration(taskTotal).toString(),
                    task.notes,
                ),
        )
    }

    /**
     * The snapshot, rather than the mutable Tag catalog, is authoritative for export text.
     * Every destination receives the composed value already embedded in [ExportRow].
     */
    private fun TaskWithIntervals.composeTaskExportText(
        manualText: String,
        category: TagCategory,
    ): String {
        val composed =
            TaskTextComposer.compose(
                manualText = manualText,
                tagTexts =
                    tagSnapshots
                        .asSequence()
                        .filter { it.category == category }
                        .sortedWith(
                            compareBy<TaskTagSnapshot> { it.selectionOrder }
                                .thenBy { it.id },
                        ).map(TaskTagSnapshot::text)
                        .toList(),
            )
        require(TaskTextComposer.codePointCount(composed) <= MAX_COMPOSED_TASK_TEXT_CODE_POINTS) {
            "Saved task text exceeds the supported export limit"
        }
        return composed
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
