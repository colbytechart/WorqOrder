package worq.order.ui.tags

import worq.order.domain.TagCsvImportFailure
import worq.order.model.TagCategory

data class TagItemUi(
    val id: String,
    val text: String,
)

data class TagManagementUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val descriptionTagCount: Int = 0,
    val purchaseTagCount: Int = 0,
)

sealed interface TagManagementEvent {
    data object Retry : TagManagementEvent

    data object OpenDescriptionTags : TagManagementEvent

    data object OpenHardwareSoftwarePurchaseTags : TagManagementEvent
}

sealed interface TagManagementEffect {
    data class NavigateToCategory(
        val category: TagCategory,
    ) : TagManagementEffect
}

enum class TagEditorMode {
    ADD,
    EDIT,
}

enum class TagFieldError {
    BLANK,
    TOO_LONG,
    DUPLICATE,
}

data class TagEditorUiState(
    val mode: TagEditorMode,
    val tagId: String? = null,
    val text: String = "",
    val fieldError: TagFieldError? = null,
    val isSaving: Boolean = false,
)

data class DeleteTagConfirmation(
    val tagId: String,
    val tagText: String,
    val isDeleting: Boolean = false,
)

enum class TagManagementMessage {
    DATA_UNAVAILABLE,
    TAG_NOT_FOUND,
}

data class TagImportSummaryUi(
    val addedCount: Int,
    val skippedCount: Int,
)

data class TagImportFailureUi(
    val failure: TagCsvImportFailure,
    val recordNumber: Int?,
    val columnNumber: Int?,
)

data class TagCategoryManagementUiState(
    val category: TagCategory,
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    /** The ViewModel supplies the currently visible, case-insensitively filtered A-Z list. */
    val tags: List<TagItemUi> = emptyList(),
    val searchQuery: String = "",
    val editor: TagEditorUiState? = null,
    val deleteConfirmation: DeleteTagConfirmation? = null,
    val message: TagManagementMessage? = null,
    val isImporting: Boolean = false,
    val importSummary: TagImportSummaryUi? = null,
    val importFailure: TagImportFailureUi? = null,
)

sealed interface TagCategoryManagementEvent {
    data object Retry : TagCategoryManagementEvent

    data class EditSearch(
        val query: String,
    ) : TagCategoryManagementEvent

    data object ClearSearch : TagCategoryManagementEvent

    data object OpenAddTag : TagCategoryManagementEvent

    data class OpenEditTag(
        val tagId: String,
    ) : TagCategoryManagementEvent

    data class RequestDeleteTag(
        val tagId: String,
    ) : TagCategoryManagementEvent

    data object ConfirmDeleteTag : TagCategoryManagementEvent

    data object DismissDeleteTag : TagCategoryManagementEvent

    data class EditTagText(
        val text: String,
    ) : TagCategoryManagementEvent

    data object ConfirmEditor : TagCategoryManagementEvent

    data object DismissEditor : TagCategoryManagementEvent

    /** The screen handles this by launching the user-mediated OpenDocument contract. */
    data object ImportCsv : TagCategoryManagementEvent

    data class ImportCsvDocumentSelected(
        val documentUri: String?,
    ) : TagCategoryManagementEvent

    data object DismissImportStatus : TagCategoryManagementEvent

    data object DismissMessage : TagCategoryManagementEvent
}
