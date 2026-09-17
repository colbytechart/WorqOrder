package worq.order.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.TagMutationResult
import worq.order.data.TagRepository
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationError
import worq.order.data.TagTextValidationResult
import worq.order.domain.TagCsvImportCoordinator
import worq.order.domain.TagCsvImportResult
import worq.order.model.Tag
import worq.order.model.TagCategory

class TagCategoryManagementViewModel(
    private val category: TagCategory,
    private val tagRepository: TagRepository,
    private val tagCsvImportCoordinator: TagCsvImportCoordinator,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TagCategoryManagementUiState(category = category))
    val uiState: StateFlow<TagCategoryManagementUiState> = mutableUiState
    private val searchQuery = MutableStateFlow("")
    private var observationJob: Job? = null

    init {
        observeTags()
    }

    fun onEvent(event: TagCategoryManagementEvent) {
        when (event) {
            TagCategoryManagementEvent.Retry -> observeTags()
            is TagCategoryManagementEvent.EditSearch -> {
                mutableUiState.update { it.copy(searchQuery = event.query) }
                searchQuery.value = event.query
            }
            TagCategoryManagementEvent.ClearSearch -> {
                mutableUiState.update { it.copy(searchQuery = "") }
                searchQuery.value = ""
            }
            TagCategoryManagementEvent.OpenAddTag -> openAddTag()
            is TagCategoryManagementEvent.OpenEditTag -> openEditTag(event.tagId)
            is TagCategoryManagementEvent.RequestDeleteTag -> requestDeleteTag(event.tagId)
            TagCategoryManagementEvent.ConfirmDeleteTag -> confirmDeleteTag()
            TagCategoryManagementEvent.DismissDeleteTag ->
                mutableUiState.update { it.copy(deleteConfirmation = null) }
            is TagCategoryManagementEvent.EditTagText ->
                mutableUiState.update { state ->
                    state.copy(editor = state.editor?.copy(text = event.text, fieldError = null))
                }
            TagCategoryManagementEvent.ConfirmEditor -> confirmEditor()
            TagCategoryManagementEvent.DismissEditor ->
                mutableUiState.update { it.copy(editor = null) }
            TagCategoryManagementEvent.ImportCsv -> Unit
            is TagCategoryManagementEvent.ImportCsvDocumentSelected -> importCsv(event.documentUri)
            TagCategoryManagementEvent.DismissImportStatus ->
                mutableUiState.update { it.copy(importSummary = null, importFailure = null) }
            TagCategoryManagementEvent.DismissMessage ->
                mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeTags() {
        observationJob?.cancel()
        // Keep the search field mounted while a new query switches its backing Flow. Showing the
        // full-screen loading state for every character would clear focus and make live search
        // unusable. Loading is reserved for the initial load and an explicit retry.
        mutableUiState.update { it.copy(isLoading = true, hasLoadError = false) }
        observationJob =
            searchQuery
                .flatMapLatest { query -> tagRepository.observeTags(category, query) }
                .onEach { tags ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadError = false,
                            tags = tags.map(Tag::toUi),
                        )
                    }
                }.catch {
                    mutableUiState.update { it.copy(isLoading = false, hasLoadError = true) }
                }.launchIn(viewModelScope)
    }

    private fun openAddTag() {
        if (!mutationsAllowed()) return
        mutableUiState.update {
            it.copy(
                editor = TagEditorUiState(mode = TagEditorMode.ADD),
                message = null,
            )
        }
    }

    private fun openEditTag(tagId: String) {
        if (!mutationsAllowed()) return
        viewModelScope.launch {
            val tag =
                runCatching { tagRepository.readTag(tagId) }
                    .getOrElse {
                        mutableUiState.update { state ->
                            state.copy(message = TagManagementMessage.DATA_UNAVAILABLE)
                        }
                        return@launch
                    }
            if (tag == null || tag.category != category) {
                mutableUiState.update { it.copy(message = TagManagementMessage.TAG_NOT_FOUND) }
                return@launch
            }
            mutableUiState.update {
                it.copy(
                    editor =
                        TagEditorUiState(
                            mode = TagEditorMode.EDIT,
                            tagId = tag.id,
                            text = tag.text,
                        ),
                    message = null,
                )
            }
        }
    }

    private fun requestDeleteTag(tagId: String) {
        if (!mutationsAllowed()) return
        val tag = mutableUiState.value.tags.firstOrNull { it.id == tagId } ?: return
        mutableUiState.update {
            it.copy(
                deleteConfirmation = DeleteTagConfirmation(tagId = tag.id, tagText = tag.text),
                message = null,
            )
        }
    }

    private fun confirmEditor() {
        val editor = mutableUiState.value.editor ?: return
        if (editor.isSaving || mutableUiState.value.isImporting) return
        when (val validation = TagTextNormalizer.validate(editor.text)) {
            is TagTextValidationResult.Invalid -> {
                mutableUiState.update {
                    it.copy(editor = editor.copy(fieldError = validation.error.toFieldError()))
                }
                return
            }
            is TagTextValidationResult.Valid -> Unit
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(editor = editor.copy(isSaving = true)) }
            val result =
                try {
                    when (editor.mode) {
                        TagEditorMode.ADD -> tagRepository.createTag(category, editor.text)
                        TagEditorMode.EDIT ->
                            tagRepository.updateTag(requireNotNull(editor.tagId), editor.text)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutableUiState.update {
                        it.copy(
                            editor = editor.copy(isSaving = false),
                            message = TagManagementMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            handleEditorResult(editor, result)
        }
    }

    private fun handleEditorResult(
        editor: TagEditorUiState,
        result: TagMutationResult,
    ) {
        mutableUiState.update { state ->
            when (result) {
                is TagMutationResult.Created,
                is TagMutationResult.Updated,
                -> state.copy(editor = null, message = null)
                is TagMutationResult.InvalidText ->
                    state.copy(
                        editor = editor.copy(fieldError = result.reason.toFieldError(), isSaving = false),
                    )
                is TagMutationResult.DuplicateNormalizedText ->
                    state.copy(
                        editor = editor.copy(fieldError = TagFieldError.DUPLICATE, isSaving = false),
                    )
                TagMutationResult.NotFound ->
                    state.copy(editor = null, message = TagManagementMessage.TAG_NOT_FOUND)
                TagMutationResult.Deleted ->
                    state.copy(editor = null, message = TagManagementMessage.DATA_UNAVAILABLE)
            }
        }
    }

    private fun confirmDeleteTag() {
        val confirmation = mutableUiState.value.deleteConfirmation ?: return
        if (confirmation.isDeleting || mutableUiState.value.isImporting) return
        mutableUiState.update {
            it.copy(deleteConfirmation = confirmation.copy(isDeleting = true))
        }
        viewModelScope.launch {
            val result =
                try {
                    tagRepository.deleteTag(confirmation.tagId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutableUiState.update {
                        it.copy(
                            deleteConfirmation = null,
                            message = TagManagementMessage.DATA_UNAVAILABLE,
                        )
                    }
                    return@launch
                }
            mutableUiState.update { state ->
                when (result) {
                    TagMutationResult.Deleted -> state.copy(deleteConfirmation = null, message = null)
                    TagMutationResult.NotFound ->
                        state.copy(
                            deleteConfirmation = null,
                            message = TagManagementMessage.TAG_NOT_FOUND,
                        )
                    else ->
                        state.copy(
                            deleteConfirmation = null,
                            message = TagManagementMessage.DATA_UNAVAILABLE,
                        )
                }
            }
        }
    }

    private fun importCsv(documentUri: String?) {
        if (documentUri == null || mutableUiState.value.isImporting || !mutationsAllowed()) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isImporting = true,
                    importSummary = null,
                    importFailure = null,
                    message = null,
                )
            }
            when (val result = tagCsvImportCoordinator.import(category, documentUri)) {
                is TagCsvImportResult.Success ->
                    mutableUiState.update {
                        it.copy(
                            isImporting = false,
                            importSummary =
                                TagImportSummaryUi(
                                    addedCount = result.summary.addedCount,
                                    skippedCount = result.summary.skippedCount,
                                ),
                        )
                    }
                is TagCsvImportResult.Failed ->
                    mutableUiState.update {
                        it.copy(
                            isImporting = false,
                            importFailure =
                                TagImportFailureUi(
                                    failure = result.failure,
                                    recordNumber = result.recordNumber,
                                    columnNumber = result.columnNumber,
                                ),
                        )
                    }
            }
        }
    }

    private fun mutationsAllowed(): Boolean {
        val state = mutableUiState.value
        return !state.isImporting &&
            state.editor?.isSaving != true &&
            state.deleteConfirmation?.isDeleting != true &&
            state.editor == null &&
            state.deleteConfirmation == null
    }

    class Factory(
        private val category: TagCategory,
        private val tagRepository: TagRepository,
        private val tagCsvImportCoordinator: TagCsvImportCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TagCategoryManagementViewModel::class.java))
            return TagCategoryManagementViewModel(
                category = category,
                tagRepository = tagRepository,
                tagCsvImportCoordinator = tagCsvImportCoordinator,
            ) as T
        }
    }
}

private fun Tag.toUi(): TagItemUi = TagItemUi(id = id, text = text)

private fun TagTextValidationError.toFieldError(): TagFieldError =
    when (this) {
        TagTextValidationError.BLANK -> TagFieldError.BLANK
        TagTextValidationError.TOO_LONG -> TagFieldError.TOO_LONG
    }
