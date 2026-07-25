package worq.order.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithText("No active clients").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add a client before creating a task.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add client").performClick()
        composeRule
            .onNodeWithText("Create (available in Milestone 6)")
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

    private fun setContent(
        state: CreateTaskUiState,
        onEvent: (CreateTaskEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                CreateTaskScreen(
                    uiState = state,
                    onEvent = onEvent,
                    onNavigateBack = {},
                )
            }
        }
    }
}
