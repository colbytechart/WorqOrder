package worq.order.export

import java.time.Instant
import java.time.LocalDate
import worq.order.data.TaskRepository
import worq.order.export.csv.CsvSerializer
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.TimerOperationLock
import worq.order.timer.UtcClock

data class PreparedCsvExport(
    val workDate: LocalDate,
    val exportedAt: Instant,
    val suggestedFileName: String,
    val mimeType: String,
    val contents: String,
    val dataRowCount: Int,
)

sealed interface PrepareCsvExportResult {
    data class Ready(
        val export: PreparedCsvExport,
    ) : PrepareCsvExportResult

    data object ClockChanged : PrepareCsvExportResult

    data object ActiveTimerChanged : PrepareCsvExportResult
}

class CsvExportCoordinator(
    private val snapshotCoordinator: ExportSnapshotCoordinator,
    private val serializer: CsvSerializer = CsvSerializer(),
) {
    constructor(
        taskRepository: TaskRepository,
        activeTimerNormalizer: ActiveTimerNormalizer,
        clock: UtcClock,
        timerOperationLock: TimerOperationLock,
        rowBuilder: ExportRowBuilder = ExportRowBuilder(),
        serializer: CsvSerializer = CsvSerializer(),
    ) : this(
        snapshotCoordinator =
            ExportSnapshotCoordinator(
                taskRepository = taskRepository,
                activeTimerNormalizer = activeTimerNormalizer,
                clock = clock,
                timerOperationLock = timerOperationLock,
                rowBuilder = rowBuilder,
            ),
        serializer = serializer,
    )

    suspend fun prepare(workDate: LocalDate): PrepareCsvExportResult =
        when (val result = snapshotCoordinator.prepare(workDate)) {
            PrepareExportSnapshotResult.ActiveTimerChanged ->
                PrepareCsvExportResult.ActiveTimerChanged
            PrepareExportSnapshotResult.ClockChanged ->
                PrepareCsvExportResult.ClockChanged
            is PrepareExportSnapshotResult.Ready -> {
                val snapshot = result.snapshot
                PrepareCsvExportResult.Ready(
                    PreparedCsvExport(
                        workDate = snapshot.workDate,
                        exportedAt = snapshot.exportedAt,
                        suggestedFileName = suggestedFileName(snapshot.workDate),
                        mimeType = MIME_TYPE,
                        contents = serializer.serialize(snapshot),
                        dataRowCount = snapshot.rows.size,
                    ),
                )
            }
        }

    companion object {
        const val MIME_TYPE = "text/csv"

        fun suggestedFileName(workDate: LocalDate): String =
            "worqorder_$workDate.csv"
    }
}
