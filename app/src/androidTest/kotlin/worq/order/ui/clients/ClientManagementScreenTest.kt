package worq.order.ui.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.domain.ClientCsvImportFailure
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

        composeRule.onNodeWithText("Active Clients").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Rename Alpha")
            .performClick()
        composeRule
            .onNodeWithContentDescription("Remove Alpha from the client list")
            .performClick()
        composeRule
            .onNodeWithText("Archived Clients")
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
        composeRule
            .onNode(
                matcher =
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Error,
                        "An active client already uses this name.",
                    ),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
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

    @Test
    fun importButtonLaunchesPickerAndSuccessIsAnnounced() {
        var importRequests = 0
        setContent(
            state =
                ClientManagementUiState(
                    isLoading = false,
                    importSummary =
                        ClientImportSummaryUi(
                            addedCount = 2,
                            restoredCount = 1,
                            skippedCount = 3,
                        ),
                ),
            onImportCsv = { importRequests += 1 },
        )

        composeRule.onNodeWithText("Import From CSV").performClick()
        composeRule
            .onNodeWithText("Client import complete. Added: 2. Restored: 1. Skipped: 3.")
            .assertIsDisplayed()
        assertEquals(1, importRequests)
    }

    @Test
    fun importingStateDisablesImportAndShowsProgressText() {
        setContent(
            state =
                ClientManagementUiState(
                    isLoading = false,
                    isImporting = true,
                ),
        )

        composeRule.onNodeWithText("Importing clients…").assertIsDisplayed()
    }

    @Test
    fun importFailureMovesToActionAreaAndResetsLongListScrollOnDisplayAndDismiss() {
        var state by
            mutableStateOf(
                ClientManagementUiState(
                    isLoading = false,
                    activeClients =
                        (1..30).map { index ->
                            ClientItemUi("client-$index", "Client $index")
                        },
                ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                ClientManagementScreen(
                    uiState = state,
                    onEvent = { event ->
                        if (event == ClientManagementEvent.DismissImportStatus) {
                            state = state.copy(importFailure = null)
                        }
                    },
                    onNavigateBack = {},
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(25)

        composeRule.runOnIdle {
            state =
                state.copy(
                    importFailure =
                        ClientImportFailureUi(
                            failure = ClientCsvImportFailure.MALFORMED_CSV,
                            recordNumber = 2,
                            columnNumber = 3,
                        ),
                )
        }
        composeRule.waitForIdle()

        val importButton =
            composeRule.onNodeWithText("Import From CSV").assertIsDisplayed().fetchSemanticsNode()
        val error =
            composeRule
                .onNodeWithText("This CSV has invalid quoting or structure. Row 2, column 3.")
                .assertIsDisplayed()
                .fetchSemanticsNode()
        val activeHeading =
            composeRule.onNodeWithText("Active Clients").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(importButton.boundsInRoot.top < error.boundsInRoot.top)
        assertTrue(error.boundsInRoot.top < activeHeading.boundsInRoot.top)

        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add client").assertIsDisplayed()
        composeRule.onNodeWithText("Import From CSV").assertIsDisplayed()
        composeRule.onNodeWithText("Active Clients").assertIsDisplayed()
    }

    private fun setContent(
        state: ClientManagementUiState,
        onEvent: (ClientManagementEvent) -> Unit = {},
        onImportCsv: () -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                ClientManagementScreen(
                    uiState = state,
                    onEvent = onEvent,
                    onNavigateBack = {},
                    onImportCsv = onImportCsv,
                )
            }
        }
    }
}
