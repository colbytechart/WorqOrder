package worq.order.export.google

import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

/** Plans a non-destructive merge. Other devices' rows are never inferred from the local snapshot. */
internal object GoogleSheetsExportPlanner {
    const val APPLICATION_MARKER_KEY = "worqorder_export_marker"
    const val APPLICATION_MARKER_VALUE = "WORQORDER_EXPORT"
    const val SCHEMA_VERSION_KEY = "worqorder_export_schema"
    const val WORK_DATE_KEY = "worqorder_export_work_date"

    private const val IDENTITY_PREFIX = "worqorder.task.v1:"
    private const val IDENTITY_HEADER = "WORQORDER_TASK_ID"
    private const val IDENTITY_COLUMN = 15 // P; N and O are reserved/hidden for older schemas.
    private const val PHYSICAL_COLUMNS = 16

    fun plan(
        spreadsheet: GoogleSpreadsheetStructure,
        snapshot: ExportSnapshot,
    ): GoogleSheetsPlanResult {
        require(spreadsheet.spreadsheetId.isNotBlank()) {
            "Spreadsheet ID must not be blank"
        }
        require(snapshot.schemaVersion == ExportSchema.VERSION) {
            "Unsupported local export schema ${snapshot.schemaVersion}"
        }
        val tabName = tabName(snapshot.workDate.toString())
        val identities = snapshot.rows.map { it.identityOrNull() }
        if (identities.any { it == null } || identities.distinct().size != identities.size) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }

        val existing = spreadsheet.sheets.singleOrNull { it.title == tabName }
        if (existing == null) {
            val rows = listOf(physicalHeader()) + snapshot.rows.map { it.physicalRow() }
            val reusable = spreadsheet.sheets.firstOrNull()?.takeIf { spreadsheet.isCompletelyBlank }
            val sheetId = reusable?.sheetId ?: allocateSheetId(spreadsheet.sheets, tabName)
            return GoogleSheetsPlanResult.Ready(
                GoogleSheetsBatchPlan(
                    tabName = tabName,
                    requests = buildList {
                        if (reusable == null) {
                            add(GoogleSheetsBatchRequest.AddSheet(sheetId, tabName, rows.size, PHYSICAL_COLUMNS))
                        } else {
                            add(GoogleSheetsBatchRequest.RenameSheet(sheetId, tabName))
                            add(GoogleSheetsBatchRequest.ResizeSheet(sheetId, rows.size, PHYSICAL_COLUMNS))
                        }
                        add(GoogleSheetsBatchRequest.CreateSheetMetadata(sheetId, APPLICATION_MARKER_KEY, APPLICATION_MARKER_VALUE))
                        add(GoogleSheetsBatchRequest.CreateSheetMetadata(sheetId, SCHEMA_VERSION_KEY, snapshot.schemaVersion.toString()))
                        add(GoogleSheetsBatchRequest.CreateSheetMetadata(sheetId, WORK_DATE_KEY, snapshot.workDate.toString()))
                        add(GoogleSheetsBatchRequest.ReplaceCells(sheetId, rows))
                        add(GoogleSheetsBatchRequest.HideColumns(sheetId, ExportSchema.headers.size, PHYSICAL_COLUMNS))
                    },
                ),
            )
        }

        val metadata = spreadsheet.developerMetadata.filter { it.sheetId == existing.sheetId }
        if (metadata.filter { it.key == APPLICATION_MARKER_KEY }.map { it.value } != listOf(APPLICATION_MARKER_VALUE)) {
            return GoogleSheetsPlanResult.TabNameConflict(tabName)
        }
        if (
            metadata.filter { it.key == SCHEMA_VERSION_KEY }.map { it.value } != listOf(snapshot.schemaVersion.toString()) ||
            metadata.filter { it.key == WORK_DATE_KEY }.map { it.value } != listOf(snapshot.workDate.toString())
        ) {
            // Old schema tabs cannot safely be replaced: they may contain rows from another device.
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        if (existing.columnCount < ExportSchema.headers.size || existing.columnCount > PHYSICAL_COLUMNS) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }

        val remoteRows = spreadsheet.sheetValues[existing.sheetId]
            ?: return GoogleSheetsPlanResult.SchemaConflict(tabName)
        if (remoteRows.isNotEmpty()) {
            val header = remoteRows.first()
            if (header.take(ExportSchema.headers.size) != ExportSchema.headers ||
                (header.size > IDENTITY_COLUMN && header[IDENTITY_COLUMN].isNotEmpty() &&
                    header[IDENTITY_COLUMN] != IDENTITY_HEADER)
            ) {
                return GoogleSheetsPlanResult.SchemaConflict(tabName)
            }
        }

        val keyedRows = mutableMapOf<String, Int>()
        val unkeyedRows = mutableListOf<Pair<Int, List<String>>>()
        remoteRows.drop(1).forEachIndexed { offset, row ->
            val rowIndex = offset + 1
            val key = row.getOrNull(IDENTITY_COLUMN).orEmpty()
            if (key.isNotEmpty()) {
                if (!key.startsWith(IDENTITY_PREFIX) || keyedRows.put(key, rowIndex) != null) {
                    return GoogleSheetsPlanResult.SchemaConflict(tabName)
                }
            } else if (row.any { it.isNotEmpty() }) {
                unkeyedRows += rowIndex to row
            }
        }

        val requests = mutableListOf<GoogleSheetsBatchRequest>()
        if (existing.columnCount < PHYSICAL_COLUMNS) {
            requests += GoogleSheetsBatchRequest.SetColumnCount(existing.sheetId, PHYSICAL_COLUMNS)
        }
        requests += GoogleSheetsBatchRequest.HideColumns(existing.sheetId, ExportSchema.headers.size, PHYSICAL_COLUMNS)
        if (remoteRows.isEmpty()) {
            requests += GoogleSheetsBatchRequest.WriteCellsAt(existing.sheetId, 0, 0, listOf(physicalHeader()))
        } else if (remoteRows.first().getOrNull(IDENTITY_COLUMN) != IDENTITY_HEADER) {
            requests += GoogleSheetsBatchRequest.WriteCellsAt(existing.sheetId, 0, IDENTITY_COLUMN, listOf(listOf(IDENTITY_HEADER)))
        }

        val appendRows = mutableListOf<List<String>>()
        for (source in snapshot.rows) {
            val identity = requireNotNull(source.identityOrNull())
            val existingIndex = keyedRows[identity]
            if (existingIndex != null) {
                // Only this task's 13 visible cells may change; never replace the whole tab.
                requests += GoogleSheetsBatchRequest.WriteCellsAt(existing.sheetId, existingIndex, 0, listOf(source.values))
                continue
            }
            // A pre-fix row has no ID. Adopt it only when its visible values match uniquely.
            val matches = unkeyedRows.filter { (_, row) -> row.take(ExportSchema.headers.size) == source.values }
            if (matches.size > 1) return GoogleSheetsPlanResult.SchemaConflict(tabName)
            if (matches.size == 1) {
                val match = matches.single()
                unkeyedRows.remove(match)
                requests += GoogleSheetsBatchRequest.WriteCellsAt(existing.sheetId, match.first, IDENTITY_COLUMN, listOf(listOf(identity)))
            } else {
                appendRows += source.physicalRow()
            }
        }
        if (appendRows.isNotEmpty()) {
            requests += GoogleSheetsBatchRequest.AppendCells(existing.sheetId, appendRows)
        }
        return GoogleSheetsPlanResult.Ready(GoogleSheetsBatchPlan(tabName, requests))
    }

    fun tabName(workDate: String): String = "WorqOrder_$workDate"

    private fun ExportRow.identityOrNull(): String? =
        sourceTaskId?.takeIf { it.isNotBlank() }?.let { IDENTITY_PREFIX + it }

    private fun ExportRow.physicalRow(): List<String> =
        values + List(IDENTITY_COLUMN - ExportSchema.headers.size) { "" } + requireNotNull(identityOrNull())

    private fun physicalHeader(): List<String> =
        ExportSchema.headers + List(IDENTITY_COLUMN - ExportSchema.headers.size) { "" } + IDENTITY_HEADER

    private fun allocateSheetId(sheets: List<GoogleSheetDescriptor>, tabName: String): Int {
        val used = sheets.mapTo(mutableSetOf()) { it.sheetId }
        var candidate = tabName.hashCode() and Int.MAX_VALUE
        while (candidate in used) {
            candidate = if (candidate == Int.MAX_VALUE) 0 else candidate + 1
        }
        return candidate
    }
}
