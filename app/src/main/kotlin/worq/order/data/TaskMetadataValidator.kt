package worq.order.data

const val MAX_TASK_DESCRIPTION_CODE_POINTS = 400
const val MAX_TASK_PURCHASES_CODE_POINTS = 400

enum class TaskMetadataValidationError {
    DESCRIPTION_REQUIRED,
    DESCRIPTION_TOO_LONG,
    PURCHASES_TOO_LONG,
}

data class NormalizedTaskMetadata(
    val description: String,
    val hardwareSoftwarePurchases: String,
)

sealed interface TaskMetadataValidationResult {
    data class Valid(
        val metadata: NormalizedTaskMetadata,
    ) : TaskMetadataValidationResult

    data class Invalid(
        val errors: Set<TaskMetadataValidationError>,
    ) : TaskMetadataValidationResult
}

object TaskMetadataValidator {
    fun validate(
        description: String,
        hardwareSoftwarePurchases: String,
    ): TaskMetadataValidationResult {
        val normalizedDescription = description.trim()
        val normalizedPurchases = hardwareSoftwarePurchases.trim()
        val errors = linkedSetOf<TaskMetadataValidationError>()

        if (normalizedDescription.isEmpty()) {
            errors += TaskMetadataValidationError.DESCRIPTION_REQUIRED
        } else if (
            normalizedDescription.codePointCount(0, normalizedDescription.length) >
            MAX_TASK_DESCRIPTION_CODE_POINTS
        ) {
            errors += TaskMetadataValidationError.DESCRIPTION_TOO_LONG
        }
        if (
            normalizedPurchases.codePointCount(0, normalizedPurchases.length) >
            MAX_TASK_PURCHASES_CODE_POINTS
        ) {
            errors += TaskMetadataValidationError.PURCHASES_TOO_LONG
        }

        return if (errors.isEmpty()) {
            TaskMetadataValidationResult.Valid(
                NormalizedTaskMetadata(
                    description = normalizedDescription,
                    hardwareSoftwarePurchases = normalizedPurchases,
                ),
            )
        } else {
            TaskMetadataValidationResult.Invalid(errors)
        }
    }
}
