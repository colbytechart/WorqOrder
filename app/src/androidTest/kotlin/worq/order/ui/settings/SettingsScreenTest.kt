package worq.order.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ThemeMode
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSectionsExposeThemeExportAndClientActions() {
        val events = mutableListOf<SettingsEvent>()
        var openedClients = false
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                ),
            onEvent = events::add,
            onOpenClientManagement = { openedClients = true },
        )

        val clientBounds =
            composeRule
                .onNodeWithText("Client Management")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val appearanceBounds =
            composeRule
                .onNodeWithText("Appearance")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val consultantBounds =
            composeRule
                .onNodeWithText("Consultant")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(clientBounds.top < consultantBounds.top)
        assertTrue(consultantBounds.top < appearanceBounds.top)
        composeRule.onNodeWithText("Use system setting").assertIsSelected()
        composeRule.onNodeWithText("Light").assertIsEnabled()
        composeRule.onNodeWithText("Dark").assertIsEnabled()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Light").performClick()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Google Sheets"))
        composeRule.onNodeWithText("Google Sheets").performClick()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Client Management"))
        composeRule.onNodeWithText("Client Management").performClick()

        assertEquals(
            listOf(
                SettingsEvent.SelectTheme(ThemeMode.LIGHT),
                SettingsEvent.SelectExportDestination(
                    worq.order.data.ExportDestination.GOOGLE_SHEETS,
                ),
            ),
            events,
        )
        assertTrue(openedClients)
    }

    @Test
    fun zoneSelectorIsSearchableAndPreservesCanonicalId() {
        val events = mutableListOf<SettingsEvent>()
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/Chicago"),
                    isZoneSelectorVisible = true,
                    zoneSearchQuery = "New York",
                    zoneOptions =
                        listOf(
                            ZoneOptionUi(
                                zoneId = ZoneId.of("America/New_York"),
                                friendlyName = "New York",
                            ),
                        ),
                ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("Search by city, region, or zone ID")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("New York").assertCountEquals(2)
        composeRule.onNodeWithText("America/New_York").performClick()

        assertEquals(
            SettingsEvent.SelectManualZone(ZoneId.of("America/New_York")),
            events.last(),
        )
    }

    @Test
    fun runningTimerDisablesZoneChangesAndGoogleRouteShowsSetupRequirement() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    isTimerRunning = true,
                    defaultExportDestination =
                        worq.order.data.ExportDestination.GOOGLE_SHEETS,
                ),
            showGoogleSetupRequired = true,
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText("Stop the running timer before changing the time zone."),
            )
        composeRule
            .onNodeWithText("Stop the running timer before changing the time zone.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Use manual time zone").assertIsNotEnabled()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Google Sheets Setup Required"))
        composeRule
            .onNodeWithText("Google Sheets Setup Required")
            .assertIsDisplayed()
    }

    @Test
    fun googleConnectionSectionShowsAccountSpreadsheetAndActions() {
        val events = mutableListOf<SettingsEvent>()
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    googleStatus = GoogleConnectionUiStatus.CONNECTED,
                    googleAccountId = "person@example.com",
                    googleAccountDisplayName = "Person",
                    spreadsheetInput = SPREADSHEET_ID,
                    connectedSpreadsheetId = SPREADSHEET_ID,
                    connectedSpreadsheetTitle = "Work Log",
                    defaultExportDestination =
                        worq.order.data.ExportDestination.GOOGLE_SHEETS,
                ),
            onEvent = events::add,
        )

        composeRule
            .onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Spreadsheet: Work Log"))
        composeRule.onNodeWithText("Spreadsheet: Work Log").assertIsDisplayed()
        composeRule
            .onNodeWithText("Signed in as Person")
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText("Spreadsheet URL or ID")
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText("Validate and connect")
            .assertCountEquals(0)
        composeRule
            .onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Disconnect spreadsheet"))
        composeRule.onNodeWithText("Disconnect spreadsheet").performClick()
        composeRule
            .onNodeWithText("Disconnect Spreadsheet?")
            .assertIsDisplayed()
        assertTrue(SettingsEvent.DisconnectSpreadsheet !in events)
        composeRule.onNodeWithText("Disconnect").performClick()
        composeRule
            .onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Sign out"))
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule
            .onNodeWithText("Sign Out of Google?")
            .assertIsDisplayed()
        assertTrue(SettingsEvent.SignOutOfGoogle !in events)
        composeRule.onNodeWithText("Sign Out").performClick()

        assertTrue(SettingsEvent.DisconnectSpreadsheet in events)
        assertTrue(SettingsEvent.SignOutOfGoogle in events)
    }

    @Test
    fun googleConnectionSectionOnlyAppearsForGoogleDestination() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    defaultExportDestination =
                        worq.order.data.ExportDestination.CSV,
                ),
        )

        composeRule
            .onAllNodesWithText("Google Sheets Connection")
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText("Sign in with Google")
            .assertCountEquals(0)
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Export Destination"))
        composeRule.onNodeWithText("Export Destination").assertIsDisplayed()
    }

    @Test
    fun xlsxIsAOneOffDestinationWithoutConnectionSection() {
        val events = mutableListOf<SettingsEvent>()
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    defaultExportDestination =
                        worq.order.data.ExportDestination.CSV,
                ),
            onEvent = events::add,
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("XLSX"))
        composeRule.onNodeWithText("XLSX").performClick()

        assertTrue(
            SettingsEvent.SelectExportDestination(
                worq.order.data.ExportDestination.XLSX,
            ) in events,
        )
        composeRule
            .onAllNodesWithText("Google Sheets Connection")
            .assertCountEquals(0)
    }

    @Test
    fun selectingGoogleSheetsAutoScrollsToConnectionSection() {
        var state by
            mutableStateOf(
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    defaultExportDestination =
                        worq.order.data.ExportDestination.CSV,
                ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = state,
                    onEvent = { event ->
                        if (
                            event is SettingsEvent.SelectExportDestination
                        ) {
                            state =
                                state.copy(
                                    defaultExportDestination =
                                        event.destination,
                                )
                        }
                    },
                    onNavigateBack = {},
                    onOpenClientManagement = {},
                )
            }
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Google Sheets"))
        composeRule.onNodeWithText("Google Sheets").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Google Sheets Connection")
            .assertIsDisplayed()
    }

    @Test
    fun partialSignOutUsesConciseNonRetryableWarning() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    defaultExportDestination =
                        worq.order.data.ExportDestination.GOOGLE_SHEETS,
                    googleMessage = GoogleSettingsMessage.SIGN_OUT_PARTIAL,
                ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(
                    "Signed out locally. Remove WorqOrder from your " +
                        "Google Account if access remains.",
                ),
            )
        composeRule
            .onNodeWithText(
                "Signed out locally. Remove WorqOrder from your " +
                    "Google Account if access remains.",
            ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    private fun setContent(
        state: SettingsUiState,
        onEvent: (SettingsEvent) -> Unit = {},
        onOpenClientManagement: () -> Unit = {},
        showGoogleSetupRequired: Boolean = false,
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = state,
                    onEvent = onEvent,
                    onNavigateBack = {},
                    onOpenClientManagement = onOpenClientManagement,
                    showGoogleSetupRequired = showGoogleSetupRequired,
                )
            }
        }
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
