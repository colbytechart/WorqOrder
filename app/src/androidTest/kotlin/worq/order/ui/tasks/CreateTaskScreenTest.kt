package worq.order.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.theme.WorqOrderTheme

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
                ),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.DESCRIPTION)
            .performTextInput("Task")
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.PURCHASES)
            .performTextInput("Laptop")
        composeRule
            .onNodeWithTag(CreateTaskScreenTestTags.CREATE)
            .performClick()

        assertTrue(events.contains(CreateTaskEvent.EditDescription("Task")))
        assertTrue(
            events.contains(
                CreateTaskEvent.EditHardwareSoftwarePurchases("Laptop"),
            ),
        )
        assertTrue(events.contains(CreateTaskEvent.CreateTask))
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
