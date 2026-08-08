package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.model.WorkType
import worq.order.model.BillingStatus

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

    @Test
    fun normalizesMileageAndRetainsWorkTypeAndBillingStatus() {
        val valid =
            TaskMetadataValidator.validate(
                description = "Task",
                hardwareSoftwarePurchases = "",
                workType = WorkType.IN_OFFICE,
                billingStatus = BillingStatus.DO_NOT_BILL,
                mileage = "0012.500",
            ) as TaskMetadataValidationResult.Valid

        assertEquals(WorkType.IN_OFFICE, valid.metadata.workType)
        assertEquals(BillingStatus.DO_NOT_BILL, valid.metadata.billingStatus)
        assertEquals("12.5", valid.metadata.mileage)

        val invalid =
            TaskMetadataValidator.validate(
                description = "Task",
                hardwareSoftwarePurchases = "",
                workType = WorkType.ON_SITE,
                mileage = "12.3456",
            ) as TaskMetadataValidationResult.Invalid
        assertEquals(
            setOf(TaskMetadataValidationError.MILEAGE_TOO_PRECISE),
            invalid.errors,
        )
    }
}
