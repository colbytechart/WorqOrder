package worq.order.ui.clients

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class ClientManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeAndArchivedClientsExposeExpectedActions() {
        val events = mutableListOf<ClientManagementEvent>()
        setContent(
            state =
                ClientManagementUiState(
                    isLoading = false,
                    activeClients = listOf(ClientItemUi("active", "Alpha")),
                    archivedClients = listOf(ClientItemUi("archived", "Zulu")),
                ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("Active clients").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Rename Alpha")
            .performClick()
        composeRule
            .onNodeWithContentDescription("Remove Alpha from the client list")
            .performClick()
        composeRule
            .onNodeWithText("Archived clients")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Zulu").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Restore").performScrollTo().performClick()

        assertEquals(
            listOf(
                ClientManagementEvent.OpenRenameClient("active"),
                ClientManagementEvent.RequestArchive("active"),
                ClientManagementEvent.RestoreClient("archived"),
            ),
            events,
        )
    }

    @Test
    fun editorShowsFieldLevelDuplicateValidation() {
        setContent(
            state =
                ClientManagementUiState(
                    isLoading = false,
                    editor =
                        ClientEditorUiState(
                            mode = ClientEditorMode.ADD,
                            name = "Acme",
                            fieldError = ClientNameFieldError.DUPLICATE_ACTIVE,
                        ),
                ),
        )

        composeRule
            .onNodeWithText("An active client already uses this name.")
            .assertIsDisplayed()
    }

    @Test
    fun archiveConfirmationExplainsHistoricalPreservation() {
        setContent(
            state =
                ClientManagementUiState(
                    isLoading = false,
                    archiveConfirmation =
                        ArchiveClientConfirmation(
                            clientId = "client",
                            clientName = "Acme",
                        ),
                ),
        )

        composeRule.onNodeWithText("Remove Acme?").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Historical tasks and recorded time will remain intact. " +
                    "This client will no longer appear when choosing a client for a new task.",
            ).assertIsDisplayed()
    }

    private fun setContent(
        state: ClientManagementUiState,
        onEvent: (ClientManagementEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                ClientManagementScreen(
                    uiState = state,
                    onEvent = onEvent,
                    onNavigateBack = {},
                )
            }
        }
    }
}
