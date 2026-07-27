package worq.order.export.google

import java.net.URI

sealed interface SpreadsheetInputParseResult {
    data class Valid(
        val spreadsheetId: String,
    ) : SpreadsheetInputParseResult

    data class Invalid(
        val reason: SpreadsheetInputError,
    ) : SpreadsheetInputParseResult
}

enum class SpreadsheetInputError {
    EMPTY,
    TOO_LONG,
    MALFORMED_ID,
    UNSUPPORTED_URL,
}

object SpreadsheetIdParser {
    const val MAX_INPUT_LENGTH = 500
    private const val MIN_ID_LENGTH = 20
    private const val MAX_ID_LENGTH = 200
    private val idPattern = Regex("[A-Za-z0-9_-]+")

    fun parse(input: String): SpreadsheetInputParseResult {
        val value = input.trim()
        if (value.isEmpty()) {
            return SpreadsheetInputParseResult.Invalid(
                SpreadsheetInputError.EMPTY,
            )
        }
        if (value.length > MAX_INPUT_LENGTH) {
            return SpreadsheetInputParseResult.Invalid(
                SpreadsheetInputError.TOO_LONG,
            )
        }
        if ("://" !in value) {
            return validIdOrError(value)
        }
        val uri =
            runCatching { URI(value) }.getOrNull()
                ?: return SpreadsheetInputParseResult.Invalid(
                    SpreadsheetInputError.UNSUPPORTED_URL,
                )
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("docs.google.com", ignoreCase = true) ||
            uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) {
            return SpreadsheetInputParseResult.Invalid(
                SpreadsheetInputError.UNSUPPORTED_URL,
            )
        }
        val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        if (
            segments.size < 3 ||
            segments[0] != "spreadsheets" ||
            segments[1] != "d"
        ) {
            return SpreadsheetInputParseResult.Invalid(
                SpreadsheetInputError.UNSUPPORTED_URL,
            )
        }
        return validIdOrError(segments[2])
    }

    private fun validIdOrError(value: String): SpreadsheetInputParseResult =
        if (
            value.length in MIN_ID_LENGTH..MAX_ID_LENGTH &&
            idPattern.matches(value)
        ) {
            SpreadsheetInputParseResult.Valid(value)
        } else {
            SpreadsheetInputParseResult.Invalid(
                SpreadsheetInputError.MALFORMED_ID,
            )
        }
}
