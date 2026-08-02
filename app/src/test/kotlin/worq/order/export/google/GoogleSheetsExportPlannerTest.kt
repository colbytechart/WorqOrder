package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

class GoogleSheetsExportPlannerTest {
    @Test
    fun missingDateTabCreatesMarkedRightSizedSheetAndCanonicalTable() {
        val snapshot =
            snapshot(
                rows =
                    listOf(
                        row(
                            "07/24/2026",
                            "07/24/2026",
                            "Employee",
                            "Client",
                            "=Literal description",
                            "Laptop",
                            "On-Site",
                            "12.5",
                            "1",
                            "09:00",
                            "10:00",
                            "01:00:00",
                            "01:00:00",
                            "60",
                        ),
                    ),
            )

        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = SPREADSHEET_ID,
                        sheets =
                            listOf(
                                GoogleSheetDescriptor(
                                    sheetId = 0,
                                    title = "Existing",
                                ),
                            ),
                        developerMetadata = emptyList(),
                    ),
                snapshot = snapshot,
            ) as GoogleSheetsPlanResult.Ready

        val requests = result.plan.requests
        assertEquals("WorqOrder_2026-07-24", result.plan.tabName)
        val add = requests[0] as GoogleSheetsBatchRequest.AddSheet
        assertEquals(2, add.rowCount)
        assertEquals(14, add.columnCount)
        assertEquals(result.plan.tabName, add.title)
        assertEquals(
            setOf(
                GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY to
                    GoogleSheetsExportPlanner.APPLICATION_MARKER_VALUE,
                GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY to "3",
                GoogleSheetsExportPlanner.WORK_DATE_KEY to "2026-07-24",
            ),
            requests
                .filterIsInstance<
                    GoogleSheetsBatchRequest.CreateSheetMetadata,
                >().map { it.key to it.value }
                .toSet(),
        )
        val cells =
            requests.last() as GoogleSheetsBatchRequest.ReplaceCells
        assertEquals(ExportSchema.headers, cells.rows.first())
        assertEquals(snapshot.rows.single().values, cells.rows[1])
        assertEquals("=Literal description", cells.rows[1][4])
    }

    @Test
    fun completelyBlankSpreadsheetReusesAndRenamesOriginalFirstSheet() {
        val originalSheetId = 0
        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = SPREADSHEET_ID,
                        sheets =
                            listOf(
                                GoogleSheetDescriptor(
                                    sheetId = originalSheetId,
                                    title = "Sheet1",
                                ),
                            ),
                        developerMetadata = emptyList(),
                        isCompletelyBlank = true,
                    ),
                snapshot = snapshot(),
            ) as GoogleSheetsPlanResult.Ready

        assertEquals(
            GoogleSheetsBatchRequest.RenameSheet(
                sheetId = originalSheetId,
                title = "WorqOrder_2026-07-24",
            ),
            result.plan.requests[0],
        )
        assertEquals(
            GoogleSheetsBatchRequest.ResizeSheet(
                sheetId = originalSheetId,
                rowCount = 1,
                columnCount = 14,
            ),
            result.plan.requests[1],
        )
        assertEquals(
            3,
            result.plan.requests
                .filterIsInstance<
                    GoogleSheetsBatchRequest.CreateSheetMetadata,
                >().size,
        )
        assertTrue(
            result.plan.requests.none {
                it is GoogleSheetsBatchRequest.AddSheet
            },
        )
    }

    @Test
    fun populatedSpreadsheetPreservesOriginalAndAddsDateSheet() {
        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = SPREADSHEET_ID,
                        sheets =
                            listOf(
                                GoogleSheetDescriptor(
                                    sheetId = 0,
                                    title = "Existing data",
                                ),
                            ),
                        developerMetadata = emptyList(),
                        isCompletelyBlank = false,
                    ),
                snapshot = snapshot(),
            ) as GoogleSheetsPlanResult.Ready

        assertEquals(
            1,
            result.plan.requests
                .filterIsInstance<GoogleSheetsBatchRequest.AddSheet>()
                .size,
        )
        assertTrue(
            result.plan.requests.none {
                it is GoogleSheetsBatchRequest.RenameSheet
            },
        )
    }

    @Test
    fun ownedDateTabIsResizedAndCompletelyReplaced() {
        val sheetId = 27
        val structure = ownedStructure(sheetId)

        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet = structure,
                snapshot =
                    snapshot(
                        rows =
                            listOf(
                                row(
                                    "07/24/2026",
                                    "07/24/2026",
                                    "",
                                    "Client",
                                    "Updated",
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
                    ),
            ) as GoogleSheetsPlanResult.Ready

        assertEquals(2, result.plan.requests.size)
        assertEquals(
            GoogleSheetsBatchRequest.ResizeSheet(
                sheetId = sheetId,
                rowCount = 2,
                columnCount = 14,
            ),
            result.plan.requests[0],
        )
        val replace =
            result.plan.requests[1] as
                GoogleSheetsBatchRequest.ReplaceCells
        assertEquals(2, replace.rows.size)
        assertEquals("Updated", replace.rows[1][4])
        assertTrue(
            result.plan.requests.none {
                it is GoogleSheetsBatchRequest.AddSheet
            },
        )
    }

    @Test
    fun reExportPlanReplacesInsteadOfAppendingOrDuplicating() {
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
                            "1",
                            "09:00",
                            "10:00",
                            "01:00:00",
                            "01:00:00",
                            "60",
                        ),
                    ),
            )

        val first =
            GoogleSheetsExportPlanner.plan(
                ownedStructure(sheetId = 12),
                snapshot,
            ) as GoogleSheetsPlanResult.Ready
        val second =
            GoogleSheetsExportPlanner.plan(
                ownedStructure(sheetId = 12),
                snapshot,
            ) as GoogleSheetsPlanResult.Ready

        assertEquals(first.plan, second.plan)
        val replace =
            second.plan.requests
                .filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>()
                .single()
        assertEquals(2, replace.rows.size)
        assertEquals(1, replace.rows.drop(1).size)
    }

    @Test
    fun sameNamedUnownedTabIsAConflictAndProducesNoRequests() {
        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = SPREADSHEET_ID,
                        sheets =
                            listOf(
                                GoogleSheetDescriptor(
                                    sheetId = 4,
                                    title = "WorqOrder_2026-07-24",
                                ),
                            ),
                        developerMetadata = emptyList(),
                    ),
                snapshot = snapshot(),
            )

        assertEquals(
            GoogleSheetsPlanResult.TabNameConflict(
                "WorqOrder_2026-07-24",
            ),
            result,
        )
    }

    @Test
    fun incompatibleSchemaOrDateIsAConflict() {
        val base = ownedStructure(sheetId = 9)
        val wrongSchema =
            base.copy(
                developerMetadata =
                    base.developerMetadata.map {
                        if (
                            it.key ==
                            GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY
                        ) {
                            it.copy(value = "999")
                        } else {
                            it
                        }
                    },
            )

        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(
                "WorqOrder_2026-07-24",
            ),
            GoogleSheetsExportPlanner.plan(
                wrongSchema,
                snapshot(),
            ),
        )
    }

    @Test
    fun knownOwnedSchemaTwoTabUpgradesMetadataAndReplacesAtomically() {
        val sheetId = 44
        val schemaMetadataId = 704
        val base = ownedStructure(sheetId)
        val legacy =
            base.copy(
                developerMetadata =
                    base.developerMetadata.map { metadata ->
                        if (metadata.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) {
                            metadata.copy(value = "2", metadataId = schemaMetadataId)
                        } else {
                            metadata
                        }
                    },
            )

        val result =
            GoogleSheetsExportPlanner.plan(legacy, snapshot()) as GoogleSheetsPlanResult.Ready

        assertEquals(
            GoogleSheetsBatchRequest.UpdateSheetMetadataValue(
                metadataId = schemaMetadataId,
                value = "3",
            ),
            result.plan.requests.first(),
        )
        assertTrue(result.plan.requests[1] is GoogleSheetsBatchRequest.ResizeSheet)
        assertTrue(result.plan.requests[2] is GoogleSheetsBatchRequest.ReplaceCells)
    }

    @Test
    fun emptyDateStillCreatesExactlyOneHeaderRow() {
        val result =
            GoogleSheetsExportPlanner.plan(
                spreadsheet =
                    GoogleSpreadsheetStructure(
                        spreadsheetId = SPREADSHEET_ID,
                        sheets = emptyList(),
                        developerMetadata = emptyList(),
                    ),
                snapshot = snapshot(),
            ) as GoogleSheetsPlanResult.Ready

        val add =
            result.plan.requests
                .filterIsInstance<GoogleSheetsBatchRequest.AddSheet>()
                .single()
        val replace =
            result.plan.requests
                .filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>()
                .single()
        assertEquals(1, add.rowCount)
        assertEquals(listOf(ExportSchema.headers), replace.rows)
    }

    private fun ownedStructure(
        sheetId: Int,
    ): GoogleSpreadsheetStructure =
        GoogleSpreadsheetStructure(
            spreadsheetId = SPREADSHEET_ID,
            sheets =
                listOf(
                    GoogleSheetDescriptor(
                        sheetId = sheetId,
                        title = "WorqOrder_2026-07-24",
                    ),
                ),
            developerMetadata =
                listOf(
                    metadata(
                        sheetId,
                        GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY,
                        GoogleSheetsExportPlanner.APPLICATION_MARKER_VALUE,
                    ),
                    metadata(
                        sheetId,
                        GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY,
                        "3",
                    ),
                    metadata(
                        sheetId,
                        GoogleSheetsExportPlanner.WORK_DATE_KEY,
                        "2026-07-24",
                    ),
                ),
        )

    private fun metadata(
        sheetId: Int,
        key: String,
        value: String,
        metadataId: Int? = null,
    ): GoogleSheetDeveloperMetadata =
        GoogleSheetDeveloperMetadata(
            sheetId = sheetId,
            key = key,
            value = value,
            metadataId = metadataId,
        )

    private fun snapshot(
        rows: List<ExportRow> = emptyList(),
    ): ExportSnapshot =
        ExportSnapshot(
            schemaVersion = ExportSchema.VERSION,
            workDate = LocalDate.of(2026, 7, 24),
            exportedAt = Instant.parse("2026-07-24T18:00:00Z"),
            rows = rows,
        )

    private fun row(vararg values: String): ExportRow =
        ExportRow(values.toList())

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
