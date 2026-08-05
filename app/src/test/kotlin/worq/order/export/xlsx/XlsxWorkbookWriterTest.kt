package worq.order.export.xlsx

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot
import worq.order.export.PrepareExportSnapshotResult
import worq.order.export.PrepareXlsxExportResult
import worq.order.export.XlsxExportCoordinator

class XlsxWorkbookWriterTest {
    private val writer = XlsxWorkbookWriter()

    @Test
    fun writesDeterministicMinimalPackageWithOneDateSheet() {
        val snapshot =
            snapshot(
                rows =
                    listOf(
                        row(
                            "07/24/2026",
                            "07/24/2026",
                            "Employee",
                            "Client",
                            "Description",
                            "",
                            "On-Site",
                            "18.5",
                            "1",
                            "09:00 AM",
                            "10:00 AM",
                            "01:00:00",
                            "01:00:00",
                            "60",
                        ),
                    ),
            )

        val first = writer.write(snapshot)
        val second = writer.write(snapshot)
        val packageParts = unzip(first)
        val workbook = parseXml(packageParts.getValue("xl/workbook.xml"))
        val sheet =
            workbook
                .getElementsByTagNameNS(SPREADSHEET_NAMESPACE, "sheet")
                .item(0) as Element

        assertArrayEquals(first, second)
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            packageParts.keys,
        )
        assertEquals("WorqOrder_2026-07-24", sheet.getAttribute("name"))
        assertEquals(
            listOf(ExportSchema.headers, snapshot.rows.single().values),
            readRows(first),
        )
    }

    @Test
    fun literalTextRoundTripsUnicodeMultilineAndFormulaPrefixes() {
        val client = "Café 東京 😀, \"North\""
        val description = "=SUM(A1:A2)\r\nSecond line"
        val purchases = "_x000D_ +Pro\nSuite"
        val bytes =
            writer.write(
                snapshot(
                    rows =
                        listOf(
                            row(
                                "07/24/2026",
                                "07/24/2026",
                                "Employee",
                                client,
                                description,
                                purchases,
                                "In-Office",
                                "",
                                "1",
                                "09:00 AM",
                                "10:00 AM",
                                "01:00:00",
                                "25:00:00",
                                "1500",
                            ),
                        ),
                ),
            )
        val worksheetXml =
            unzip(bytes)
                .getValue("xl/worksheets/sheet1.xml")
                .toString(Charsets.UTF_8)

        assertEquals(client, readRows(bytes)[1][3])
        assertEquals(description, readRows(bytes)[1][4])
        assertEquals(purchases, readRows(bytes)[1][5])
        assertFalse(worksheetXml.contains("<f"))
        assertTrue(worksheetXml.contains("=SUM(A1:A2)"))
        assertTrue(worksheetXml.contains("_x000D_"))
        assertTrue(worksheetXml.contains("_x005F_x000D_"))
    }

    @Test
    fun emptyDateContainsHeaderOnlyAndNoUnsafePackageParts() {
        val bytes = writer.write(snapshot(rows = emptyList()))
        val parts = unzip(bytes)
        val allXml =
            parts.values.joinToString("\n") { it.toString(Charsets.UTF_8) }

        assertEquals(listOf(ExportSchema.headers), readRows(bytes))
        assertFalse(parts.keys.any { it.contains("vba", ignoreCase = true) })
        assertFalse(parts.keys.any { it.contains("externalLink", ignoreCase = true) })
        assertFalse(parts.keys.any { it.contains("customXml", ignoreCase = true) })
        assertFalse(FORMULA_ELEMENT_PATTERN.containsMatchIn(allXml))
        assertFalse(allXml.contains("TargetMode=\"External\""))
    }

    @Test
    fun coordinatorUsesSharedSnapshotAndOneOffDocumentContract() =
        runTest {
            val snapshot =
                snapshot(
                    rows =
                        listOf(
                            row(
                                "07/24/2026",
                                "07/24/2026",
                                "",
                                "Client",
                                "Task",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "00:00:00",
                                "0",
                            ),
                        ),
                )
            val coordinator =
                XlsxExportCoordinator(
                    snapshotCoordinator = {
                        PrepareExportSnapshotResult.Ready(snapshot)
                    },
                )

            val result = coordinator.prepare(WORK_DATE)

            assertTrue(result is PrepareXlsxExportResult.Ready)
            val prepared = (result as PrepareXlsxExportResult.Ready).export
            assertEquals("worqorder_2026-07-24.xlsx", prepared.suggestedFileName)
            assertEquals(XlsxExportCoordinator.MIME_TYPE, prepared.mimeType)
            assertEquals(1, prepared.dataRowCount)
            assertEquals(
                listOf(ExportSchema.headers, snapshot.rows.single().values),
                readRows(prepared.contents),
            )
        }

    private fun snapshot(rows: List<ExportRow>): ExportSnapshot =
        ExportSnapshot(
            schemaVersion = ExportSchema.VERSION,
            workDate = WORK_DATE,
            exportedAt = Instant.parse("2026-07-24T20:00:00Z"),
            rows = rows,
        )

    private fun row(vararg values: String): ExportRow =
        ExportRow(values.toList())

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> =
        buildMap {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(!name.startsWith("/") && ".." !in name.split('/'))
                    put(name, zip.readBytes())
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

    /**
     * Test-only reader intentionally parses the produced package independently from the writer.
     */
    private fun readRows(bytes: ByteArray): List<List<String>> {
        val sheet = parseXml(unzip(bytes).getValue("xl/worksheets/sheet1.xml"))
        val rows = sheet.getElementsByTagNameNS(SPREADSHEET_NAMESPACE, "row")
        return (0 until rows.length).map { rowIndex ->
            val row = rows.item(rowIndex) as Element
            val cells = row.getElementsByTagNameNS(SPREADSHEET_NAMESPACE, "c")
            (0 until cells.length).map { cellIndex ->
                val cell = cells.item(cellIndex) as Element
                val text =
                    cell
                        .getElementsByTagNameNS(SPREADSHEET_NAMESPACE, "t")
                        .item(0)
                        .textContent
                decodeSpreadsheetEscapes(text)
            }
        }
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true,
                )
                setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false,
                )
                setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false,
                )
                setAttribute(
                    "http://javax.xml.XMLConstants/property/accessExternalDTD",
                    "",
                )
                setAttribute(
                    "http://javax.xml.XMLConstants/property/accessExternalSchema",
                    "",
                )
            }.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun decodeSpreadsheetEscapes(value: String): String =
        SPREADSHEET_ESCAPE_PATTERN.replace(value) { match ->
            match.groupValues[1]
                .toInt(radix = 16)
                .toChar()
                .toString()
        }

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 24)
        const val SPREADSHEET_NAMESPACE =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val SPREADSHEET_ESCAPE_PATTERN =
            Regex("""_x([0-9A-Fa-f]{4})_""")
        val FORMULA_ELEMENT_PATTERN =
            Regex("""<f(?:\s|>)""")
    }
}
