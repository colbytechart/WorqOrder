package worq.order.data

import java.util.Locale

const val MAX_EMPLOYEE_NAME_CODE_POINTS = 100

data class NormalizedEmployeeName(
    val displayName: String,
    val canonicalName: String,
)

enum class EmployeeNameValidationError {
    BLANK,
    TOO_LONG,
}

sealed interface EmployeeNameValidationResult {
    data class Valid(
        val name: NormalizedEmployeeName,
    ) : EmployeeNameValidationResult

    data class Invalid(
        val error: EmployeeNameValidationError,
    ) : EmployeeNameValidationResult
}

object EmployeeNameNormalizer {
    fun validate(rawName: String): EmployeeNameValidationResult {
        val displayName = collapseWhitespace(rawName)
        if (displayName.isEmpty()) {
            return EmployeeNameValidationResult.Invalid(EmployeeNameValidationError.BLANK)
        }
        if (displayName.codePointCount(0, displayName.length) > MAX_EMPLOYEE_NAME_CODE_POINTS) {
            return EmployeeNameValidationResult.Invalid(EmployeeNameValidationError.TOO_LONG)
        }
        return EmployeeNameValidationResult.Valid(
            NormalizedEmployeeName(
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
                    if (pendingSpace) append(' ')
                    append(character)
                    pendingSpace = false
                }
            }
        }
}
