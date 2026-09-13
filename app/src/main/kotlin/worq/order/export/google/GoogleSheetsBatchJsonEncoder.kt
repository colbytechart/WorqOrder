package worq.order.export.google

import org.json.JSONArray
import org.json.JSONObject

internal object GoogleSheetsBatchJsonEncoder {
    fun encode(plan: GoogleSheetsBatchPlan): String =
        JSONObject()
            .put(
                "requests",
                JSONArray().apply {
                    plan.requests.forEach { put(it.toJson()) }
                },
            ).toString()

    private fun GoogleSheetsBatchRequest.toJson(): JSONObject =
        when (this) {
            is GoogleSheetsBatchRequest.AddSheet ->
                JSONObject().put(
                    "addSheet",
                    JSONObject().put(
                        "properties",
                        JSONObject()
                            .put("sheetId", sheetId)
                            .put("title", title)
                            .put(
                                "gridProperties",
                                JSONObject()
                                    .put("rowCount", rowCount)
                                    .put("columnCount", columnCount),
                        ),
                    ),
                )
            is GoogleSheetsBatchRequest.RenameSheet ->
                JSONObject().put(
                    "updateSheetProperties",
                    JSONObject()
                        .put(
                            "properties",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put("title", title),
                        ).put("fields", "title"),
                )
            is GoogleSheetsBatchRequest.CreateSheetMetadata ->
                JSONObject().put(
                    "createDeveloperMetadata",
                    JSONObject().put(
                        "developerMetadata",
                        JSONObject()
                            .put("metadataKey", key)
                            .put("metadataValue", value)
                            .put("visibility", "PROJECT")
                            .put(
                                "location",
                                JSONObject().put("sheetId", sheetId),
                            ),
                    ),
                )
            is GoogleSheetsBatchRequest.UpdateSheetMetadataValue ->
                JSONObject().put(
                    "updateDeveloperMetadata",
                    JSONObject()
                        .put(
                            "dataFilters",
                            JSONArray().put(
                                JSONObject().put(
                                    "developerMetadataLookup",
                                    JSONObject().put("metadataId", metadataId),
                                ),
                            ),
                        ).put(
                            "developerMetadata",
                            JSONObject().put("metadataValue", value),
                        ).put("fields", "metadataValue"),
                )
            is GoogleSheetsBatchRequest.ResizeSheet ->
                JSONObject().put(
                    "updateSheetProperties",
                    JSONObject()
                        .put(
                            "properties",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put(
                                    "gridProperties",
                                    JSONObject()
                                        .put("rowCount", rowCount)
                                        .put("columnCount", columnCount),
                                ),
                        ).put(
                            "fields",
                            "gridProperties(rowCount,columnCount)",
                        ),
                )
            is GoogleSheetsBatchRequest.SetColumnCount ->
                JSONObject().put(
                    "updateSheetProperties",
                    JSONObject()
                        .put(
                            "properties",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put("gridProperties", JSONObject().put("columnCount", columnCount)),
                        ).put("fields", "gridProperties.columnCount"),
                )
            is GoogleSheetsBatchRequest.HideColumns ->
                JSONObject().put(
                    "updateDimensionProperties",
                    JSONObject()
                        .put(
                            "range",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put("dimension", "COLUMNS")
                                .put("startIndex", startIndex)
                                .put("endIndex", endIndex),
                        ).put("properties", JSONObject().put("hiddenByUser", true))
                        .put("fields", "hiddenByUser"),
                )
            is GoogleSheetsBatchRequest.WriteCellsAt ->
                JSONObject().put(
                    "updateCells",
                    JSONObject()
                        .put(
                            "start",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put("rowIndex", rowIndex)
                                .put("columnIndex", columnIndex),
                        ).put("rows", rows.toCellRows())
                        .put("fields", "userEnteredValue"),
                )
            is GoogleSheetsBatchRequest.AppendCells ->
                JSONObject().put(
                    "appendCells",
                    JSONObject()
                        .put("sheetId", sheetId)
                        .put("rows", rows.toCellRows())
                        .put("fields", "userEnteredValue"),
                )
            is GoogleSheetsBatchRequest.ReplaceCells ->
                JSONObject().put(
                    "updateCells",
                    JSONObject()
                        .put(
                            "range",
                            JSONObject()
                                .put("sheetId", sheetId)
                                .put("startRowIndex", 0)
                                .put("endRowIndex", rows.size)
                                .put("startColumnIndex", 0)
                                .put(
                                    "endColumnIndex",
                                    rows.firstOrNull()?.size ?: 0,
                                ),
                        ).put("rows", rows.toCellRows())
                        .put("fields", "userEnteredValue"),
                )
        }

    private fun List<List<String>>.toCellRows(): JSONArray =
        JSONArray().apply {
            this@toCellRows.forEach { row ->
                put(
                    JSONObject().put(
                        "values",
                        JSONArray().apply {
                            row.forEach { value ->
                                put(
                                    JSONObject().put(
                                        "userEnteredValue",
                                        JSONObject().put("stringValue", value),
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }
}
