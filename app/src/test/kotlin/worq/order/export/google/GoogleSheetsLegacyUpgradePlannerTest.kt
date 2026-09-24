package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ExportOriginState
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

class GoogleSheetsLegacyUpgradePlannerTest {
    @Test
    fun ownedSchemaFiveUpgradePreservesOtherDeviceRowsAndTransportIds() {
        val sourceOld = values("Before")
        val otherDevice = values("Other device")
        val remote =
            listOf(
                physicalHeader(),
                physicalRow(sourceOld, "task-local"),
                physicalRow(otherDevice, "task-remote"),
            )
        val plan =
            ready(
                planPrepared(
                    ownedLegacy(remote),
                    snapshot(row("task-local", "After", "Updated note"), row("new", "New", "")),
                ),
            )

        assertTrue(plan.requests.contains(GoogleSheetsBatchRequest.ShowColumns(27, 13, 14)))
        assertTrue(plan.requests.contains(GoogleSheetsBatchRequest.HideColumns(27, 14, 16)))
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(27, 0, 13, listOf(listOf("Notes"))),
            ),
        )
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(
                    27,
                    1,
                    0,
                    listOf(values("After") + "Updated note"),
                ),
            ),
        )
        assertFalse(
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>()
                .any { it.rowIndex == 2 },
        )
        val appended =
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>()
                .single().rows.single()
        assertEquals(
            values("New") + listOf("", "", "worqorder.task.v2:$ORIGIN_ID:new"),
            appended,
        )
        assertEquals(
            GoogleSheetsBatchRequest.UpdateSheetMetadataValue(41, "6"),
            plan.requests.last(),
        )
        assertFalse(
            plan.requests.any {
                it is GoogleSheetsBatchRequest.ResizeSheet ||
                    it is GoogleSheetsBatchRequest.ReplaceCells
            },
        )
    }

    @Test
    fun uniqueUnkeyedMatchGainsNotesAndIdentityWithoutRewritingItsOldValues() {
        val legacyRow = values("Unkeyed")
        val plan =
            ready(
                planPrepared(
                    ownedLegacy(listOf(physicalHeader(), legacyRow)),
                    snapshot(row("task-local", "Unkeyed", "New note")),
                ),
            )
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(27, 1, 13, listOf(listOf("New note"))),
            ),
        )
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(
                    27,
                    1,
                    15,
                    listOf(listOf("worqorder.task.v2:$ORIGIN_ID:task-local")),
                ),
            ),
        )
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AppendCells })
        assertFalse(
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>()
                .any { it.rowIndex == 1 && it.columnIndex < 13 },
        )
    }

    @Test
    fun occupiedFormerlyReservedCellsAndMissingMetadataIdFailClosed() {
        for (column in listOf(13, 14)) {
            val remote =
                physicalRow(values("Old"), "task-local").toMutableList()
                    .also { it[column] = "=IF(TRUE,\"\",\"\")" }
            assertEquals(
                GoogleSheetsPlanResult.SchemaConflict(TAB),
                planPrepared(
                    ownedLegacy(listOf(physicalHeader(), remote)),
                    snapshot(row("task-local", "Old", "New note")),
                ),
            )
        }
        val noMetadataId =
            ownedLegacy(listOf(physicalHeader())).copy(
                developerMetadata =
                    ownedLegacy(listOf(physicalHeader())).developerMetadata.map {
                        if (it.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) {
                            it.copy(metadataId = null)
                        } else {
                            it
                        }
                    },
            )
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            planPrepared(noMetadataId, snapshot()),
        )
    }

    @Test
    fun markedLegacyTabWithoutItsCanonicalHeaderFailsClosed() {
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            planPrepared(ownedLegacy(emptyList()), snapshot()),
        )
    }

    @Test
    fun legacyTabWithUnknownPhysicalColumnCountCannotBeUpgraded() {
        val structure =
            ownedLegacy(listOf(physicalHeader())).copy(
                sheets = listOf(GoogleSheetDescriptor(27, TAB, 0)),
            )
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            planPrepared(structure, snapshot()),
        )
    }

    @Test
    fun olderUnknownNewerAndUnownedTabsCannotBeUpgraded() {
        for (version in listOf("4", "7", "unknown")) {
            val structure =
                ownedLegacy(listOf(physicalHeader())).copy(
                    developerMetadata =
                        ownedLegacy(listOf(physicalHeader())).developerMetadata.map {
                            if (it.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) {
                                it.copy(value = version)
                            } else {
                                it
                            }
                        },
                )
            assertEquals(
                GoogleSheetsPlanResult.SchemaConflict(TAB),
                planPrepared(structure, snapshot()),
            )
        }
        val unowned = ownedLegacy(listOf(physicalHeader())).copy(developerMetadata = emptyList())
        assertEquals(
            GoogleSheetsPlanResult.TabNameConflict(TAB),
            planPrepared(unowned, snapshot()),
        )
    }

    @Test
    fun ambiguousLocalIdentityForOneUnkeyedRowFailsClosed() {
        val remote = listOf(physicalHeader(), values("Same"))
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            planPrepared(
                ownedLegacy(remote),
                snapshot(row("first", "Same", "A"), row("second", "Same", "B")),
            ),
        )
    }

    @Test
    fun freshSchemaSixTabShowsNotesAndHidesOnlyReservedColumns() {
        val plan =
            ready(
                planPrepared(
                    GoogleSpreadsheetStructure(SPREADSHEET_ID, emptyList(), emptyList()),
                    snapshot(row("new", "New", "Visible note")),
                ),
            )
        val added = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AddSheet>().single()
        assertEquals(16, added.columnCount)
        val cells = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>().single()
        assertEquals("Notes", cells.rows.first()[13])
        assertEquals("Visible note", cells.rows[1][13])
        assertEquals("", cells.rows[1][14])
        assertEquals("worqorder.task.v2:$ORIGIN_ID:new", cells.rows[1][15])
        assertEquals(GoogleSheetsBatchRequest.HideColumns(added.sheetId, 14, 16), plan.requests.last())
    }

    @Test
    fun secondSchemaSixExportUpdatesItsKeyedRowWithoutDuplicatingOtherDeviceRows() {
        val prior = values("Old") + "Prior note"
        val other = values("Other device") + "Other note"
        val remote =
            listOf(
                snapshot().headers + listOf("", "WORQORDER_TASK_ID"),
                prior + listOf("", "worqorder.task.v2:$ORIGIN_ID:task-local"),
                other + listOf("", "worqorder.task.v2:$ORIGIN_ID:task-remote"),
            )
        val structure =
            ownedLegacy(remote).copy(
                developerMetadata =
                    ownedLegacy(remote).developerMetadata.map {
                        if (it.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) {
                            it.copy(value = "6")
                        } else {
                            it
                        }
                    },
            )
        val plan =
            ready(
                planPrepared(
                    structure,
                    snapshot(row("task-local", "Edited", "Current note")),
                ),
            )
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(
                    27,
                    1,
                    0,
                    listOf(values("Edited") + "Current note"),
                ),
            ),
        )
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AppendCells })
        assertFalse(plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>()
            .any { it.rowIndex == 2 })
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.UpdateSheetMetadataValue })
    }

    @Test
    fun postWriteConfirmationRequiresExactOwnedDateAndSchemaMarker() {
        val exportSnapshot =
            ExportSnapshot(6, LocalDate.of(2026, 7, 24), Instant.EPOCH, emptyList())
        val legacy = ownedLegacy(emptyList())
        val upgraded =
            legacy.copy(
                developerMetadata =
                    legacy.developerMetadata.map {
                        if (it.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) {
                            it.copy(value = "6")
                        } else {
                            it
                        }
                    },
            )
        assertTrue(GoogleSheetsExportPlanner.confirmsOwnedSchema(upgraded, exportSnapshot))
        assertFalse(GoogleSheetsExportPlanner.confirmsOwnedSchema(legacy, exportSnapshot))
        assertFalse(
            GoogleSheetsExportPlanner.confirmsOwnedSchema(
                upgraded.copy(developerMetadata = emptyList()),
                exportSnapshot,
            ),
        )
    }

    private fun ready(result: GoogleSheetsPlanResult): GoogleSheetsBatchPlan =
        (result as GoogleSheetsPlanResult.Ready).plan

    private fun planPrepared(
        spreadsheet: GoogleSpreadsheetStructure,
        snapshot: GooglePlanningSnapshot,
        exportOrigin: ExportOriginState =
            ExportOriginState(ORIGIN_ID, legacyV1AdoptionAllowed = true),
    ): GoogleSheetsPlanResult =
        GoogleSheetsExportPlanner.planPrepared(spreadsheet, snapshot, exportOrigin)

    private fun ownedLegacy(rows: List<List<String>>): GoogleSpreadsheetStructure =
        GoogleSpreadsheetStructure(
            spreadsheetId = SPREADSHEET_ID,
            sheets = listOf(GoogleSheetDescriptor(27, TAB, 16)),
            developerMetadata =
                listOf(
                    GoogleSheetDeveloperMetadata(
                        27,
                        GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY,
                        GoogleSheetsExportPlanner.APPLICATION_MARKER_VALUE,
                        metadataId = 40,
                    ),
                    GoogleSheetDeveloperMetadata(
                        27,
                        GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY,
                        "5",
                        metadataId = 41,
                    ),
                    GoogleSheetDeveloperMetadata(
                        27,
                        GoogleSheetsExportPlanner.WORK_DATE_KEY,
                        "2026-07-24",
                        metadataId = 42,
                    ),
                ),
            sheetValues = mapOf(27 to rows),
        )

    private fun snapshot(vararg rows: GooglePlanningRow): GooglePlanningSnapshot =
        GooglePlanningSnapshot(
            schemaVersion = 6,
            workDate = LocalDate.of(2026, 7, 24),
            headers = ExportSchema.headers.take(13) + "Notes",
            rows = rows.toList(),
        )

    private fun row(taskId: String, description: String, notes: String): GooglePlanningRow =
        GooglePlanningRow(values(description) + notes, taskId)

    private fun values(description: String): List<String> =
        listOf(
            "07/24/2026", "07/24/2026", "Consultant", "Client", description,
            "Expense", "On-Site", "Billable", "12.5", "09:00 AM", "10:00 AM", "01:00:00", "60",
        )

    private fun physicalHeader(): List<String> =
        ExportSchema.headers.take(13) + listOf("", "", "WORQORDER_TASK_ID")

    private fun physicalRow(values: List<String>, taskId: String): List<String> =
        values + listOf("", "", "worqorder.task.v1:$taskId")

    private companion object {
        const val SPREADSHEET_ID = "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
        const val TAB = "WorqOrder_2026-07-24"
        const val ORIGIN_ID = "0123456789abcdef0123456789abcdef"
    }
}
