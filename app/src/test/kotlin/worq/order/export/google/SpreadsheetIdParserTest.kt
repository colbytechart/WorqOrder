package worq.order.export.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetIdParserTest {
    @Test
    fun parsesRawSpreadsheetId() {
        assertEquals(
            SpreadsheetInputParseResult.Valid(SPREADSHEET_ID),
            SpreadsheetIdParser.parse(SPREADSHEET_ID),
        )
    }

    @Test
    fun parsesStandardGoogleSheetsUrl() {
        assertEquals(
            SpreadsheetInputParseResult.Valid(SPREADSHEET_ID),
            SpreadsheetIdParser.parse(
                "https://docs.google.com/spreadsheets/d/" +
                    "$SPREADSHEET_ID/edit?gid=123#gid=123",
            ),
        )
    }

    @Test
    fun rejectsUnsupportedAndMalformedInputs() {
        val inputs =
            listOf(
                "",
                "short",
                "https://example.com/spreadsheets/d/$SPREADSHEET_ID/edit",
                "https://docs.google.com/document/d/$SPREADSHEET_ID/edit",
                "$SPREADSHEET_ID?gid=1",
            )

        inputs.forEach { input ->
            assertTrue(
                "Expected invalid input: $input",
                SpreadsheetIdParser.parse(input) is
                    SpreadsheetInputParseResult.Invalid,
            )
        }
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
