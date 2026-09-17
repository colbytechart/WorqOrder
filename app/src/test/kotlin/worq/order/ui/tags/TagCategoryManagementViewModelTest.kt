package worq.order.ui.tags

import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.TagMutationResult
import worq.order.data.TagRepository
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationResult
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.TagImportApplyResult
import worq.order.data.TagImportCandidate
import worq.order.data.TagImportRepository
import worq.order.domain.TagCsvImportCoordinator
import worq.order.model.Tag
import worq.order.model.TagCategory
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TagCategoryManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun overviewReportsBothCatalogCountsAndEmitsCategoryNavigation() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeTagRepository(
                    listOf(
                        testTag("description", TagCategory.DESCRIPTION, "Description"),
                        testTag("purchase-1", TagCategory.HARDWARE_SOFTWARE_PURCHASE, "Purchase one"),
                        testTag("purchase-2", TagCategory.HARDWARE_SOFTWARE_PURCHASE, "Purchase two"),
                    ),
                )
            val viewModel = TagManagementViewModel(repository)
            val effects = mutableListOf<TagManagementEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }
            runCurrent()

            assertEquals(1, viewModel.uiState.value.descriptionTagCount)
            assertEquals(2, viewModel.uiState.value.purchaseTagCount)
            viewModel.onEvent(TagManagementEvent.OpenHardwareSoftwarePurchaseTags)
            runCurrent()
            assertEquals(
                listOf(
                    TagManagementEffect.NavigateToCategory(
                        TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                    ),
                ),
                effects,
            )
        }

    @Test
    fun addNormalizesTextAndReportsBlankTooLongAndDuplicateErrors() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTagRepository(listOf(testTag("alpha", TagCategory.DESCRIPTION, "Alpha")))
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(TagCategoryManagementEvent.OpenAddTag)
            viewModel.onEvent(TagCategoryManagementEvent.EditTagText("  Beta   Group "))
            viewModel.onEvent(TagCategoryManagementEvent.ConfirmEditor)
            runCurrent()
            assertEquals("Beta Group", repository.tags.value.single { it.id == "tag-2" }.text)
            assertNull(viewModel.uiState.value.editor)

            viewModel.onEvent(TagCategoryManagementEvent.OpenAddTag)
            viewModel.onEvent(TagCategoryManagementEvent.EditTagText("   "))
            viewModel.onEvent(TagCategoryManagementEvent.ConfirmEditor)
            assertEquals(TagFieldError.BLANK, viewModel.uiState.value.editor?.fieldError)

            viewModel.onEvent(TagCategoryManagementEvent.EditTagText("x".repeat(401)))
            viewModel.onEvent(TagCategoryManagementEvent.ConfirmEditor)
            assertEquals(TagFieldError.TOO_LONG, viewModel.uiState.value.editor?.fieldError)

            viewModel.onEvent(TagCategoryManagementEvent.EditTagText(" ALPHA. "))
            viewModel.onEvent(TagCategoryManagementEvent.ConfirmEditor)
            runCurrent()
            assertEquals(TagFieldError.DUPLICATE, viewModel.uiState.value.editor?.fieldError)
            assertEquals(2, repository.tags.value.size)
        }

    @Test
    fun searchIsCaseInsensitiveAndClearRestoresAlphabeticalList() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeTagRepository(
                    listOf(
                        testTag("z", TagCategory.DESCRIPTION, "Zulu"),
                        testTag("b", TagCategory.DESCRIPTION, "Beta Group"),
                        testTag("a", TagCategory.DESCRIPTION, "Alpha"),
                        testTag("p", TagCategory.HARDWARE_SOFTWARE_PURCHASE, "Purchase"),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)
            assertEquals(listOf("Alpha", "Beta Group", "Zulu"), viewModel.uiState.value.tags.map(TagItemUi::text))

            viewModel.onEvent(TagCategoryManagementEvent.EditSearch("GROUP"))
            runCurrent()
            assertEquals(listOf("Beta Group"), viewModel.uiState.value.tags.map(TagItemUi::text))

            viewModel.onEvent(TagCategoryManagementEvent.ClearSearch)
            runCurrent()
            assertEquals(listOf("Alpha", "Beta Group", "Zulu"), viewModel.uiState.value.tags.map(TagItemUi::text))
        }

    @Test
    fun deleteRequiresConfirmationAndRemovesOnlyCatalogRecord() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTagRepository(listOf(testTag("tag-1", TagCategory.DESCRIPTION, "Keep history")))
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(TagCategoryManagementEvent.RequestDeleteTag("tag-1"))
            assertEquals("Keep history", viewModel.uiState.value.deleteConfirmation?.tagText)
            assertEquals(1, repository.tags.value.size)

            viewModel.onEvent(TagCategoryManagementEvent.ConfirmDeleteTag)
            runCurrent()
            assertNull(viewModel.uiState.value.deleteConfirmation)
            assertTrue(repository.tags.value.isEmpty())

            // SAF cancellation returns without starting an import or mutating the catalog.
            viewModel.onEvent(TagCategoryManagementEvent.ImportCsvDocumentSelected(null))
            runCurrent()
            assertFalse(viewModel.uiState.value.isImporting)
        }

    @Test
    fun staleEditAndDeleteShowActionableNotFoundState() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTagRepository(listOf(testTag("tag-1", TagCategory.DESCRIPTION, "Existing")))
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(TagCategoryManagementEvent.OpenEditTag("missing"))
            runCurrent()
            assertEquals(TagManagementMessage.TAG_NOT_FOUND, viewModel.uiState.value.message)

            viewModel.onEvent(TagCategoryManagementEvent.RequestDeleteTag("missing"))
            assertNull(viewModel.uiState.value.deleteConfirmation)
        }

    private fun TestScope.viewModelAndCollect(repository: FakeTagRepository): TagCategoryManagementViewModel {
        val viewModel =
            TagCategoryManagementViewModel(
                category = TagCategory.DESCRIPTION,
                tagRepository = repository,
                tagCsvImportCoordinator =
                    TagCsvImportCoordinator(
                        documentSource = NoOpDocumentSource,
                        repository = NoOpTagImportRepository,
                    ),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        runCurrent()
        return viewModel
    }

    private class FakeTagRepository(initial: List<Tag>) : TagRepository {
        val tags = MutableStateFlow(initial)
        private var nextId = initial.size + 1

        override fun observeTags(category: TagCategory, searchQuery: String): Flow<List<Tag>> =
            tags.map { values ->
                val query =
                    TagTextNormalizer
                        .collapseWhitespace(searchQuery)
                        .lowercase(Locale.ROOT)
                values
                    .filter { it.category == category }
                    .filter {
                        query.isEmpty() || it.text.lowercase(Locale.ROOT).contains(query)
                    }
                    .sortedWith(
                        compareBy<Tag> { it.text.lowercase(Locale.ROOT) }
                            .thenBy { it.text }
                            .thenBy { it.id },
                    )
            }

        override suspend fun readTag(tagId: String): Tag? = tags.value.firstOrNull { it.id == tagId }

        override suspend fun createTag(category: TagCategory, text: String): TagMutationResult {
            val normalized = TagTextNormalizer.validate(text)
            if (normalized is TagTextValidationResult.Invalid) {
                return TagMutationResult.InvalidText(normalized.error)
            }
            val value = (normalized as TagTextValidationResult.Valid).text
            tags.value.firstOrNull { it.category == category && it.normalizedText == value.normalizedText }?.let {
                return TagMutationResult.DuplicateNormalizedText(it.id)
            }
            val tag = testTag("tag-${nextId++}", category, value.displayText, value.normalizedText)
            tags.value += tag
            return TagMutationResult.Created(tag)
        }

        override suspend fun updateTag(tagId: String, text: String): TagMutationResult {
            val current = tags.value.firstOrNull { it.id == tagId } ?: return TagMutationResult.NotFound
            val normalized = TagTextNormalizer.validate(text)
            if (normalized is TagTextValidationResult.Invalid) {
                return TagMutationResult.InvalidText(normalized.error)
            }
            val value = (normalized as TagTextValidationResult.Valid).text
            tags.value.firstOrNull {
                it.id != tagId && it.category == current.category && it.normalizedText == value.normalizedText
            }?.let { return TagMutationResult.DuplicateNormalizedText(it.id) }
            val changed = current.copy(text = value.displayText, normalizedText = value.normalizedText)
            tags.value = tags.value.map { if (it.id == tagId) changed else it }
            return TagMutationResult.Updated(changed)
        }

        override suspend fun deleteTag(tagId: String): TagMutationResult {
            if (tags.value.none { it.id == tagId }) return TagMutationResult.NotFound
            tags.value = tags.value.filterNot { it.id == tagId }
            return TagMutationResult.Deleted
        }
    }

    private object NoOpDocumentSource : ClientCsvDocumentSource {
        override suspend fun read(documentUri: String): ClientCsvDocumentReadResult =
            error("CSV source is not used by this test")
    }

    private object NoOpTagImportRepository : TagImportRepository {
        override suspend fun applyImport(
            category: TagCategory,
            candidates: List<TagImportCandidate>,
        ): TagImportApplyResult = error("CSV repository is not used by this test")
    }
}

private fun testTag(
    id: String,
    category: TagCategory,
    text: String,
    normalizedText: String = TagTextNormalizer.canonicalize(text),
) =
    Tag(
        id = id,
        category = category,
        text = text,
        normalizedText = normalizedText,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
