package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

class GoogleSheetsExportPlannerTest {
    @Test
    fun newTabHasThirteenVisibleColumnsAndHiddenStableTaskIds() {
        val plan = ready(
            GoogleSheetsExportPlanner.plan(
                GoogleSpreadsheetStructure(SPREADSHEET_ID, emptyList(), emptyList()),
                snapshot(row("task-a", "Alpha")),
            ),
        )
        val add = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AddSheet>().single()
        assertEquals(16, add.columnCount)
        assertEquals(2, add.rowCount)
        val cells = plan.requests.filterIsInstance<GoogleSheetsBatchRequest.ReplaceCells>().single()
        assertEquals(ExportSchema.headers, cells.rows.first().take(13))
        assertEquals("WORQORDER_TASK_ID", cells.rows.first()[15])
        assertEquals("worqorder.task.v1:task-a", cells.rows[1][15])
        assertEquals("Alpha", cells.rows[1][4])
        assertEquals(GoogleSheetsBatchRequest.HideColumns(add.sheetId, 13, 16), plan.requests.last())
    }

    @Test
    fun blankSpreadsheetReusesItsOriginalTab() {
        val plan = ready(
            GoogleSheetsExportPlanner.plan(
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
            GoogleSheetsExportPlanner.plan(
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
            GoogleSheetsExportPlanner.plan(
                ownedStructure(remote),
                snapshot(row("task-b", "Second device")),
            ),
        )
        assertEquals(1, plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>().size)
        assertEquals(
            "worqorder.task.v1:task-b",
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
            GoogleSheetsExportPlanner.plan(
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
    fun preFixExactMatchIsAdoptedButOtherDeviceRowsRemain() {
        val oldRow = row("task-a", "Original")
        val remote = listOf(
            ExportSchema.headers,
            oldRow.values,
            row("other-task", "Other device").values,
        )
        val plan = ready(
            GoogleSheetsExportPlanner.plan(
                ownedStructure(remote, columnCount = 13),
                snapshot(oldRow),
            ),
        )
        assertTrue(plan.requests.contains(GoogleSheetsBatchRequest.SetColumnCount(27, 16)))
        assertTrue(plan.requests.contains(
            GoogleSheetsBatchRequest.WriteCellsAt(27, 1, 15, listOf(listOf("worqorder.task.v1:task-a"))),
        ))
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.AppendCells })
        assertFalse(plan.requests.any { it is GoogleSheetsBatchRequest.ResizeSheet })
    }

    @Test
    fun preFixUnmatchedRowIsPreservedAndNewTaskAppended() {
        val remote = listOf(ExportSchema.headers, row("old", "Earlier data").values)
        val plan = ready(
            GoogleSheetsExportPlanner.plan(
                ownedStructure(remote, columnCount = 13),
                snapshot(row("new", "New data")),
            ),
        )
        assertEquals(1, plan.requests.filterIsInstance<GoogleSheetsBatchRequest.AppendCells>().single().rows.size)
        assertFalse(plan.requests.filterIsInstance<GoogleSheetsBatchRequest.WriteCellsAt>().any { it.rowIndex == 1 })
    }

    @Test
    fun ambiguousLegacyMatchAndDuplicateRemoteKeysFailWithoutMutation() {
        val old = row("task-a", "Same")
        val ambiguous = ownedStructure(listOf(ExportSchema.headers, old.values, old.values), 13)
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            GoogleSheetsExportPlanner.plan(ambiguous, snapshot(old)),
        )
        val duplicate = ownedStructure(listOf(header(), physical(old), physical(old)))
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            GoogleSheetsExportPlanner.plan(duplicate, snapshot(old)),
        )
    }

    @Test
    fun missingRemoteReadCannotProduceDestructiveFallback() {
        val structure = ownedStructure(listOf(header())).copy(sheetValues = emptyMap())
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            GoogleSheetsExportPlanner.plan(structure, snapshot(row("task-a", "Alpha"))),
        )
    }

    @Test
    fun unexpectedExtraColumnsFailClosedInsteadOfMisplacingTaskIdentity() {
        val structure = ownedStructure(listOf(header()), columnCount = 17)
        assertEquals(
            GoogleSheetsPlanResult.SchemaConflict(TAB),
            GoogleSheetsExportPlanner.plan(structure, snapshot(row("task-a", "Alpha"))),
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
            GoogleSheetsExportPlanner.plan(structure, snapshot(row("task-a", "Alpha"))),
        )
    }

    @Test
    fun unownedTabAndWrongDateAreRejected() {
        val unowned = ownedStructure(listOf(header())).copy(developerMetadata = emptyList())
        assertEquals(GoogleSheetsPlanResult.TabNameConflict(TAB), GoogleSheetsExportPlanner.plan(unowned, snapshot()))
        val wrongDate = ownedStructure(listOf(header())).copy(
            developerMetadata = ownedStructure(listOf(header())).developerMetadata.map {
                if (it.key == GoogleSheetsExportPlanner.WORK_DATE_KEY) it.copy(value = "2026-07-23") else it
            },
        )
        assertEquals(GoogleSheetsPlanResult.SchemaConflict(TAB), GoogleSheetsExportPlanner.plan(wrongDate, snapshot()))
    }

    @Test
    fun emptyDateRetainsPreviousRowsOnOwnedTab() {
        val remote = listOf(header(), physical(row("task-a", "Prior")))
        val plan = ready(GoogleSheetsExportPlanner.plan(ownedStructure(remote), snapshot()))
        assertFalse(plan.requests.any {
            it is GoogleSheetsBatchRequest.AppendCells ||
                it is GoogleSheetsBatchRequest.ResizeSheet ||
                it is GoogleSheetsBatchRequest.ReplaceCells
        })
    }

    private fun ready(result: GoogleSheetsPlanResult) = (result as GoogleSheetsPlanResult.Ready).plan

    private fun ownedStructure(
        rows: List<List<String>>,
        columnCount: Int = 16,
    ): GoogleSpreadsheetStructure =
        GoogleSpreadsheetStructure(
            spreadsheetId = SPREADSHEET_ID,
            sheets = listOf(GoogleSheetDescriptor(27, TAB, columnCount)),
            developerMetadata = listOf(
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY, GoogleSheetsExportPlanner.APPLICATION_MARKER_VALUE),
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.SCHEMA_VERSION_KEY, "5"),
                GoogleSheetDeveloperMetadata(27, GoogleSheetsExportPlanner.WORK_DATE_KEY, "2026-07-24"),
            ),
            sheetValues = mapOf(27 to rows),
        )

    private fun snapshot(vararg rows: ExportRow) =
        ExportSnapshot(5, LocalDate.of(2026, 7, 24), Instant.parse("2026-07-24T18:00:00Z"), rows.toList())

    private fun row(taskId: String, description: String): ExportRow =
        ExportRow(
            values = listOf(
                "07/24/2026", "07/24/2026", "Consultant", "Client", description,
                "Expense", "On-Site", "Billable", "12.5", "09:00 AM", "10:00 AM", "01:00:00", "60",
            ),
            sourceTaskId = taskId,
        )

    private fun header() = ExportSchema.headers + listOf("", "", "WORQORDER_TASK_ID")

    private fun physical(row: ExportRow) = row.values + listOf("", "", "worqorder.task.v1:${row.sourceTaskId}")

    private companion object {
        const val SPREADSHEET_ID = "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
        const val TAB = "WorqOrder_2026-07-24"
    }
}
