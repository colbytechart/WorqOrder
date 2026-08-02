package worq.order.data

import java.math.BigDecimal

enum class MileageValidationError {
    MALFORMED,
    TOO_LARGE,
    TOO_PRECISE,
}

sealed interface MileageValidationResult {
    data class Valid(
        val canonicalValue: String?,
    ) : MileageValidationResult

    data class Invalid(
        val error: MileageValidationError,
    ) : MileageValidationResult
}

object MileageNormalizer {
    const val MAX_INTEGER_DIGITS = 9
    const val MAX_FRACTION_DIGITS = 3
    private const val MAX_INPUT_CHARACTERS = 32
    private val decimalPattern = Regex("""(?:\d+(?:\.\d*)?|\.\d+)""")

    fun normalize(rawValue: String?): MileageValidationResult {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return MileageValidationResult.Valid(null)
        if (value.length > MAX_INPUT_CHARACTERS || !decimalPattern.matches(value)) {
            return MileageValidationResult.Invalid(MileageValidationError.MALFORMED)
        }
        val normalized = BigDecimal(value).stripTrailingZeros()
        val canonical = if (normalized.signum() == 0) "0" else normalized.toPlainString()
        val parts = canonical.split('.', limit = 2)
        if (parts[0].length > MAX_INTEGER_DIGITS) {
            return MileageValidationResult.Invalid(MileageValidationError.TOO_LARGE)
        }
        if (parts.getOrNull(1).orEmpty().length > MAX_FRACTION_DIGITS) {
            return MileageValidationResult.Invalid(MileageValidationError.TOO_PRECISE)
        }
        return MileageValidationResult.Valid(canonical)
    }
}
