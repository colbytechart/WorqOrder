package worq.order.ui.settings

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
                .onNodeWithText("Client management")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val appearanceBounds =
            composeRule
                .onNodeWithText("Appearance")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(clientBounds.top < appearanceBounds.top)
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
            .performScrollToNode(hasText("Client management"))
        composeRule.onNodeWithText("Client management").performClick()

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
                ),
            showGoogleSetupRequired = true,
        )

        composeRule
            .onNodeWithText("Stop the running timer before changing the time zone.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Use manual time zone").assertIsNotEnabled()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Google Sheets setup required"))
        composeRule
            .onNodeWithText("Google Sheets setup required")
            .assertIsDisplayed()
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
}
