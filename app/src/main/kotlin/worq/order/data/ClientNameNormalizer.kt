package worq.order.data

import java.util.Locale

const val MAX_CLIENT_NAME_CODE_POINTS = 100

data class NormalizedClientName(
    val displayName: String,
    val canonicalName: String,
)

enum class ClientNameValidationError {
    BLANK,
    TOO_LONG,
}

sealed interface ClientNameValidationResult {
    data class Valid(
        val name: NormalizedClientName,
    ) : ClientNameValidationResult

    data class Invalid(
        val error: ClientNameValidationError,
    ) : ClientNameValidationResult
}

object ClientNameNormalizer {
    fun validate(rawName: String): ClientNameValidationResult {
        val displayName = collapseWhitespace(rawName)
        if (displayName.isEmpty()) {
            return ClientNameValidationResult.Invalid(ClientNameValidationError.BLANK)
        }
        if (displayName.codePointCount(0, displayName.length) > MAX_CLIENT_NAME_CODE_POINTS) {
            return ClientNameValidationResult.Invalid(ClientNameValidationError.TOO_LONG)
        }
        return ClientNameValidationResult.Valid(
            NormalizedClientName(
                displayName = displayName,
                canonicalName = displayName.lowercase(Locale.ROOT),
            ),
        )
    }

    private fun collapseWhitespace(value: String): String =
        buildString(value.length) {
            var pendingSpace = false
            value.forEach { character ->
                if (character.isWhitespace()) {
                    pendingSpace = isNotEmpty()
                } else {
                    if (pendingSpace) {
                        append(' ')
                    }
                    append(character)
                    pendingSpace = false
                }
            }
        }
}
