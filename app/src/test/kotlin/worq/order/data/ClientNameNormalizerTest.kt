package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientNameNormalizerTest {
    @Test
    fun normalizesSurroundingRepeatedWhitespaceAndCase() {
        val result = ClientNameNormalizer.validate(" \tAcme\u2003  CORP\n")

        assertTrue(result is ClientNameValidationResult.Valid)
        val valid = result as ClientNameValidationResult.Valid
        assertEquals("Acme CORP", valid.name.displayName)
        assertEquals("acme corp", valid.name.canonicalName)
    }

    @Test
    fun rejectsBlankAndOverLimitNames() {
        assertEquals(
            ClientNameValidationResult.Invalid(ClientNameValidationError.BLANK),
            ClientNameNormalizer.validate(" \t\n "),
        )
        assertEquals(
            ClientNameValidationResult.Invalid(ClientNameValidationError.TOO_LONG),
            ClientNameNormalizer.validate("a".repeat(MAX_CLIENT_NAME_CODE_POINTS + 1)),
        )
    }

    @Test
    fun lengthLimitCountsUnicodeCodePoints() {
        val emoji = "\uD83D\uDC68"
        val result = ClientNameNormalizer.validate(emoji.repeat(MAX_CLIENT_NAME_CODE_POINTS))

        assertTrue(result is ClientNameValidationResult.Valid)
    }
}
