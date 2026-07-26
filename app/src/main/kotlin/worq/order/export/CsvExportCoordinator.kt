package worq.order.export

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.sync.withLock
import worq.order.data.TaskRepository
import worq.order.export.csv.CsvSerializer
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.NormalizeTimerResult
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
    private val taskRepository: TaskRepository,
    private val activeTimerNormalizer: ActiveTimerNormalizer,
    private val clock: UtcClock,
    private val timerOperationLock: TimerOperationLock,
    private val rowBuilder: ExportRowBuilder = ExportRowBuilder(),
    private val serializer: CsvSerializer = CsvSerializer(),
) {
    suspend fun prepare(workDate: LocalDate): PrepareCsvExportResult =
        timerOperationLock.mutex.withLock {
            val exportedAt = clock.now()
            when (activeTimerNormalizer.normalizeWhileLocked(exportedAt)) {
                is NormalizeTimerResult.ClockChanged ->
                    return@withLock PrepareCsvExportResult.ClockChanged
                NormalizeTimerResult.ActiveTimerChanged ->
                    return@withLock PrepareCsvExportResult.ActiveTimerChanged
                NormalizeTimerResult.NoActiveTimer,
                NormalizeTimerResult.NoChange,
                is NormalizeTimerResult.Normalized,
                -> Unit
            }
            val tasks = taskRepository.readTasksWithIntervalsForDate(workDate)
            val snapshot =
                rowBuilder.build(
                    workDate = workDate,
                    exportedAt = exportedAt,
                    tasks = tasks,
                )
            PrepareCsvExportResult.Ready(
                PreparedCsvExport(
                    workDate = workDate,
                    exportedAt = exportedAt,
                    suggestedFileName = suggestedFileName(workDate),
                    mimeType = MIME_TYPE,
                    contents = serializer.serialize(snapshot),
                    dataRowCount = snapshot.rows.size,
                ),
            )
        }

    companion object {
        const val MIME_TYPE = "text/csv"

        fun suggestedFileName(workDate: LocalDate): String =
            "worqorder_$workDate.csv"
    }
}
