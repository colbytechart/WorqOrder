package worq.order.export

import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import worq.order.export.csv.CsvSerializer
import worq.order.export.google.GoogleSheetsBatchRequest
import worq.order.export.google.GoogleSheetsExportPlanner
import worq.order.export.google.GoogleSpreadsheetStructure
import worq.order.export.xlsx.XlsxWorkbookWriter

/**
 * Guards that each destination receives the same current schema-6 headers and visible values.
 * Destination-specific assertions remain in their own writer/planner tests.
 */
class Schema6ExportEquivalenceTest {
    @Test
    fun allDestinationsConsumeTheSameFourteenColumnProjection() {
        val row =
            ExportRow(
                values =
                    listOf(
                        "09/14/2026", "09/14/2026", "Consultant", "Client", "Description",
                        "Expense", "On-Site", "Billable", "12.5", "09:00 AM", "10:00 AM",
                        "01:00:00", "60", "Notes",
                    ),
                sourceTaskId = "task-1",
            )
        val snapshot =
            ExportSnapshot(
                schemaVersion = ExportSchema.VERSION,
                workDate = LocalDate.of(2026, 9, 14),
                exportedAt = Instant.parse("2026-09-14T16:00:00Z"),
                rows = listOf(row),
            )

        val csv = CsvSerializer().serialize(snapshot)
        assertEquals(
            ExportSchema.headers.joinToString(",") + "\r\n" +
                row.values.joinToString(",") + "\r\n",
            csv,
        )

        val xlsxXml =
            unzip(XlsxWorkbookWriter().write(snapshot))
                .getValue("xl/worksheets/sheet1.xml")
                .toString(Charsets.UTF_8)
        val xlsxValues =
            Regex("""<t xml:space="preserve">(.*?)</t>""")
                .findAll(xlsxXml)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(ExportSchema.headers + row.values, xlsxValues)

        val googlePlan =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = "spreadsheet-id",
                        sheets = emptyList(),
                        developerMetadata = emptyList(),
                    ),
                snapshot = snapshot,
            )
        val replace =
            ((googlePlan as worq.order.export.google.GoogleSheetsPlanResult.Ready).plan.requests
                .filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>()
                .single())
        assertEquals(ExportSchema.headers, replace.rows.first().take(ExportSchema.headers.size))
        assertEquals(row.values, replace.rows[1].take(ExportSchema.headers.size))
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> =
        buildMap {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    put(entry.name, zip.readBytes())
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
}
