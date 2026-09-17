package worq.order.data

import worq.order.model.WorkType
import worq.order.model.BillingStatus
import worq.order.model.TaskTagSnapshotDraft

const val MAX_TASK_DESCRIPTION_CODE_POINTS = MAX_COMPOSED_TASK_TEXT_CODE_POINTS
const val MAX_TASK_PURCHASES_CODE_POINTS = MAX_COMPOSED_TASK_TEXT_CODE_POINTS
const val MAX_TASK_NOTES_CODE_POINTS = 999

enum class TaskMetadataValidationError {
    DESCRIPTION_REQUIRED,
    DESCRIPTION_TOO_LONG,
    PURCHASES_TOO_LONG,
    DESCRIPTION_TAG_INVALID,
    PURCHASES_TAG_INVALID,
    NOTES_TOO_LONG,
    MILEAGE_MALFORMED,
    MILEAGE_TOO_LARGE,
    MILEAGE_TOO_PRECISE,
}

data class NormalizedTaskMetadata(
    val description: String,
    val hardwareSoftwarePurchases: String,
    val workType: WorkType = WorkType.UNSPECIFIED,
    val billingStatus: BillingStatus? = null,
    val mileage: String? = null,
    val notes: String = "",
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
        billingStatus: BillingStatus? = null,
        mileage: String? = null,
        notes: String = "",
        descriptionTagSnapshots: List<TaskTagSnapshotDraft> = emptyList(),
        hardwareSoftwarePurchaseTagSnapshots: List<TaskTagSnapshotDraft> = emptyList(),
    ): TaskMetadataValidationResult {
        val normalizedDescription = description.trim()
        val normalizedPurchases = hardwareSoftwarePurchases.trim()
        val normalizedNotes = notes.trim()
        val errors = linkedSetOf<TaskMetadataValidationError>()
        val normalizedDescriptionTags =
            descriptionTagSnapshots.mapNotNull { draft ->
                (TagTextNormalizer.validate(draft.text) as? TagTextValidationResult.Valid)?.text
            }
        val normalizedPurchaseTags =
            hardwareSoftwarePurchaseTagSnapshots.mapNotNull { draft ->
                (TagTextNormalizer.validate(draft.text) as? TagTextValidationResult.Valid)?.text
            }
        if (normalizedDescriptionTags.size != descriptionTagSnapshots.size) {
            errors += TaskMetadataValidationError.DESCRIPTION_TAG_INVALID
        }
        if (normalizedPurchaseTags.size != hardwareSoftwarePurchaseTagSnapshots.size) {
            errors += TaskMetadataValidationError.PURCHASES_TAG_INVALID
        }
        val composedDescription =
            TaskTextComposer.compose(
                normalizedDescription,
                normalizedDescriptionTags.map(NormalizedTagText::displayText),
            )
        val composedPurchases =
            TaskTextComposer.compose(
                normalizedPurchases,
                normalizedPurchaseTags.map(NormalizedTagText::displayText),
            )

        if (composedDescription.isEmpty()) {
            errors += TaskMetadataValidationError.DESCRIPTION_REQUIRED
        } else if (
            TaskTextComposer.codePointCount(composedDescription) >
            MAX_TASK_DESCRIPTION_CODE_POINTS
        ) {
            errors += TaskMetadataValidationError.DESCRIPTION_TOO_LONG
        }
        if (
            TaskTextComposer.codePointCount(composedPurchases) >
            MAX_TASK_PURCHASES_CODE_POINTS
        ) {
            errors += TaskMetadataValidationError.PURCHASES_TOO_LONG
        }
        if (normalizedNotes.codePointCount(0, normalizedNotes.length) > MAX_TASK_NOTES_CODE_POINTS) {
            errors += TaskMetadataValidationError.NOTES_TOO_LONG
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
                    billingStatus = billingStatus,
                    mileage = normalizedMileage,
                    notes = normalizedNotes,
                ),
            )
        } else {
            TaskMetadataValidationResult.Invalid(errors)
        }
    }
}
