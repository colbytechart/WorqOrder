package worq.order.data

import worq.order.model.WorkType

const val MAX_TASK_DESCRIPTION_CODE_POINTS = 400
const val MAX_TASK_PURCHASES_CODE_POINTS = 400

enum class TaskMetadataValidationError {
    DESCRIPTION_REQUIRED,
    DESCRIPTION_TOO_LONG,
    PURCHASES_TOO_LONG,
    MILEAGE_MALFORMED,
    MILEAGE_TOO_LARGE,
    MILEAGE_TOO_PRECISE,
}

data class NormalizedTaskMetadata(
    val description: String,
    val hardwareSoftwarePurchases: String,
    val workType: WorkType = WorkType.UNSPECIFIED,
    val mileage: String? = null,
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
        workType: WorkType = WorkType.UNSPECIFIED,
        mileage: String? = null,
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

        val normalizedMileage =
            when (val result = MileageNormalizer.normalize(mileage)) {
                is MileageValidationResult.Valid -> result.canonicalValue
                is MileageValidationResult.Invalid -> {
                    errors +=
                        when (result.error) {
                            MileageValidationError.MALFORMED ->
                                TaskMetadataValidationError.MILEAGE_MALFORMED
                            MileageValidationError.TOO_LARGE ->
                                TaskMetadataValidationError.MILEAGE_TOO_LARGE
                            MileageValidationError.TOO_PRECISE ->
                                TaskMetadataValidationError.MILEAGE_TOO_PRECISE
                        }
                    null
                }
            }

        return if (errors.isEmpty()) {
            TaskMetadataValidationResult.Valid(
                NormalizedTaskMetadata(
                    description = normalizedDescription,
                    hardwareSoftwarePurchases = normalizedPurchases,
                    workType = workType,
                    mileage = normalizedMileage,
                ),
            )
        } else {
            TaskMetadataValidationResult.Invalid(errors)
        }
    }
}
