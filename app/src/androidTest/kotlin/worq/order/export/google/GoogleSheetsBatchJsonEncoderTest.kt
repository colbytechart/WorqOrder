package worq.order.export.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleSheetsBatchJsonEncoderTest {
    @Test
    fun encodesAtomicCreateMetadataAndLiteralCellRequests() {
        val plan =
            GoogleSheetsBatchPlan(
                tabName = "WorqOrder_2026-07-24",
                requests =
                    listOf(
                        GoogleSheetsBatchRequest.AddSheet(
                            sheetId = 42,
                            title = "WorqOrder_2026-07-24",
                            rowCount = 2,
                            columnCount = 9,
                        ),
                        GoogleSheetsBatchRequest.CreateSheetMetadata(
                            sheetId = 42,
                            key =
                                GoogleSheetsExportPlanner
                                    .APPLICATION_MARKER_KEY,
                            value =
                                GoogleSheetsExportPlanner
                                    .APPLICATION_MARKER_VALUE,
                        ),
                        GoogleSheetsBatchRequest.ReplaceCells(
                            sheetId = 42,
                            rows =
                                listOf(
                                    listOf("Header"),
                                    listOf("=literal user text"),
                                ),
                        ),
                    ),
            )

        val requests =
            JSONObject(GoogleSheetsBatchJsonEncoder.encode(plan))
                .getJSONArray("requests")
        val addProperties =
            requests
                .getJSONObject(0)
                .getJSONObject("addSheet")
                .getJSONObject("properties")
        assertEquals(42, addProperties.getInt("sheetId"))
        assertEquals(
            "WorqOrder_2026-07-24",
            addProperties.getString("title"),
        )

        val metadata =
            requests
                .getJSONObject(1)
                .getJSONObject("createDeveloperMetadata")
                .getJSONObject("developerMetadata")
        assertEquals("PROJECT", metadata.getString("visibility"))
        assertEquals(
            42,
            metadata.getJSONObject("location").getInt("sheetId"),
        )

        val update =
            requests
                .getJSONObject(2)
                .getJSONObject("updateCells")
        assertEquals("userEnteredValue", update.getString("fields"))
        val literalValue =
            update
                .getJSONArray("rows")
                .getJSONObject(1)
                .getJSONArray("values")
                .getJSONObject(0)
                .getJSONObject("userEnteredValue")
        assertEquals(
            "=literal user text",
            literalValue.getString("stringValue"),
        )
        assertEquals(1, literalValue.length())
    }

    @Test
    fun encodesBlankFirstSheetRenameWithoutAddingAnotherSheet() {
        val plan =
            GoogleSheetsBatchPlan(
                tabName = "WorqOrder_2026-07-24",
                requests =
                    listOf(
                        GoogleSheetsBatchRequest.RenameSheet(
                            sheetId = 0,
                            title = "WorqOrder_2026-07-24",
                        ),
                    ),
            )

        val request =
            JSONObject(GoogleSheetsBatchJsonEncoder.encode(plan))
                .getJSONArray("requests")
                .getJSONObject(0)
        assertFalse(request.has("addSheet"))
        val update = request.getJSONObject("updateSheetProperties")
        assertEquals("title", update.getString("fields"))
        assertEquals(
            "WorqOrder_2026-07-24",
            update
                .getJSONObject("properties")
                .getString("title"),
        )
    }

    @Test
    fun parsesSheetNestedOwnershipMetadataUsedByReExport() {
        val gateway = RestGoogleSheetsGateway()
        val structure =
            gateway.parseSpreadsheetStructure(
                """
                {
                  "spreadsheetId": "spreadsheet-id",
                  "sheets": [{
                    "properties": {
                      "sheetId": 27,
                      "title": "WorqOrder_2026-07-24"
                    },
                    "developerMetadata": [{
                      "metadataId": 1,
                      "metadataKey": "worqorder_export_marker",
                      "metadataValue": "WORQORDER_EXPORT",
                      "location": {"sheetId": 27}
                    }, {
                      "metadataId": 2,
                      "metadataKey": "worqorder_export_schema",
                      "metadataValue": "2",
                      "location": {"sheetId": 27}
                    }, {
                      "metadataId": 3,
                      "metadataKey": "worqorder_export_work_date",
                      "metadataValue": "2026-07-24",
                      "location": {"sheetId": 27}
                    }]
                  }]
                }
                """.trimIndent(),
            )

        assertEquals(3, structure?.developerMetadata?.size)
        assertEquals(
            "WORQORDER_EXPORT",
            structure
                ?.developerMetadata
                ?.single {
                    it.key ==
                        GoogleSheetsExportPlanner.APPLICATION_MARKER_KEY
                }?.value,
        )
    }

    @Test
    fun blanknessInspectionDistinguishesEmptyAndPopulatedSheets() {
        val gateway = RestGoogleSheetsGateway()

        assertTrue(
            gateway.parseSpreadsheetBlankness(
                """{"sheets":[{"data":[{"rowData":[]}]}]}""",
            ) == true,
        )
        assertFalse(
            gateway.parseSpreadsheetBlankness(
                """
                {
                  "sheets": [{
                    "data": [{
                      "rowData": [{
                        "values": [{
                          "userEnteredValue": {"stringValue": "existing"}
                        }]
                      }]
                    }]
                  }]
                }
                """.trimIndent(),
            ) == true,
        )
    }
}
