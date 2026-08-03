package worq.order.domain

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ClientCsvImportLimits

class ClientCsvParserTest {
    private val parser = ClientCsvParser()

    @Test
    fun parsesEveryCellIncludingQuotedUnicodeCommasQuotesAndLineBreaks() {
        val csv =
            "\uFEFFAlpha,\"Bravo, LLC\"\r\n" +
                "\"Delta \"\"Design\"\"\",\"Élan\nStudio\""

        val result = parser.parse(csv.toByteArray(StandardCharsets.UTF_8))

        assertTrue(result is ClientCsvParseResult.Success)
        assertEquals(
            listOf("Alpha", "Bravo, LLC", "Delta \"Design\"", "Élan\nStudio"),
            (result as ClientCsvParseResult.Success).cells.map(ParsedClientCsvCell::value),
        )
        assertEquals(listOf(1, 1, 2, 2), result.cells.map(ParsedClientCsvCell::recordNumber))
        assertEquals(listOf(1, 2, 1, 2), result.cells.map(ParsedClientCsvCell::columnNumber))
    }

    @Test
    fun acceptsCrLfLfCrAndTrailingEmptyCell() {
        val result = parser.parse("A\r\nB\nC\rD,".toByteArray())

        assertEquals(
            listOf("A", "B", "C", "D", ""),
            (result as ClientCsvParseResult.Success).cells.map(ParsedClientCsvCell::value),
        )
    }

    @Test
    fun rejectsInvalidUtf8AndMalformedCsv() {
        assertEquals(
            ClientCsvParseResult.InvalidUtf8,
            parser.parse(byteArrayOf(0xC3.toByte(), 0x28)),
        )
        assertTrue(
            parser.parse("\"unterminated".toByteArray()) is
                ClientCsvParseResult.MalformedCsv,
        )
        assertTrue(
            parser.parse("\"closed\"suffix".toByteArray()) is
                ClientCsvParseResult.MalformedCsv,
        )
        assertTrue(
            parser.parse("unquoted\"quote".toByteArray()) is
                ClientCsvParseResult.MalformedCsv,
        )
    }

    @Test
    fun enforcesByteRecordCellAndRawCellLimits() {
        assertEquals(
            ClientCsvParseResult.TooLarge,
            parser.parse(ByteArray(ClientCsvImportLimits.MAX_BYTES + 1)),
        )
        assertEquals(
            ClientCsvParseResult.TooManyRecords,
            parser.parse("A\n".repeat(ClientCsvImportLimits.MAX_RECORDS + 1).toByteArray()),
        )
        assertEquals(
            ClientCsvParseResult.TooManyCells,
            parser.parse(
                ("A,".repeat(ClientCsvImportLimits.MAX_CELLS) + "A").toByteArray(),
            ),
        )
        assertEquals(
            ClientCsvParseResult.CellTooLarge(1, 1),
            parser.parse(
                "A".repeat(ClientCsvImportLimits.MAX_RAW_CELL_UTF16_UNITS + 1).toByteArray(),
            ),
        )
    }
}
