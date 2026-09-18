package worq.order.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.model.TagCategory
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class TagManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overviewExposesBothCategoryRoutes() {
        val events = mutableListOf<TagManagementEvent>()
        setContent {
            TagManagementScreen(
                uiState =
                    TagManagementUiState(
                        isLoading = false,
                        descriptionTagCount = 2,
                        purchaseTagCount = 1,
                    ),
                onEvent = events::add,
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Description Tags").performClick()
        composeRule.onNodeWithText("Hardware / Software Purchase Tags").performClick()

        assertEquals(
            listOf(
                TagManagementEvent.OpenDescriptionTags,
                TagManagementEvent.OpenHardwareSoftwarePurchaseTags,
            ),
            events,
        )
    }

    @Test
    fun emptyCategoryOffersSearchAddImportAndGuidance() {
        val events = mutableListOf<TagCategoryManagementEvent>()
        var importRequests = 0
        setContent {
            TagCategoryManagementScreen(
                uiState =
                    TagCategoryManagementUiState(
                        category = TagCategory.DESCRIPTION,
                        isLoading = false,
                    ),
                onEvent = events::add,
                onNavigateBack = {},
                onImportCsv = { importRequests += 1 },
            )
        }

        composeRule.onNodeWithText("Search tags").assertIsDisplayed()
        composeRule.onNodeWithText("Add tag").performClick()
        composeRule.onNodeWithText("Import From CSV").performClick()
        composeRule.onNodeWithText("No Description Tags").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add a reusable Tag or import Tags from a CSV file.")
            .assertIsDisplayed()

        assertEquals(
            listOf(
                TagCategoryManagementEvent.OpenAddTag,
                TagCategoryManagementEvent.ImportCsv,
            ),
            events,
        )
        assertEquals(1, importRequests)
    }

    @Test
    fun searchClearAndRowActionsEmitTypedEvents() {
        val events = mutableListOf<TagCategoryManagementEvent>()
        var state by
            mutableStateOf(
                TagCategoryManagementUiState(
                    category = TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                    isLoading = false,
                    tags = listOf(TagItemUi("tag-1", "Install monitor")),
                ),
            )
        setContent {
            TagCategoryManagementScreen(
                uiState = state,
                onEvent = { event ->
                    events += event
                    state =
                        when (event) {
                            is TagCategoryManagementEvent.EditSearch ->
                                state.copy(searchQuery = event.query)
                            TagCategoryManagementEvent.ClearSearch ->
                                state.copy(searchQuery = "")
                            else -> state
                        }
                },
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Search tags").performTextInput("monitor")
        composeRule
            .onNodeWithContentDescription("Clear tag search")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription("Edit tag Install monitor").performClick()
        composeRule.onNodeWithContentDescription("Delete tag Install monitor").performClick()

        assertEquals(
            listOf(
                TagCategoryManagementEvent.EditSearch("monitor"),
                TagCategoryManagementEvent.ClearSearch,
                TagCategoryManagementEvent.OpenEditTag("tag-1"),
                TagCategoryManagementEvent.RequestDeleteTag("tag-1"),
            ),
            events,
        )
    }

    @Test
    fun editorExposesAccessibleValidationMessage() {
        setContent {
            TagCategoryManagementScreen(
                uiState =
                    TagCategoryManagementUiState(
                        category = TagCategory.DESCRIPTION,
                        isLoading = false,
                        editor =
                            TagEditorUiState(
                                mode = TagEditorMode.EDIT,
                                tagId = "tag-1",
                                text = "Install monitor",
                                fieldError = TagFieldError.DUPLICATE,
                            ),
                    ),
                onEvent = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Edit Tag").assertIsDisplayed()
        composeRule
            .onNodeWithText("A Tag with this text already exists.")
            .assertIsDisplayed()
        composeRule
            .onNode(
                matcher =
                    androidx.compose.ui.test.SemanticsMatcher.expectValue(
                        SemanticsProperties.Error,
                        "A Tag with this text already exists.",
                    ),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
    }

    @Test
    fun editorShowsLiveCodePointCountAndBlocksOverlengthSave() {
        var editorText by mutableStateOf("x".repeat(400))
        setContent {
            TagCategoryManagementScreen(
                uiState =
                    TagCategoryManagementUiState(
                        category = TagCategory.DESCRIPTION,
                        isLoading = false,
                        editor =
                            TagEditorUiState(
                                mode = TagEditorMode.ADD,
                                text = editorText,
                            ),
                    ),
                onEvent = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("400 / 400").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()

        editorText = "x".repeat(401)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Character limit: 401 / 400").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun deleteConfirmationExplainsHistoricalPreservation() {
        setContent {
            TagCategoryManagementScreen(
                uiState =
                    TagCategoryManagementUiState(
                        category = TagCategory.DESCRIPTION,
                        isLoading = false,
                        deleteConfirmation =
                            DeleteTagConfirmation(
                                tagId = "tag-1",
                                tagText = "Install monitor",
                            ),
                    ),
                onEvent = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Delete Tag?").assertIsDisplayed()
        composeRule
            .onNodeWithText("Existing tasks keep their saved text.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun importSummaryAndFailureAreDisplayedAsAccessibleStatus() {
        var state by
            mutableStateOf(
                TagCategoryManagementUiState(
                    category = TagCategory.DESCRIPTION,
                    isLoading = false,
                    importSummary = TagImportSummaryUi(addedCount = 2, skippedCount = 1),
                ),
            )
        setContent {
            TagCategoryManagementScreen(
                uiState = state,
                onEvent = {},
                onNavigateBack = {},
            )
        }

        composeRule
            .onNodeWithText("Tag import complete. Added: 2. Skipped: 1.")
            .assertIsDisplayed()

        state =
            state.copy(
                importSummary = null,
                importFailure =
                    TagImportFailureUi(
                        failure = worq.order.domain.TagCsvImportFailure.MALFORMED_CSV,
                        recordNumber = 2,
                        columnNumber = 1,
                    ),
            )
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("This CSV has invalid quoting or structure. Row 2, column 1.")
            .assertIsDisplayed()
    }

    @Test
    fun loadingAndErrorStatesAreRenderedWithoutExposingImplementationDetails() {
        setContent {
            TagCategoryManagementScreen(
                uiState =
                    TagCategoryManagementUiState(
                        category = TagCategory.DESCRIPTION,
                        isLoading = false,
                        hasLoadError = true,
                    ),
                onEvent = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Tags could not be loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                content()
            }
        }
    }
}
