package worq.order.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.TaskMetadataValidationError
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.theme.WorqOrderTheme
import worq.order.model.BillingStatus

@RunWith(AndroidJUnit4::class)
class CreateTaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyClientStateExplainsRequirementAndOffersInlineAdd() {
        val events = mutableListOf<CreateTaskEvent>()
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients = emptyList(),
                ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("No Active Clients").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add a client before creating a task.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add client").performClick()
        composeRule
            .onNodeWithText("Create")
            .assertIsNotEnabled()

        assertEquals(listOf(CreateTaskEvent.OpenAddClient), events)
    }

    @Test
    fun activeClientSelectorShowsOnlyProvidedActiveChoices() {
        val events = mutableListOf<CreateTaskEvent>()
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients =
                        listOf(
                            ClientItemUi("alpha", "Alpha"),
                            ClientItemUi("beta", "Beta"),
                        ),
                    isClientMenuExpanded = true,
                ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").performClick()

        assertEquals(
            CreateTaskEvent.SelectClient("beta"),
            events.last(),
        )
    }

    @Test
    fun taskFieldsAndCreateActionEmitFormEvents() {
        val events = mutableListOf<CreateTaskEvent>()
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients = listOf(ClientItemUi("client", "Client")),
                    selectedClientId = "client",
                    isLoadingConsultant = false,
                    selectedConsultantId = "employee",
                    selectedConsultantName = "Alex Rivera",
                ),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.DESCRIPTION)
            .performTextInput("task")
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.PURCHASES)
            .performTextInput("laptop")
        composeRule
            .onNodeWithText("In-Office")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("Billable")
            .performScrollTo()
            .assertIsSelected()
        composeRule
            .onNodeWithText("Do not bill")
            .performClick()
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.MILEAGE)
            .performTextInput("12.5")
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.NOTES)
            .performTextInput("follow up")
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.CREATE)
            .performScrollTo()
            .performClick()

        assertTrue(events.contains(CreateTaskEvent.EditDescription("task")))
        assertTrue(
            events.contains(
                CreateTaskEvent.EditHardwareSoftwarePurchases("laptop"),
            ),
        )
        assertTrue(
            events.contains(CreateTaskEvent.SelectWorkType(worq.order.model.WorkType.IN_OFFICE)),
        )
        assertTrue(
            events.contains(CreateTaskEvent.SelectBillingStatus(BillingStatus.DO_NOT_BILL)),
        )
        assertTrue(events.contains(CreateTaskEvent.EditMileage("12.5")))
        assertTrue(events.contains(CreateTaskEvent.EditNotes("follow up")))
        assertTrue(events.contains(CreateTaskEvent.CreateTask))
    }

    @Test
    fun createHeaderStartsWithDateAndClientChoiceWithoutConsultantHeading() {
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients = listOf(ClientItemUi("client", "Client")),
                    isLoadingConsultant = false,
                    selectedConsultantId = "employee",
                    selectedConsultantName = "Alex Rivera",
                ),
        )

        composeRule.onNodeWithText("Task for", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Choose a client").assertIsDisplayed()
        composeRule.onAllNodesWithText("Consultant").assertCountEquals(0)
        composeRule.onAllNodesWithText("Client").assertCountEquals(0)
        composeRule.onAllNodesWithText("Consultant: Alex Rivera").assertCountEquals(0)
    }

    @Test
    fun overlengthNotesShowFieldLevelErrorBelowTheEditableField() {
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients = listOf(ClientItemUi("client", "Client")),
                    selectedClientId = "client",
                    isLoadingConsultant = false,
                    selectedConsultantId = "employee",
                    notes = "x".repeat(1000),
                    metadataErrors = setOf(TaskMetadataValidationError.NOTES_TOO_LONG),
                ),
        )

        composeRule.onNodeWithTag(CreateTaskScreenTestTags.NOTES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Character limit: 999").assertIsDisplayed()
    }

    @Test
    fun missingConsultantExplainsRequirementAndRoutesToSettings() {
        val events = mutableListOf<CreateTaskEvent>()
        setContent(
            state =
                CreateTaskUiState(
                    isLoadingClients = false,
                    activeClients = listOf(ClientItemUi("client", "Client")),
                    selectedClientId = "client",
                    isLoadingConsultant = false,
                ),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithText("Select an active Consultant in Settings before creating a task.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open Consultant Settings").performClick()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()

        assertEquals(CreateTaskEvent.OpenConsultantSettings, events.last())
    }

    private fun setContent(
        state: CreateTaskUiState,
        onEvent: (CreateTaskEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                CreateTaskScreen(
                    uiState = state,
                    onEvent = onEvent,
                )
            }
        }
    }
}
