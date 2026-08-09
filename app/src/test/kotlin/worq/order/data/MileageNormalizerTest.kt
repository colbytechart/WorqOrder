package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MileageNormalizerTest {
    @Test
    fun blankAndValidDecimalsUseCanonicalPlainText() {
        assertEquals(MileageValidationResult.Valid(null), MileageNormalizer.normalize("  "))
        assertEquals(MileageValidationResult.Valid("18.5"), MileageNormalizer.normalize("0018.500"))
        assertEquals(MileageValidationResult.Valid("0.25"), MileageNormalizer.normalize(".250"))
        assertEquals(MileageValidationResult.Valid("0"), MileageNormalizer.normalize("0.000"))
    }

    @Test
    fun malformedOverlargeAndOverpreciseValuesAreRejected() {
        assertEquals(
            MileageValidationResult.Invalid(MileageValidationError.MALFORMED),
            MileageNormalizer.normalize("1e3"),
        )
        assertEquals(
            MileageValidationResult.Invalid(MileageValidationError.TOO_LARGE),
            MileageNormalizer.normalize("1234567890"),
        )
        assertEquals(
            MileageValidationResult.Invalid(MileageValidationError.TOO_PRECISE),
            MileageNormalizer.normalize("1.2345"),
        )
    }

    @Test
    fun editableInputAllowsOnlyDigitsAndOneDecimalPoint() {
        assertEquals(true, MileageNormalizer.acceptsInput(""))
        assertEquals(true, MileageNormalizer.acceptsInput("."))
        assertEquals(true, MileageNormalizer.acceptsInput("12.5"))
        assertEquals(false, MileageNormalizer.acceptsInput("12.5.1"))
        assertEquals(false, MileageNormalizer.acceptsInput("-1"))
        assertEquals(false, MileageNormalizer.acceptsInput("1,5"))
    }
}
