package worq.order.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
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
import worq.order.data.LandscapeHandedness
import worq.order.ui.employees.ConsultantSettingsUiState
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutShowsBuildVersionAndHasNoRepositoryLink() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("WorqOrder v0.2.0 - stable"))
        composeRule.onAllNodesWithText("About").assertCountEquals(0)
        composeRule
            .onNodeWithText("WorqOrder v0.2.0 - stable")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithText("GitHub", substring = true).assertCountEquals(0)
    }

    @Test
    fun settingsSectionsExposeThemeExportAndClientActions() {
        val events = mutableListOf<SettingsEvent>()
        var openedClients = false
        var openedConsultants = false
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                ),
            onEvent = events::add,
            onOpenClientManagement = { openedClients = true },
            onOpenConsultantManagement = { openedConsultants = true },
            consultantState = ConsultantSettingsUiState(isLoading = false),
        )

        val clientBounds =
            composeRule
                .onNodeWithText("Client Management")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val consultantBounds =
            composeRule
                .onNodeWithText("Consultant Management")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(clientBounds.top < consultantBounds.top)
        composeRule.onNodeWithText("Client Management").performClick()
        composeRule.onNodeWithText("Consultant Management").performClick()
        composeRule.onNodeWithText("Choose a Consultant").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add and select a Consultant before creating a task.")
            .assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Appearance"))
        composeRule.onNodeWithText("Use system setting").assertIsSelected()
        composeRule.onNodeWithText("Light").assertIsEnabled()
        composeRule.onNodeWithText("Dark").assertIsEnabled()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Light").performClick()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Landscape Orientation"))
        composeRule.onNodeWithText("Right-handed").assertIsSelected()
        composeRule.onNodeWithText("Left-handed").performClick()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Google Sheets"))
        composeRule.onNodeWithText("Google Sheets").performClick()
        assertEquals(
            listOf(
                SettingsEvent.SelectTheme(ThemeMode.LIGHT),
                SettingsEvent.SelectLandscapeHandedness(
                    LandscapeHandedness.LEFT_HANDED,
                ),
                SettingsEvent.SelectExportDestination(
                    worq.order.data.ExportDestination.GOOGLE_SHEETS,
                ),
            ),
            events,
        )
        assertTrue(openedClients)
        assertTrue(openedConsultants)
    }

    @Test
    fun timeZoneSettingsAndSelectorAreHidden() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/Chicago"),
                    isZoneSelectorVisible = true,
                ),
        )

        composeRule.onAllNodesWithText("Time Zone").assertCountEquals(0)
        composeRule.onAllNodesWithText("Use device time zone").assertCountEquals(0)
        composeRule.onAllNodesWithText("Use manual time zone").assertCountEquals(0)
        composeRule
            .onAllNodesWithText("Search by city, region, or zone ID")
            .assertCountEquals(0)
    }

    @Test
    fun googleRouteShowsSetupRequirementWhileTimerRuns() {
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
                    onOpenConsultantManagement = {},
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
        onOpenConsultantManagement: () -> Unit = {},
        consultantState: ConsultantSettingsUiState = ConsultantSettingsUiState(),
        showGoogleSetupRequired: Boolean = false,
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = state,
                    onEvent = onEvent,
                    onNavigateBack = {},
                    onOpenClientManagement = onOpenClientManagement,
                    onOpenConsultantManagement = onOpenConsultantManagement,
                    consultantUiState = consultantState,
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
