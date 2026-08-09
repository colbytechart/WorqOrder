package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeeNameNormalizerTest {
    @Test
    fun trimsCollapsesWhitespaceAndUsesLocaleIndependentCase() {
        val result = EmployeeNameNormalizer.validate("  Alex\t  RIVERA  ")

        assertEquals(
            NormalizedEmployeeName("Alex RIVERA", "alex rivera"),
            (result as EmployeeNameValidationResult.Valid).name,
        )
    }

    @Test
    fun blankAndOverlengthNamesAreRejected() {
        assertEquals(
            EmployeeNameValidationError.BLANK,
            (EmployeeNameNormalizer.validate(" \t ") as EmployeeNameValidationResult.Invalid).error,
        )
        assertTrue(
            EmployeeNameNormalizer.validate("a".repeat(101)) is
                EmployeeNameValidationResult.Invalid,
        )
    }
}
