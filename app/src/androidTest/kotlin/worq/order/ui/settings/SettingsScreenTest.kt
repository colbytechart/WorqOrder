package worq.order.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.BuildConfig
import worq.order.data.BackupRestoreStatus
import worq.order.data.LandscapeHandedness
import worq.order.data.ThemeMode
import worq.order.ui.employees.ConsultantSettingsUiState
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutShowsBuildVersionAndHasNoRepositoryLink() {
        val expectedVersionText = "WorqOrder v${BuildConfig.VERSION_NAME} - stable"
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(expectedVersionText))
        composeRule.onAllNodesWithText("About").assertCountEquals(0)
        composeRule
            .onNodeWithText(expectedVersionText)
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithText("GitHub", substring = true).assertCountEquals(0)
    }

    @Test
    fun settingsSectionsExposeThemeExportAndClientActions() {
        val events = mutableListOf<SettingsEvent>()
        var openedClients = false
        var openedConsultants = false
        var openedTags = false
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                ),
            onEvent = events::add,
            onOpenClientManagement = { openedClients = true },
            onOpenConsultantManagement = { openedConsultants = true },
            onOpenTagManagement = { openedTags = true },
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
        composeRule.onNodeWithText("Tag Management").performClick()
        composeRule.onNodeWithText("Choose a Consultant").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add and select a Consultant before creating a task.")
            .assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Appearance"))
        composeRule.onNodeWithText("Use system setting").assertIsSelected()
        composeRule.onNodeWithText("Dark").assertIsEnabled()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule
            .onNodeWithText("Light")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Left-handed"))
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
        assertTrue(openedTags)
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
    fun autoExportSwitchAppearsOnlyForGoogleAndEmitsToggle() {
        var state by mutableStateOf(
            SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
        )
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = state,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onOpenClientManagement = {},
                    onOpenConsultantManagement = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Auto Export").assertCountEquals(0)

        state =
            state.copy(
                defaultExportDestination =
                    worq.order.data.ExportDestination.GOOGLE_SHEETS,
                googleAccountId = "person@example.com",
                connectedSpreadsheetId = SPREADSHEET_ID,
                connectedSpreadsheetTitle = "Work Log",
                googleStatus = GoogleConnectionUiStatus.CONNECTED,
            )
        composeRule.waitForIdle()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Auto Export"))
        composeRule.onNodeWithText("Auto Export").performClick()

        assertTrue(events.contains(SettingsEvent.SetAutomaticGoogleExport(true)))
    }

    @Test
    fun autoExportEnablementErrorAppearsInlineWithSpecificRecovery() {
        setContent(
            state =
                SettingsUiState(
                    effectiveZoneId = ZoneId.of("America/New_York"),
                    defaultExportDestination =
                        worq.order.data.ExportDestination.GOOGLE_SHEETS,
                    googleAccountId = "person@example.com",
                    connectedSpreadsheetId = SPREADSHEET_ID,
                    connectedSpreadsheetTitle = "Work Log",
                    googleStatus = GoogleConnectionUiStatus.CONNECTED,
                    automaticGoogleExportEnablementError =
                        AutomaticGoogleExportEnablementError
                            .NOTIFICATION_PERMISSION_REQUIRED,
                ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(
                    "Enable notifications in Settings > Apps > WorqOrder > " +
                        "Notifications to use Auto Export.",
                ),
            )
        composeRule
            .onNodeWithText(
                "Enable notifications in Settings > Apps > WorqOrder > " +
                    "Notifications to use Auto Export.",
            ).assertIsDisplayed()
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

    @Test
    fun backupRestoreSectionExposesActionsAndAvailableRestorePoint() {
        val backupEvents = mutableListOf<BackupRestoreEvent>()
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState = BackupRestoreUiState(hasRestorePoint = true),
            onBackupRestoreEvent = backupEvents::add,
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Backup & Restore"))
        composeRule.onNodeWithText("Backup & Restore").assertIsDisplayed()
        composeRule
            .onAllNodesWithText(
                "Create a portable copy of your WorqOrder data or replace this app’s data " +
                    "from a backup.",
            ).assertCountEquals(0)
        composeRule
            .onAllNodesWithText("Backups are not encrypted. Store and share them carefully.")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Restore Previous State").assertCountEquals(0)
        composeRule
            .onAllNodesWithText(
                "Restore the most recent state saved before an import or restore. " +
                    "Your current state becomes the next restore point.",
            ).assertCountEquals(0)
        composeRule.onNodeWithText("Import Backup").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Create Backup").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(
            listOf(
                BackupRestoreEvent.ImportBackup,
                BackupRestoreEvent.CreateBackup,
                BackupRestoreEvent.RestorePreviousState,
            ),
            backupEvents,
        )
    }

    @Test
    fun backupRestoreDisablesAllMutationsWhileTimerRuns() {
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState =
                BackupRestoreUiState(
                    isTimerRunning = true,
                    hasRestorePoint = true,
                ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(
                    "Stop the running timer before creating, importing, or restoring a backup.",
                ),
            )
        composeRule.onNodeWithText("Import Backup").assertIsNotEnabled()
        composeRule.onNodeWithText("Create Backup").assertIsNotEnabled()
        composeRule.onNodeWithText("Restore").assertIsNotEnabled()
        composeRule
            .onNodeWithText(
                "Stop the running timer before creating, importing, or restoring a backup.",
            ).assertIsDisplayed()
    }

    @Test
    fun backupImportConfirmationExplainsReplacementBeforeMutation() {
        val backupEvents = mutableListOf<BackupRestoreEvent>()
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState =
                BackupRestoreUiState(
                    operation = BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION,
                ),
            onBackupRestoreEvent = backupEvents::add,
        )

        composeRule
            .onNodeWithText("Replace All WorqOrder Data?")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "WorqOrder will save your current data as a restore point, then replace it with the selected backup.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(BackupRestoreEvent.DismissImportConfirmation), backupEvents)
    }

    @Test
    fun restoreConfirmationExplainsSwapBeforeMutation() {
        val backupEvents = mutableListOf<BackupRestoreEvent>()
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState =
                BackupRestoreUiState(
                    operation = BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION,
                ),
            onBackupRestoreEvent = backupEvents::add,
        )

        composeRule.onNodeWithText("Restore Previous State?").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "This replaces your current WorqOrder data. The state being replaced becomes the next restore point.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Restore").performClick()
        assertEquals(listOf(BackupRestoreEvent.ConfirmRestore), backupEvents)
    }

    @Test
    fun destructiveReplacementShowsProgressBarrier() {
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState =
                BackupRestoreUiState(
                    operation = BackupRestoreOperation.IMPORTING,
                ),
        )

        composeRule.onNodeWithText("Importing backup\u2026").assertIsDisplayed()
    }

    @Test
    fun backupRestoreErrorUsesPersistentActionableText() {
        val backupEvents = mutableListOf<BackupRestoreEvent>()
        setContent(
            state = SettingsUiState(effectiveZoneId = ZoneId.of("America/New_York")),
            backupRestoreState =
                BackupRestoreUiState(
                    status =
                        BackupRestoreStatusUi(
                            BackupRestoreStatus.IMPORT_INVALID_ARCHIVE,
                        ),
                ),
            onBackupRestoreEvent = backupEvents::add,
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("The selected file is not a valid WorqOrder backup."))
        composeRule
            .onNodeWithText("The selected file is not a valid WorqOrder backup.")
            .assertIsDisplayed()
        // A short API 26 viewport cannot display the status and actions simultaneously. Verify
        // that both remain reachable instead of comparing bounds for an off-screen action.
        composeRule.onNodeWithText("Import Backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(BackupRestoreEvent.DismissStatus), backupEvents)
    }

    private fun setContent(
        state: SettingsUiState,
        onEvent: (SettingsEvent) -> Unit = {},
        onOpenClientManagement: () -> Unit = {},
        onOpenConsultantManagement: () -> Unit = {},
        onOpenTagManagement: () -> Unit = {},
        consultantState: ConsultantSettingsUiState = ConsultantSettingsUiState(),
        backupRestoreState: BackupRestoreUiState = BackupRestoreUiState(),
        onBackupRestoreEvent: (BackupRestoreEvent) -> Unit = {},
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
                    onOpenTagManagement = onOpenTagManagement,
                    consultantUiState = consultantState,
                    backupRestoreUiState = backupRestoreState,
                    onBackupRestoreEvent = onBackupRestoreEvent,
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
