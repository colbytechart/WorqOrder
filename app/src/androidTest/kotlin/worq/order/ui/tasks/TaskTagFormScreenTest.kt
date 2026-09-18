package worq.order.ui.tasks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class TaskTagFormScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createFormShowsFieldAssociatedChipsAndOpensDescriptionPicker() {
        val events = mutableListOf<CreateTaskEvent>()
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                CreateTaskScreen(
                    uiState =
                        CreateTaskUiState(
                            isLoadingClients = false,
                            activeClients = listOf(ClientItemUi("client", "Client")),
                            selectedClientId = "client",
                            isLoadingConsultant = false,
                            selectedConsultantId = "employee",
                            descriptionTagSelections =
                                listOf(TaskTagSelectionUi("tag-1", "Install monitor", "tag-1")),
                            descriptionCatalogTags =
                                listOf(
                                    TaskTagCatalogItemUi("tag-1", "Install monitor"),
                                    TaskTagCatalogItemUi("tag-2", "Configure network"),
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onAllNodesWithText("Description Tags").assertCountEquals(0)
        composeRule.onAllNodesWithText("Hardware / Software Purchase Tags").assertCountEquals(0)
        composeRule.onNodeWithText("Add tags").assertIsDisplayed()
        composeRule.onNodeWithText("Edit tags").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Edit selected tag Install monitor")
            .assertIsDisplayed()
            .performClick()

        assertTrue(
            events == listOf(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION)),
        )
    }

    @Test
    fun editPickerExposesSelectedStateAndCancelEvent() {
        val events = mutableListOf<EditTaskEvent>()
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                EditTaskScreen(
                    uiState =
                        EditTaskUiState(
                            taskId = "task",
                            isLoading = false,
                            workDate = LocalDate.of(2026, 7, 25),
                            activeClients = listOf(ClientItemUi("client", "Client")),
                            selectedClientId = "client",
                            activeConsultants = emptyList(),
                            tagPicker =
                                TaskTagPickerUiState(
                                    field = TaskTagField.DESCRIPTION,
                                    draftSelections =
                                        listOf(
                                            TaskTagSelectionUi(
                                                id = "tag-1",
                                                text = "Install monitor",
                                                sourceTagId = "tag-1",
                                            ),
                                        ),
                                ),
                            descriptionCatalogTags =
                                listOf(
                                    TaskTagCatalogItemUi("tag-1", "Install monitor"),
                                    TaskTagCatalogItemUi("tag-2", "Configure network"),
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Install monitor")
            .assertIsSelected()
        composeRule.onNodeWithText("Select All").performClick()
        composeRule.onNodeWithText("Deselect All").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(events.contains(EditTaskEvent.SelectAllVisibleTagPickerItems))
        assertTrue(events.contains(EditTaskEvent.DeselectAllVisibleTagPickerItems))
        assertTrue(events.contains(EditTaskEvent.DismissTagPicker))
    }

    @Test
    fun inlineTagEditorShowsLiveCodePointCountAndBlocksOverlengthConfirmation() {
        val editor =
            androidx.compose.runtime.mutableStateOf(
                TaskTagInlineEditorUiState(field = TaskTagField.DESCRIPTION),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                TaskTagInlineEditorDialog(
                    editor = editor.value,
                    onTextChanged = { editor.value = editor.value.copy(text = it) },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("0 / 400").assertIsDisplayed()
        composeRule.onNodeWithText("Create and Select").assertIsEnabled()

        composeRule.runOnIdle {
            editor.value = editor.value.copy(text = "x".repeat(401))
        }
        composeRule.onNodeWithText("Character limit: 401 / 400").assertIsDisplayed()
        composeRule.onNodeWithText("Create and Select").assertIsNotEnabled()

        composeRule.runOnIdle {
            editor.value = editor.value.copy(text = "x".repeat(400))
        }
        composeRule.onNodeWithText("400 / 400").assertIsDisplayed()
        composeRule.onNodeWithText("Create and Select").assertIsEnabled()
    }
}
