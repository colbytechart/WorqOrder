package worq.order.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import worq.order.data.ClientCsvImportLimits

data class ParsedClientCsvCell(
    val value: String,
    val recordNumber: Int,
    val columnNumber: Int,
)

sealed interface ClientCsvParseResult {
    data class Success(
        val cells: List<ParsedClientCsvCell>,
    ) : ClientCsvParseResult

    data object TooLarge : ClientCsvParseResult

    data object InvalidUtf8 : ClientCsvParseResult

    data class MalformedCsv(
        val recordNumber: Int,
        val columnNumber: Int,
    ) : ClientCsvParseResult

    data object TooManyRecords : ClientCsvParseResult

    data object TooManyCells : ClientCsvParseResult

    data class CellTooLarge(
        val recordNumber: Int,
        val columnNumber: Int,
    ) : ClientCsvParseResult
}

class ClientCsvParser {
    fun parse(bytes: ByteArray): ClientCsvParseResult {
        if (bytes.size > ClientCsvImportLimits.MAX_BYTES) {
            return ClientCsvParseResult.TooLarge
        }
        val decoded = decodeUtf8(bytes) ?: return ClientCsvParseResult.InvalidUtf8
        val text = decoded.removePrefix("\uFEFF")
        if (text.isEmpty()) return ClientCsvParseResult.Success(emptyList())

        val cells = mutableListOf<ParsedClientCsvCell>()
        val field = StringBuilder()
        var index = 0
        var recordNumber = 1
        var columnNumber = 1
        var recordCount = 0
        var inQuotes = false
        var justClosedQuote = false
        var atFieldStart = true
        var endedWithRecordDelimiter = false

        fun append(character: Char): ClientCsvParseResult? {
            if (character == '\u0000') {
                return ClientCsvParseResult.MalformedCsv(recordNumber, columnNumber)
            }
            field.append(character)
            return if (field.length > ClientCsvImportLimits.MAX_RAW_CELL_UTF16_UNITS) {
                ClientCsvParseResult.CellTooLarge(recordNumber, columnNumber)
            } else {
                null
            }
        }

        fun finishField(): ClientCsvParseResult? {
            cells +=
                ParsedClientCsvCell(
                    value = field.toString(),
                    recordNumber = recordNumber,
                    columnNumber = columnNumber,
                )
            if (cells.size > ClientCsvImportLimits.MAX_CELLS) {
                return ClientCsvParseResult.TooManyCells
            }
            field.clear()
            atFieldStart = true
            justClosedQuote = false
            return null
        }

        fun finishRecord(): ClientCsvParseResult? {
            recordCount += 1
            if (recordCount > ClientCsvImportLimits.MAX_RECORDS) {
                return ClientCsvParseResult.TooManyRecords
            }
            recordNumber += 1
            columnNumber = 1
            return null
        }

        while (index < text.length) {
            val character = text[index]
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        append('"')?.let { return it }
                        index += 2
                    } else {
                        inQuotes = false
                        justClosedQuote = true
                        index += 1
                    }
                } else {
                    append(character)?.let { return it }
                    index += 1
                }
                endedWithRecordDelimiter = false
                continue
            }

            if (justClosedQuote) {
                when (character) {
                    ',' -> {
                        finishField()?.let { return it }
                        columnNumber += 1
                        index += 1
                        endedWithRecordDelimiter = false
                    }
                    '\r', '\n' -> {
                        finishField()?.let { return it }
                        if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                            index += 1
                        }
                        finishRecord()?.let { return it }
                        index += 1
                        endedWithRecordDelimiter = true
                    }
                    else ->
                        return ClientCsvParseResult.MalformedCsv(recordNumber, columnNumber)
                }
                continue
            }

            when (character) {
                '"' -> {
                    if (!atFieldStart) {
                        return ClientCsvParseResult.MalformedCsv(recordNumber, columnNumber)
                    }
                    inQuotes = true
                    atFieldStart = false
                    index += 1
                    endedWithRecordDelimiter = false
                }
                ',' -> {
                    finishField()?.let { return it }
                    columnNumber += 1
                    index += 1
                    endedWithRecordDelimiter = false
                }
                '\r', '\n' -> {
                    finishField()?.let { return it }
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 1
                    }
                    finishRecord()?.let { return it }
                    index += 1
                    endedWithRecordDelimiter = true
                }
                else -> {
                    append(character)?.let { return it }
                    atFieldStart = false
                    index += 1
                    endedWithRecordDelimiter = false
                }
            }
        }

        if (inQuotes) {
            return ClientCsvParseResult.MalformedCsv(recordNumber, columnNumber)
        }
        if (!endedWithRecordDelimiter) {
            finishField()?.let { return it }
            finishRecord()?.let { return it }
        }
        return ClientCsvParseResult.Success(cells)
    }

    private fun decodeUtf8(bytes: ByteArray): String? =
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
}
