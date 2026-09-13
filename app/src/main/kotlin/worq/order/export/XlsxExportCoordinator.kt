package worq.order.export

import java.time.Instant
import java.time.LocalDate
import worq.order.export.xlsx.XlsxWorkbookWriter

data class PreparedXlsxExport(
    val workDate: LocalDate,
    val exportedAt: Instant,
    val suggestedFileName: String,
    val mimeType: String,
    val contents: ByteArray,
    val dataRowCount: Int,
)

sealed interface PrepareXlsxExportResult {
    data class Ready(
        val export: PreparedXlsxExport,
    ) : PrepareXlsxExportResult

    data object ClockChanged : PrepareXlsxExportResult

    data object ActiveTimerChanged : PrepareXlsxExportResult
}

class XlsxExportCoordinator(
    private val snapshotCoordinator: ExportSnapshotProvider,
    private val writer: XlsxWorkbookWriter = XlsxWorkbookWriter(),
) {
    suspend fun prepare(workDate: LocalDate): PrepareXlsxExportResult =
        when (val result = snapshotCoordinator.prepare(workDate)) {
            PrepareExportSnapshotResult.ActiveTimerChanged ->
                PrepareXlsxExportResult.ActiveTimerChanged
            PrepareExportSnapshotResult.ActiveTimerRunning ->
                PrepareXlsxExportResult.ActiveTimerChanged
            PrepareExportSnapshotResult.ClockChanged ->
                PrepareXlsxExportResult.ClockChanged
            is PrepareExportSnapshotResult.Ready -> {
                val snapshot = result.snapshot
                PrepareXlsxExportResult.Ready(
                    PreparedXlsxExport(
                        workDate = snapshot.workDate,
                        exportedAt = snapshot.exportedAt,
                        suggestedFileName = suggestedFileName(snapshot.workDate),
                        mimeType = MIME_TYPE,
                        contents = writer.write(snapshot),
                        dataRowCount = snapshot.rows.size,
                    ),
                )
            }
        }

    companion object {
        const val MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        fun suggestedFileName(workDate: LocalDate): String =
            "worqorder_$workDate.xlsx"
    }
}
