package worq.order.ui.tasks

import java.util.Locale
import worq.order.data.MAX_COMPOSED_TASK_TEXT_CODE_POINTS
import worq.order.data.MAX_TASK_NOTES_CODE_POINTS
import worq.order.data.TaskMetadataValidationError
import worq.order.data.TaskTextComposer
import worq.order.model.Tag
import worq.order.model.TagCategory
import worq.order.model.TaskTagSnapshotDraft
import worq.order.ui.tags.TagChipUi
import worq.order.ui.tags.TagPickerItemUi

/** The task text field to which a reusable Tag belongs. */
enum class TaskTagField {
    DESCRIPTION,
    HARDWARE_SOFTWARE_PURCHASES,
}

/** A task-owned, ordered Tag-text snapshot being edited in a task form. */
data class TaskTagSelectionUi(
    val id: String,
    val text: String,
    val sourceTagId: String? = null,
)

/** A current catalog record available to a task form. */
data class TaskTagCatalogItemUi(
    val id: String,
    val text: String,
)

data class TaskTagPickerUiState(
    val field: TaskTagField,
    val searchQuery: String = "",
    val draftSelections: List<TaskTagSelectionUi> = emptyList(),
    val error: TaskTagPickerError? = null,
)

enum class TaskTagPickerError {
    COMPOSED_TEXT_TOO_LONG,
    DATA_UNAVAILABLE,
}

enum class TaskTagInlineEditorError {
    BLANK,
    TOO_LONG,
    DATA_UNAVAILABLE,
}

data class TaskTagInlineEditorUiState(
    val field: TaskTagField,
    val text: String = "",
    val error: TaskTagInlineEditorError? = null,
    val isSaving: Boolean = false,
)

internal fun TaskTagField.category(): TagCategory =
    when (this) {
        TaskTagField.DESCRIPTION -> TagCategory.DESCRIPTION
        TaskTagField.HARDWARE_SOFTWARE_PURCHASES -> TagCategory.HARDWARE_SOFTWARE_PURCHASE
    }

internal fun List<TaskTagSelectionUi>.toSnapshotDrafts(): List<TaskTagSnapshotDraft> =
    map { selection ->
        TaskTagSnapshotDraft(
            text = selection.text,
            sourceTagId = selection.sourceTagId,
        )
    }

internal fun List<TaskTagSelectionUi>.toChipUi(): List<TagChipUi> =
    map { selection -> TagChipUi(id = selection.id, text = selection.text) }

/**
 * Produces a stable catalog-order picker view without ever replacing a task's stored snapshot.
 * A selected saved version occupies its catalog slot; deleted sources remain selected/removable
 * rows at the end of the list.
 */
internal fun pickerItemsFor(
    selections: List<TaskTagSelectionUi>,
    catalog: List<TaskTagCatalogItemUi>,
    query: String,
): List<TagPickerItemUi> {
    val selectionsBySource = selections.mapNotNull { selection ->
        selection.sourceTagId?.let { sourceTagId -> sourceTagId to selection }
    }.toMap()
    val catalogIds = catalog.map(TaskTagCatalogItemUi::id).toSet()
    val catalogRows =
        catalog.map { catalogItem ->
            selectionsBySource[catalogItem.id]?.let { selection ->
                TagPickerItemUi(
                    id = selection.id,
                    text = selection.text,
                    isSelected = true,
                    isStale = selection.text != catalogItem.text,
                )
            } ?: TagPickerItemUi(
                id = catalogItem.id,
                text = catalogItem.text,
                isSelected = false,
            )
        }
    val deletedSourceRows =
        selections
            .filter { selection ->
                selection.sourceTagId == null || selection.sourceTagId !in catalogIds
            }.map { selection ->
                TagPickerItemUi(
                    id = selection.id,
                    text = selection.text,
                    isSelected = true,
                    isStale = true,
                )
            }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return (catalogRows + deletedSourceRows).filter { item ->
        normalizedQuery.isEmpty() || item.text.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
}

/** Selects every currently visible catalog row while preserving hidden selections and order. */
internal fun selectAllVisiblePickerItems(
    picker: TaskTagPickerUiState,
    catalog: List<TaskTagCatalogItemUi>,
): List<TaskTagSelectionUi> {
    val visibleUnselectedSourceIds =
        pickerItemsFor(picker.draftSelections, catalog, picker.searchQuery)
            .filterNot(TagPickerItemUi::isSelected)
            .map(TagPickerItemUi::id)
            .toSet()
    val additions =
        catalog
            .filter { item -> item.id in visibleUnselectedSourceIds }
            .map(::newTaskTagSelection)
    return picker.draftSelections + additions
}

/** Deselects currently visible rows without changing selections hidden by the active search. */
internal fun deselectAllVisiblePickerItems(
    picker: TaskTagPickerUiState,
    catalog: List<TaskTagCatalogItemUi>,
): List<TaskTagSelectionUi> {
    val visibleSelectionIds =
        pickerItemsFor(picker.draftSelections, catalog, picker.searchQuery)
            .filter(TagPickerItemUi::isSelected)
            .map(TagPickerItemUi::id)
            .toSet()
    return picker.draftSelections.filterNot { selection -> selection.id in visibleSelectionIds }
}

internal fun composedTaskText(
    manualText: String,
    selections: List<TaskTagSelectionUi>,
): String = TaskTextComposer.compose(manualText, selections.map(TaskTagSelectionUi::text))

internal fun TaskTagSelectionUi.updatedCatalogText(
    catalog: List<TaskTagCatalogItemUi>,
): String? =
    sourceTagId
        ?.let { sourceId -> catalog.firstOrNull { it.id == sourceId } }
        ?.text
        ?.takeIf { it != text }

internal fun newTaskTagSelection(
    catalogItem: TaskTagCatalogItemUi,
): TaskTagSelectionUi =
    TaskTagSelectionUi(
        id = "source:${catalogItem.id}",
        sourceTagId = catalogItem.id,
        text = catalogItem.text,
    )

internal fun Tag.toTaskTagCatalogItem(): TaskTagCatalogItemUi =
    TaskTagCatalogItemUi(
        id = id,
        text = text,
    )

internal fun List<TaskTagCatalogItemUi>.upsert(
    item: TaskTagCatalogItemUi,
): List<TaskTagCatalogItemUi> =
    (filterNot { it.id == item.id } + item).sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER, TaskTagCatalogItemUi::text)
            .thenBy(TaskTagCatalogItemUi::text)
            .thenBy(TaskTagCatalogItemUi::id),
    )

internal fun projectedTagTextErrors(
    description: String,
    descriptionSelections: List<TaskTagSelectionUi>,
    purchases: String,
    purchaseSelections: List<TaskTagSelectionUi>,
    notes: String = "",
): Set<TaskMetadataValidationError> =
    buildSet {
        if (
            composedTaskText(description, descriptionSelections).codePointCount() >
                MAX_COMPOSED_TASK_TEXT_CODE_POINTS
        ) {
            add(TaskMetadataValidationError.DESCRIPTION_TOO_LONG)
        }
        if (
            composedTaskText(purchases, purchaseSelections).codePointCount() >
                MAX_COMPOSED_TASK_TEXT_CODE_POINTS
        ) {
            add(TaskMetadataValidationError.PURCHASES_TOO_LONG)
        }
        if (notes.codePointCount() > MAX_TASK_NOTES_CODE_POINTS) {
            add(TaskMetadataValidationError.NOTES_TOO_LONG)
        }
    }

private fun String.codePointCount(): Int = codePointCount(0, length)
