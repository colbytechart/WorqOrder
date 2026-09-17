package worq.order.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
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
                                listOf(TaskTagCatalogItemUi("tag-1", "Install monitor")),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithText("Description Tags").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove tag Install monitor").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add tags")[0].performClick()

        assertTrue(
            events.contains(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION)),
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
                                listOf(TaskTagCatalogItemUi("tag-1", "Install monitor")),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Install monitor")
            .assertIsSelected()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(events.contains(EditTaskEvent.DismissTagPicker))
    }
}
