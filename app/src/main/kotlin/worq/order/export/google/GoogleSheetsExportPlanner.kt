package worq.order.export.google

import java.time.LocalDate
import worq.order.data.ExportOriginState
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

/** A transport view of the already-selected canonical rows; no field selection occurs here. */
internal data class GooglePlanningRow(
    val values: List<String>,
    val sourceTaskId: String?,
)

internal data class GooglePlanningSnapshot(
    val schemaVersion: Int,
    val workDate: LocalDate,
    val headers: List<String>,
    val rows: List<GooglePlanningRow>,
)

/** Plans a non-destructive merge. Other devices' rows are never inferred from the local snapshot. */
internal object GoogleSheetsExportPlanner {
    const val APPLICATION_MARKER_KEY = "worqorder_export_marker"
    const val APPLICATION_MARKER_VALUE = "WORQORDER_EXPORT"
    const val SCHEMA_VERSION_KEY = "worqorder_export_schema"
    const val WORK_DATE_KEY = "worqorder_export_work_date"

    private const val LEGACY_IDENTITY_PREFIX = "worqorder.task.v1:"
    private const val ORIGIN_SCOPED_IDENTITY_PREFIX = "worqorder.task.v2:"
    private const val IDENTITY_HEADER = "WORQORDER_TASK_ID"
    private const val LEGACY_VISIBLE_COLUMNS = 13
    private const val NOTES_COLUMN = 13 // N; previously reserved in schema 5.
    private const val IDENTITY_COLUMN = 15 // P; O remains reserved/hidden.
    private const val PHYSICAL_COLUMNS = 16

    fun plan(
        spreadsheet: GoogleSpreadsheetStructure,
        snapshot: ExportSnapshot,
        exportOrigin: ExportOriginState,
    ): GoogleSheetsPlanResult {
        require(snapshot.schemaVersion == ExportSchema.VERSION) {
            "Unsupported local export schema ${snapshot.schemaVersion}"
        }
        return planPrepared(
            spreadsheet = spreadsheet,
            snapshot =
                GooglePlanningSnapshot(
                    schemaVersion = snapshot.schemaVersion,
                    workDate = snapshot.workDate,
                    headers = ExportSchema.headers,
                    rows = snapshot.rows.map { GooglePlanningRow(it.values, it.sourceTaskId) },
                ),
            exportOrigin = exportOrigin,
        )
    }

    /** Pure entry point for owned legacy-tab and current-schema planner fixtures. */
    internal fun planPrepared(
        spreadsheet: GoogleSpreadsheetStructure,
        snapshot: GooglePlanningSnapshot,
        exportOrigin: ExportOriginState,
    ): GoogleSheetsPlanResult {
        require(spreadsheet.spreadsheetId.isNotBlank()) {
            "Spreadsheet ID must not be blank"
        }
        require(snapshot.schemaVersion in 5..6) {
            "Unsupported local export schema ${snapshot.schemaVersion}"
        }
        val expectedVisibleColumns =
            if (snapshot.schemaVersion == 6) LEGACY_VISIBLE_COLUMNS + 1 else LEGACY_VISIBLE_COLUMNS
        require(snapshot.headers.size == expectedVisibleColumns) {
            "Export header count does not match schema ${snapshot.schemaVersion}"
        }
        require(snapshot.rows.all { it.values.size == snapshot.headers.size }) {
            "Export row count does not match its canonical headers"
        }
        require(ORIGIN_ID.matches(exportOrigin.originId)) {
            "Export origin must be a 32-character lowercase hexadecimal value"
        }
        val tabName = tabName(snapshot.workDate.toString())
        val identities = snapshot.rows.map { it.currentIdentityOrNull(exportOrigin) }
        if (identities.any { it == null } || identities.distinct().size != identities.size) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }

        val matchingSheets = spreadsheet.sheets.filter { it.title == tabName }
        if (matchingSheets.size > 1) return GoogleSheetsPlanResult.SchemaConflict(tabName)
        val existing = matchingSheets.singleOrNull()
        if (existing == null) {
            val rows =
                listOf(physicalHeader(snapshot.headers)) +
                    snapshot.rows.map { it.physicalRow(snapshot.headers.size, exportOrigin) }
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
                        add(
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId,
                                APPLICATION_MARKER_KEY,
                                APPLICATION_MARKER_VALUE,
                            ),
                        )
                        add(
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId,
                                SCHEMA_VERSION_KEY,
                                snapshot.schemaVersion.toString(),
                            ),
                        )
                        add(
                            GoogleSheetsBatchRequest.CreateSheetMetadata(
                                sheetId,
                                WORK_DATE_KEY,
                                snapshot.workDate.toString(),
                            ),
                        )
                        add(GoogleSheetsBatchRequest.ReplaceCells(sheetId, rows))
                        add(GoogleSheetsBatchRequest.HideColumns(sheetId, snapshot.headers.size, PHYSICAL_COLUMNS))
                    },
                ),
            )
        }

        val metadata = spreadsheet.developerMetadata.filter { it.sheetId == existing.sheetId }
        if (metadata.filter { it.key == APPLICATION_MARKER_KEY }.map { it.value } != listOf(APPLICATION_MARKER_VALUE)) {
            return GoogleSheetsPlanResult.TabNameConflict(tabName)
        }
        val schemaMetadata = metadata.filter { it.key == SCHEMA_VERSION_KEY }.singleOrNull()
            ?: return GoogleSheetsPlanResult.SchemaConflict(tabName)
        if (metadata.filter { it.key == WORK_DATE_KEY }.map { it.value } != listOf(snapshot.workDate.toString())) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        val legacyUpgrade = snapshot.schemaVersion == 6 && schemaMetadata.value == "5"
        if (schemaMetadata.value != snapshot.schemaVersion.toString() && !legacyUpgrade) {
            // Schemas 2-4, unknown/newer schemas, and a newer tab than this app fail closed.
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        if (legacyUpgrade && schemaMetadata.metadataId == null) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        val remoteVisibleColumns = if (legacyUpgrade) LEGACY_VISIBLE_COLUMNS else snapshot.headers.size
        if (existing.columnCount < remoteVisibleColumns || existing.columnCount > PHYSICAL_COLUMNS) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }

        val remoteRows = spreadsheet.sheetValues[existing.sheetId]
            ?: return GoogleSheetsPlanResult.SchemaConflict(tabName)
        if (legacyUpgrade && remoteRows.isEmpty()) {
            // A marked legacy tab without its original header cannot be verified as schema 5.
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }
        if (remoteRows.isNotEmpty()) {
            val header = remoteRows.first()
            if (header.take(remoteVisibleColumns) != snapshot.headers.take(remoteVisibleColumns) ||
                (header.size > IDENTITY_COLUMN && header[IDENTITY_COLUMN].isNotEmpty() &&
                    header[IDENTITY_COLUMN] != IDENTITY_HEADER)
            ) {
                return GoogleSheetsPlanResult.SchemaConflict(tabName)
            }
        }
        // N/O were reserved in schema 5, and O remains reserved in schema 6. The gateway reads
        // formulas rather than calculated display values so even a formula returning "" is seen.
        if (remoteRows.any { row ->
                (remoteVisibleColumns until IDENTITY_COLUMN).any { column ->
                    row.getOrNull(column).orEmpty().isNotEmpty()
                }
            }
        ) {
            return GoogleSheetsPlanResult.SchemaConflict(tabName)
        }

        val keyedRows = mutableMapOf<String, Int>()
        val legacyRows = mutableMapOf<String, Int>()
        val unkeyedRows = mutableListOf<Pair<Int, List<String>>>()
        remoteRows.drop(1).forEachIndexed { offset, row ->
            val rowIndex = offset + 1
            val key = row.getOrNull(IDENTITY_COLUMN).orEmpty()
            if (key.isNotEmpty()) {
                val identity = key.remoteIdentityOrNull()
                if (identity == null || keyedRows.put(key, rowIndex) != null) {
                    return GoogleSheetsPlanResult.SchemaConflict(tabName)
                }
                if (identity is RemoteRowIdentity.LegacyV1 &&
                    legacyRows.put(identity.taskId, rowIndex) != null
                ) {
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
        if (legacyUpgrade) {
            requests += GoogleSheetsBatchRequest.ShowColumns(existing.sheetId, NOTES_COLUMN, NOTES_COLUMN + 1)
        }
        requests += GoogleSheetsBatchRequest.HideColumns(existing.sheetId, snapshot.headers.size, PHYSICAL_COLUMNS)
        if (remoteRows.isEmpty()) {
            requests +=
                GoogleSheetsBatchRequest.WriteCellsAt(
                    existing.sheetId,
                    0,
                    0,
                    listOf(physicalHeader(snapshot.headers)),
                )
        } else {
            if (legacyUpgrade) {
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        0,
                        NOTES_COLUMN,
                        listOf(listOf(snapshot.headers[NOTES_COLUMN])),
                    )
            }
            if (remoteRows.first().getOrNull(IDENTITY_COLUMN) != IDENTITY_HEADER) {
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        0,
                        IDENTITY_COLUMN,
                        listOf(listOf(IDENTITY_HEADER)),
                    )
            }
        }

        val unkeyedLocalCounts: Map<List<String>, Int> =
            if (exportOrigin.legacyV1AdoptionAllowed) {
                snapshot.rows
                    .filter { requireNotNull(it.currentIdentityOrNull(exportOrigin)) !in keyedRows }
                    .groupingBy { it.values.take(remoteVisibleColumns) }
                    .eachCount()
            } else {
                emptyMap()
            }
        val appendRows = mutableListOf<List<String>>()
        for (source in snapshot.rows) {
            val identity = requireNotNull(source.currentIdentityOrNull(exportOrigin))
            val existingIndex = keyedRows[identity]
            if (existingIndex != null) {
                // Only this task's canonical visible cells may change; never replace the tab.
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        existingIndex,
                        0,
                        listOf(source.values),
                )
                continue
            }
            val legacyRowIndex =
                if (exportOrigin.legacyV1AdoptionAllowed) {
                    source.sourceTaskIdOrNull()?.let(legacyRows::get)
                } else {
                    null
                }
            if (legacyRowIndex != null) {
                // A legacy v1 key has no origin. Only an installation that predates portable
                // restore is allowed to adopt its own stable local task ID into the v2 namespace.
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        legacyRowIndex,
                        0,
                        listOf(source.values),
                    )
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        legacyRowIndex,
                        IDENTITY_COLUMN,
                        listOf(listOf(identity)),
                    )
                continue
            }
            // A pre-identity row is adopted only on a unique match in both directions.
            val comparableValues = source.values.take(remoteVisibleColumns)
            val matches =
                if (exportOrigin.legacyV1AdoptionAllowed) {
                    unkeyedRows.filter { (_, row) ->
                        row.take(remoteVisibleColumns) == comparableValues
                    }
                } else {
                    emptyList()
                }
            if (matches.size > 1 || (matches.isNotEmpty() && unkeyedLocalCounts[comparableValues] != 1)) {
                return GoogleSheetsPlanResult.SchemaConflict(tabName)
            }
            if (matches.size == 1) {
                val match = matches.single()
                unkeyedRows.remove(match)
                if (legacyUpgrade) {
                    requests +=
                        GoogleSheetsBatchRequest.WriteCellsAt(
                            existing.sheetId,
                            match.first,
                            NOTES_COLUMN,
                            listOf(listOf(source.values[NOTES_COLUMN])),
                        )
                }
                requests +=
                    GoogleSheetsBatchRequest.WriteCellsAt(
                        existing.sheetId,
                        match.first,
                        IDENTITY_COLUMN,
                        listOf(listOf(identity)),
                    )
            } else {
                appendRows += source.physicalRow(snapshot.headers.size, exportOrigin)
            }
        }
        if (appendRows.isNotEmpty()) {
            requests += GoogleSheetsBatchRequest.AppendCells(existing.sheetId, appendRows)
        }
        if (legacyUpgrade) {
            // The marker changes in the same batch as N, row updates, and appends. An ambiguous
            // response is not success; retry then re-reads marker and cells before planning.
            requests += GoogleSheetsBatchRequest.UpdateSheetMetadataValue(
                metadataId = requireNotNull(schemaMetadata.metadataId),
                value = snapshot.schemaVersion.toString(),
            )
        }
        return GoogleSheetsPlanResult.Ready(GoogleSheetsBatchPlan(tabName, requests))
    }

    fun tabName(workDate: String): String = "WorqOrder_$workDate"

    fun confirmsOwnedSchema(
        spreadsheet: GoogleSpreadsheetStructure,
        snapshot: ExportSnapshot,
    ): Boolean {
        val sheet =
            spreadsheet.sheets.singleOrNull { it.title == tabName(snapshot.workDate.toString()) }
                ?: return false
        val metadata = spreadsheet.developerMetadata.filter { it.sheetId == sheet.sheetId }
        return sheet.columnCount >= ExportSchema.headers.size &&
            metadata.filter { it.key == APPLICATION_MARKER_KEY }.map { it.value } ==
            listOf(APPLICATION_MARKER_VALUE) &&
            metadata.filter { it.key == SCHEMA_VERSION_KEY }.map { it.value } ==
            listOf(snapshot.schemaVersion.toString()) &&
            metadata.filter { it.key == WORK_DATE_KEY }.map { it.value } ==
            listOf(snapshot.workDate.toString())
    }

    private fun GooglePlanningRow.sourceTaskIdOrNull(): String? =
        sourceTaskId?.takeIf { it.isNotBlank() && ':' !in it }

    private fun GooglePlanningRow.currentIdentityOrNull(
        exportOrigin: ExportOriginState,
    ): String? =
        sourceTaskIdOrNull()?.let {
            ORIGIN_SCOPED_IDENTITY_PREFIX + exportOrigin.originId + ":" + it
        }

    private fun GooglePlanningRow.physicalRow(
        visibleColumns: Int,
        exportOrigin: ExportOriginState,
    ): List<String> =
        values +
            List(IDENTITY_COLUMN - visibleColumns) { "" } +
            requireNotNull(currentIdentityOrNull(exportOrigin))

    private fun String.remoteIdentityOrNull(): RemoteRowIdentity? =
        when {
            startsWith(LEGACY_IDENTITY_PREFIX) ->
                removePrefix(LEGACY_IDENTITY_PREFIX)
                    .takeIf { it.isNotBlank() && ':' !in it }
                    ?.let(RemoteRowIdentity::LegacyV1)
            startsWith(ORIGIN_SCOPED_IDENTITY_PREFIX) -> {
                val parts = removePrefix(ORIGIN_SCOPED_IDENTITY_PREFIX).split(':')
                if (
                    parts.size == 2 &&
                        ORIGIN_ID.matches(parts[0]) &&
                        parts[1].isNotBlank() &&
                        ':' !in parts[1]
                ) {
                    RemoteRowIdentity.OriginScopedV2(parts[0], parts[1])
                } else {
                    null
                }
            }
            else -> null
        }

    private sealed interface RemoteRowIdentity {
        data class LegacyV1(
            val taskId: String,
        ) : RemoteRowIdentity

        data class OriginScopedV2(
            val originId: String,
            val taskId: String,
        ) : RemoteRowIdentity
    }

    private fun physicalHeader(headers: List<String>): List<String> =
        headers + List(IDENTITY_COLUMN - headers.size) { "" } + IDENTITY_HEADER

    private fun allocateSheetId(sheets: List<GoogleSheetDescriptor>, tabName: String): Int {
        val used = sheets.mapTo(mutableSetOf()) { it.sheetId }
        var candidate = tabName.hashCode() and Int.MAX_VALUE
        while (candidate in used) {
            candidate = if (candidate == Int.MAX_VALUE) 0 else candidate + 1
        }
        return candidate
    }

    private val ORIGIN_ID = Regex("^[a-f0-9]{32}$")
}
