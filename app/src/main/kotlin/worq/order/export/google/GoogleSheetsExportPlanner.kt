package worq.order.export.google

import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

internal object GoogleSheetsExportPlanner {
    const val APPLICATION_MARKER_KEY = "worqorder_export_marker"
    const val APPLICATION_MARKER_VALUE = "WORQORDER_EXPORT"
    const val SCHEMA_VERSION_KEY = "worqorder_export_schema"
    const val WORK_DATE_KEY = "worqorder_export_work_date"

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
        val rows =
            buildList {
                add(ExportSchema.headers)
                snapshot.rows.forEach { add(it.values) }
            }
        val requiredRows = rows.size
        val existing = spreadsheet.sheets.singleOrNull { it.title == tabName }
        if (existing == null) {
            val reusableSheet =
                spreadsheet.sheets.firstOrNull()
                    ?.takeIf { spreadsheet.isCompletelyBlank }
            if (reusableSheet != null) {
                return GoogleSheetsPlanResult.Ready(
                    GoogleSheetsBatchPlan(
                        tabName = tabName,
                        requests =
                            listOf(
                                GoogleSheetsBatchRequest.RenameSheet(
                                    sheetId = reusableSheet.sheetId,
                                    title = tabName,
                                ),
                                GoogleSheetsBatchRequest.ResizeSheet(
                                    sheetId = reusableSheet.sheetId,
                                    rowCount = requiredRows,
                                    columnCount = ExportSchema.headers.size,
                                ),
                                GoogleSheetsBatchRequest.CreateSheetMetadata(
                                    sheetId = reusableSheet.sheetId,
                                    key = APPLICATION_MARKER_KEY,
                                    value = APPLICATION_MARKER_VALUE,
                                ),
                                GoogleSheetsBatchRequest.CreateSheetMetadata(
                                    sheetId = reusableSheet.sheetId,
                                    key = SCHEMA_VERSION_KEY,
                                    value = snapshot.schemaVersion.toString(),
                                ),
                                GoogleSheetsBatchRequest.CreateSheetMetadata(
                                    sheetId = reusableSheet.sheetId,
                                    key = WORK_DATE_KEY,
                                    value = snapshot.workDate.toString(),
                                ),
                                GoogleSheetsBatchRequest.ReplaceCells(
                                    sheetId = reusableSheet.sheetId,
                                    rows = rows,
                                ),
                            ),
                    ),
                )
            }
            val sheetId = allocateSheetId(spreadsheet.sheets, tabName)
            return GoogleSheetsPlanResult.Ready(
                GoogleSheetsBatchPlan(
                    tabName = tabName,
                    requests =
                        listOf(
                            GoogleSheetsBatchRequest.AddSheet(
                                sheetId = sheetId,
                                title = tabName,
                                rowCount = requiredRows,
                                columnCount = ExportSchema.headers.size,
                            ),
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId = sheetId,
                                key = APPLICATION_MARKER_KEY,
                                value = APPLICATION_MARKER_VALUE,
                            ),
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId = sheetId,
                                key = SCHEMA_VERSION_KEY,
                                value = snapshot.schemaVersion.toString(),
                            ),
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId = sheetId,
                                key = WORK_DATE_KEY,
                                value = snapshot.workDate.toString(),
                            ),
                            GoogleSheetsBatchRequest.ReplaceCells(
                                sheetId = sheetId,
                                rows = rows,
                            ),
                        ),
                ),
            )
        }

        val metadata =
            spreadsheet.developerMetadata.filter {
                it.sheetId == existing.sheetId
            }
        val markerValues =
            metadata
                .filter { it.key == APPLICATION_MARKER_KEY }
                .map { it.value }
        if (markerValues != listOf(APPLICATION_MARKER_VALUE)) {
            return GoogleSheetsPlanResult.TabNameConflict(tabName)
        }
        val schemaEntries = metadata.filter { it.key == SCHEMA_VERSION_KEY }
        val dateValues =
            metadata
                .filter { it.key == WORK_DATE_KEY }
                .map { it.value }
        if (
            schemaEntries.size != 1 || dateValues != listOf(snapshot.workDate.toString())
        ) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        val schemaEntry = schemaEntries.single()
        val schemaUpgradeRequest =
            when (schemaEntry.value) {
                snapshot.schemaVersion.toString() -> null
                in LEGACY_SCHEMA_VERSIONS -> {
                    val metadataId =
                        schemaEntry.metadataId
                            ?: return GoogleSheetsPlanResult.SchemaConflict(tabName)
                    GoogleSheetsBatchRequest.UpdateSheetMetadataValue(
                        metadataId = metadataId,
                        value = snapshot.schemaVersion.toString(),
                    )
                }
                else -> return GoogleSheetsPlanResult.SchemaConflict(tabName)
            }
        return GoogleSheetsPlanResult.Ready(
            GoogleSheetsBatchPlan(
                tabName = tabName,
                requests =
                    buildList {
                        schemaUpgradeRequest?.let(::add)
                        add(
                            GoogleSheetsBatchRequest.ResizeSheet(
                                sheetId = existing.sheetId,
                                rowCount = requiredRows,
                                columnCount = ExportSchema.headers.size,
                            ),
                        )
                        add(
                            GoogleSheetsBatchRequest.ReplaceCells(
                                sheetId = existing.sheetId,
                                rows = rows,
                            ),
                        )
                    },
            ),
        )
    }

    fun tabName(workDate: String): String = "WorqOrder_$workDate"

    private fun allocateSheetId(
        sheets: List<GoogleSheetDescriptor>,
        tabName: String,
    ): Int {
        val used = sheets.mapTo(mutableSetOf()) { it.sheetId }
        var candidate = tabName.hashCode() and Int.MAX_VALUE
        while (candidate in used) {
            candidate = if (candidate == Int.MAX_VALUE) 0 else candidate + 1
        }
        return candidate
    }

    private val LEGACY_SCHEMA_VERSIONS = setOf("2", "3", "4")
}
