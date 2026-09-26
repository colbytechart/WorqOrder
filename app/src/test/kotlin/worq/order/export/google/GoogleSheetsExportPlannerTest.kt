package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ExportOriginState
import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

class GoogleSheetsExportPlannerTest {
    @Test
    fun newTabHasFourteenVisibleColumnsAndHiddenStableTaskIds() {
        val plan = ready(
            plan(
                GoogleSpreadsheetStructure(SPREADSHEET_ID, emptyList(), emptyList()),
                snapshot(row("task-a", "Alpha")),
            ),
        )
        val add = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AddSheet>().single()
        assertEquals(16, add.columnCount)
        assertEquals(2, add.rowCount)
        val cells = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>().single()
        assertEquals(ExportSchema.headers, cells.rows.first().take(14))
        assertEquals("WORQORDER_TASK_ID", cells.rows.first()[15])
        assertEquals("worqorder.task.v2:$ORIGIN_ID:task-a", cells.rows[1][15])
        assertEquals("Alpha", cells.rows[1][4])
        assertEquals(GoogleSheetsBatchRequest.HideColumns(add.sheetId, 14, 16), plan.requests.last())
    }

    @Test
    fun blankSpreadsheetReusesItsOriginalTab() {
        val plan = ready(
            plan(
                GoogleSpreadsheetStructure(
                    SPREADSHEET_ID,
                    listOf(GoogleSheetDescriptor(0, "Sheet1")),
                    emptyList(),
                    isCompletelyBlank = true,
                ),
                snapshot(),
            ),
        )
        assertEquals(GoogleSheetsBatchRequest.RenameSheet(0, TAB), plan.requests.first())
        assertEquals(GoogleSheetsBatchRequest.ResizeSheet(0, 1, 16), plan.requests[1])
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AddSheet })
    }

    @Test
    fun populatedSpreadsheetPreservesOtherTabs() {
        val plan = ready(
            plan(
                GoogleSpreadsheetStructure(
                    SPREADSHEET_ID,
                    listOf(GoogleSheetDescriptor(0, "Other data")),
                    emptyList(),
                ),
                snapshot(),
            ),
        )
        assertEquals(1, plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AddSheet>().size)
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.RenameSheet })
    }

    @Test
    fun secondDeviceAppendsWithoutReplacingOrShrinkingFirstDevicesRows() {
        val remote = listOf(header(), physical(row("task-a", "First device")))
        val plan = ready(
            plan(
                ownedStructure(remote),
                snapshot(row("task-b", "Second device")),
            ),
        )
        assertEquals(1, plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>().size)
        assertEquals(
            "worqorder.task.v2:$ORIGIN_ID:task-b",
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>().single().rows.single()[15],
        )
        assertFalse(plan.requests.any {
            it is GoogleSheetsBatchRequest.ResizeSheet || it is GoogleSheetsBatchRequest.ReplaceCells
        })
        assertFalse(plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>().any {
            it.rowIndex == 1
        })
    }

    @Test
    fun repeatedExportUpdatesOnlyTheSameTaskAndNeverAppendsDuplicate() {
        val remote = listOf(
            header(),
            physical(row("task-a", "Original")),
            physical(row("task-b", "Other device")),
        )
        val plan = ready(
            plan(
                ownedStructure(remote),
                snapshot(row("task-a", "Edited")),
            ),
        )
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AppendCells })
        assertEquals(
            GoogleSheetsBatchRequest.WriteCellsAt(27, 1, 0, listOf(row("task-a", "Edited").values)),
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>().single(),
        )
    }

    @Test
    fun originalInstallationAdoptsItsMatchingLegacyV1RowIntoTheV2Namespace() {
        val remote =
            listOf(
                header(),
                row("task-a", "Before").values + listOf("", "worqorder.task.v1:task-a"),
            )

        val plan = ready(plan(ownedStructure(remote), snapshot(row("task-a", "After"))))

        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AppendCells })
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(
                    27,
                    1,
                    0,
                    listOf(row("task-a", "After").values),
                ),
            ),
        )
        assertTrue(
            plan.requests.contains(
                GoogleSheetsBatchRequest.WriteCellsAt(
                    27,
                    1,
                    15,
                    listOf(listOf("worqorder.task.v2:$ORIGIN_ID:task-a")),
                ),
            ),
        )
    }

    @Test
    fun restoredInstallationAppendsWithoutClaimingLegacyForeignOrUnkeyedRows() {
        val foreignOrigin = "fedcba9876543210fedcba9876543210"
        val remote =
            listOf(
                header(),
                row("task-a", "Legacy source").values + listOf("", "worqorder.task.v1:task-a"),
                physical(row("task-b", "Foreign source"), foreignOrigin),
                row("task-c", "Unkeyed source").values,
            )
        val restoredOrigin =
            ExportOriginState(ORIGIN_ID, legacyV1AdoptionAllowed = false)

        val plan =
            ready(
                plan(
                    ownedStructure(remote),
                    snapshot(
                        row("task-a", "Legacy source"),
                        row("task-b", "Foreign source"),
                        row("task-c", "Unkeyed source"),
                    ),
                    restoredOrigin,
                ),
            )

        val appended = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>().single()
        assertEquals(3, appended.rows.size)
        assertEquals(
            listOf(
                "worqorder.task.v2:$ORIGIN_ID:task-a",
                "worqorder.task.v2:$ORIGIN_ID:task-b",
                "worqorder.task.v2:$ORIGIN_ID:task-c",
            ),
            appended.rows.map { it[15] },
        )
        assertFalse(
            plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>()
                .any { it.rowIndex in 1..3 },
        )
    }

    @Test
    fun ambiguousLegacyMatchAndDuplicateRemoteKeysFailWithoutMutation() {
        val old = row("task-a", "Same")
        val ambiguous = ownedStructure(listOf(ExportSchema.headers, old.values, old.values), 13)
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            plan(ambiguous, snapshot(old)),
        )
        val duplicate = ownedStructure(listOf(header(), physical(old), physical(old)))
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            plan(duplicate, snapshot(old)),
        )
    }

    @Test
    fun missingRemoteReadCannotProduceDestructiveFallback() {
        val structure = ownedStructure(listOf(header())).copy(sheetValues = emptyMap())
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            plan(structure, snapshot(row("task-a", "Alpha"))),
        )
    }

    @Test
    fun unexpectedExtraColumnsFailClosedInsteadOfMisplacingTaskIdentity() {
        val structure = ownedStructure(listOf(header()), columnCount = 17)
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            plan(structure, snapshot(row("task-a", "Alpha"))),
        )
    }

    @Test
    fun oldSchemaTabIsProtectedInsteadOfSilentlyDiscardingRows() {
        val structure = ownedStructure(listOf(header())).copy(
            developerMetadata = ownedStructure(listOf(header())).developerMetadata.map {
                if (it.key == GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY) it.copy(value = "4") else it
            },
        )
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            plan(structure, snapshot(row("task-a", "Alpha"))),
        )
    }

    @Test
    fun unownedTabAndWrongDateAreRejected() {
        val unowned = ownedStructure(listOf(header())).copy(developerMetadata = emptyList())
        assertEquals(GoogleSheetsPlanResult.TabNameConflict(TAB), plan(unowned, snapshot()))
        val wrongDate = ownedStructure(listOf(header())).copy(
            developerMetadata = ownedStructure(listOf(header())).developerMetadata.map {
                if (it.key == GoogleSheetsExportPlanner.WORK_DATE_KEY) it.copy(value = "2026-07-23") else it
            },
        )
        assertEquals(GoogleSheetsPlanResult.SchemaConflict(TAB), plan(wrongDate, snapshot()))
    }

    @Test
    fun emptyDateRetainsPreviousRowsOnOwnedTab() {
        val remote = listOf(header(), physical(row("task-a", "Prior")))
        val plan = ready(plan(ownedStructure(remote), snapshot()))
        assertFalse(plan.requests.any {
            it is GoogleSheetsBatchRequest.AppendCells ||
                it is GoogleSheetsBatchRequest.ResizeSheet ||
                it is GoogleSheetsBatchRequest.ReplaceCells
        })
    }

    private fun ready(result: GoogleSheetsPlanResult) = (result as GoogleSheetsPlanResult.Ready).plan

    private fun plan(
        structure: GoogleSpreadsheetStructure,
        snapshot: ExportSnapshot,
        exportOrigin: ExportOriginState = ExportOriginState(ORIGIN_ID, legacyV1AdoptionAllowed = true),
    ): GoogleSheetsPlanResult =
        GoogleSheetsExportPlanner.plan(structure, snapshot, exportOrigin)

    private fun ownedStructure(
        rows: List<List<String>>,
        columnCount: Int = 16,
    ): GoogleSpreadsheetStructure =
        GoogleSpreadsheetStructure(
            spreadsheetId = SPREADSHEET_ID,
            sheets = listOf(GoogleSheetDescriptor(27, TAB, columnCount)),
            developerMetadata = listOf(
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY, GoogleSheetsExportPlanner.APPLICATION_MARKER_VALUE),
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY, "6"),
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.WORK_DATE_KEY, "2026-07-24"),
            ),
            sheetValues = mapOf(27 to rows),
        )

    private fun snapshot(vararg rows: ExportRow) =
        ExportSnapshot(6, LocalDate.of(2026, 7, 24), Instant.parse("2026-07-24T18:00:00Z"), rows.toList())

    private fun row(taskId: String, description: String): ExportRow =
        ExportRow(
            values = listOf(
                "07/24/2026", "07/24/2026", "Consultant", "Client", description,
                "Expense", "On-Site", "Billable", "12.5", "09:00 AM", "10:00 AM", "01:00:00", "60", "",
            ),
            sourceTaskId = taskId,
        )

    private fun header() = ExportSchema.headers + listOf("", "WORQORDER_TASK_ID")

    private fun physical(
        row: ExportRow,
        originId: String = ORIGIN_ID,
    ) = row.values + listOf("", "worqorder.task.v2:$originId:${row.sourceTaskId}")

    private companion object {
        const val SPREADSHEET_ID = "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
        const val TAB = "WorqOrder_2026-07-24"
        const val ORIGIN_ID = "0123456789abcdef0123456789abcdef"
    }
}
