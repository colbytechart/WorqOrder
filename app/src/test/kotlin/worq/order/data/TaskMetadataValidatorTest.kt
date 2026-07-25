package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMetadataValidatorTest {
    @Test
    fun trimsBothFieldsAndAllowsBlankPurchases() {
        val result =
            TaskMetadataValidator.validate(
                description = "  Short task  ",
                hardwareSoftwarePurchases = "  ",
            )

        assertEquals(
            TaskMetadataValidationResult.Valid(
                NormalizedTaskMetadata(
                    description = "Short task",
                    hardwareSoftwarePurchases = "",
                ),
            ),
            result,
        )
    }

    @Test
    fun rejectsBlankDescriptionAndIndependentOverlengthFields() {
        val blank =
            TaskMetadataValidator.validate(
                description = " \n ",
                hardwareSoftwarePurchases = "",
            )
        assertTrue(
            (blank as TaskMetadataValidationResult.Invalid)
                .errors
                .contains(TaskMetadataValidationError.DESCRIPTION_REQUIRED),
        )

        val overlength =
            TaskMetadataValidator.validate(
                description = "d".repeat(MAX_TASK_DESCRIPTION_CODE_POINTS + 1),
                hardwareSoftwarePurchases =
                    "p".repeat(MAX_TASK_PURCHASES_CODE_POINTS + 1),
            ) as TaskMetadataValidationResult.Invalid
        assertEquals(
            setOf(
                TaskMetadataValidationError.DESCRIPTION_TOO_LONG,
                TaskMetadataValidationError.PURCHASES_TOO_LONG,
            ),
            overlength.errors,
        )
    }

    @Test
    fun countsUnicodeCodePointsInsteadOfUtf16Units() {
        val emoji = "\uD83D\uDEE0"

        assertTrue(
            TaskMetadataValidator.validate(
                description = emoji.repeat(MAX_TASK_DESCRIPTION_CODE_POINTS),
                hardwareSoftwarePurchases = emoji.repeat(MAX_TASK_PURCHASES_CODE_POINTS),
            ) is TaskMetadataValidationResult.Valid,
        )
    }
}
