package worq.order.ui.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.ExportDestination
import worq.order.data.GoogleAccountHint
import worq.order.data.LandscapeHandedness
import worq.order.data.NewDailyTask
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeGoogleConnectionRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule
import worq.order.export.google.GoogleConnectionFailure
import worq.order.export.google.GoogleConnectionOperationResult

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun themeAndExportChangesApplyImmediately() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
            assertEquals(
                ExportDestination.CSV,
                viewModel.uiState.value.defaultExportDestination,
            )

            viewModel.onEvent(SettingsEvent.SelectTheme(ThemeMode.LIGHT))
            runCurrent()
            viewModel.onEvent(
                SettingsEvent.SelectExportDestination(
                    ExportDestination.XLSX,
                ),
            )
            runCurrent()

            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
            assertEquals(
                ExportDestination.XLSX,
                viewModel.uiState.value.defaultExportDestination,
            )

            viewModel.onEvent(SettingsEvent.SelectTheme(ThemeMode.SYSTEM))
            runCurrent()
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        }

    @Test
    fun landscapeHandednessAppliesImmediatelyAndPersistsThroughRepository() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertEquals(
                LandscapeHandedness.RIGHT_HANDED,
                viewModel.uiState.value.landscapeHandedness,
            )

            viewModel.onEvent(
                SettingsEvent.SelectLandscapeHandedness(
                    LandscapeHandedness.LEFT_HANDED,
                ),
            )
            runCurrent()

            assertEquals(
                LandscapeHandedness.LEFT_HANDED,
                viewModel.uiState.value.landscapeHandedness,
            )
            assertEquals(
                LandscapeHandedness.LEFT_HANDED,
                fixture.settings.readSettings().landscapeHandedness,
            )
        }

    @Test
    fun searchableManualZoneSelectionStoresCanonicalIdAndMode() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(SettingsEvent.SelectManualTimeZone)
            viewModel.onEvent(SettingsEvent.EditZoneSearch("New York"))
            runCurrent()

            val option =
                viewModel.uiState.value.zoneOptions.single {
                    it.zoneId.id == "America/New_York"
                }
            viewModel.onEvent(SettingsEvent.SelectManualZone(option.zoneId))
            runCurrent()

            assertFalse(viewModel.uiState.value.isZoneSelectorVisible)
            assertEquals(TimeZoneMode.MANUAL, viewModel.uiState.value.timeZoneMode)
            assertEquals(
                ZoneId.of("America/New_York"),
                viewModel.uiState.value.manualZoneId,
            )
        }

    @Test
    fun timeZoneControlsAreBlockedWhileTimerRuns() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task =
                fixture.tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = "client",
                        description = "Task",
                        workDate = LocalDate.of(2026, 7, 24),
                        zoneId = fixture.zone.current,
                    ),
                )
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = fixture.zone.current,
                start = Instant.parse("2026-07-24T13:00:00Z"),
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(SettingsEvent.SelectManualTimeZone)
            runCurrent()

            assertEquals(
                SettingsMessage.STOP_TIMER_BEFORE_TIME_ZONE_CHANGE,
                viewModel.uiState.value.message,
            )
            assertFalse(viewModel.uiState.value.isZoneSelectorVisible)
            assertEquals(TimeZoneMode.DEVICE, viewModel.uiState.value.timeZoneMode)
        }

    @Test
    fun googleConnectionEventsExposeProgressAndStructuredRecoveryState() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val signInEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(SettingsEvent.SignInToGoogle)
            assertEquals(
                SettingsEffect.SignInToGoogle,
                signInEffect.await(),
            )
            assertEquals(
                GoogleConnectionUiStatus.SIGNING_IN,
                viewModel.uiState.value.googleStatus,
            )

            val account =
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                )
            fixture.google.saveSignedInAccount(account)
            viewModel.onGoogleOperationResult(
                GoogleConnectionOperationResult.SignedIn(account),
            )
            runCurrent()
            assertEquals(
                GoogleConnectionUiStatus.SIGNED_IN_NO_SPREADSHEET,
                viewModel.uiState.value.googleStatus,
            )

            viewModel.onEvent(
                SettingsEvent.EditSpreadsheetInput(SPREADSHEET_ID),
            )
            val validationEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(SettingsEvent.ValidateAndConnectSpreadsheet)
            assertEquals(
                SettingsEffect.ValidateAndConnectSpreadsheet(SPREADSHEET_ID),
                validationEffect.await(),
            )
            assertEquals(
                GoogleConnectionUiStatus.VALIDATING_SPREADSHEET,
                viewModel.uiState.value.googleStatus,
            )

            viewModel.onGoogleOperationResult(
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.OFFLINE,
                ),
            )
            runCurrent()
            assertEquals(
                GoogleConnectionUiStatus.OFFLINE_ERROR,
                viewModel.uiState.value.googleStatus,
            )
            assertEquals(
                GoogleSettingsMessage.OFFLINE,
                viewModel.uiState.value.googleMessage,
            )
        }

    @Test
    fun connectedSpreadsheetCannotBeRedundantlyValidated() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.google.saveSignedInAccount(
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                ),
            )
            fixture.google.saveConnectedSpreadsheet(
                spreadsheetId = SPREADSHEET_ID,
                spreadsheetTitle = "Work Log",
                validatedAt = Instant.parse("2026-07-26T14:00:00Z"),
            )
            val viewModel = fixture.viewModel()
            val effects = mutableListOf<SettingsEffect>()
            collectState(viewModel)
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) {
                viewModel.effects.collect(effects::add)
            }
            runCurrent()

            viewModel.onEvent(
                SettingsEvent.ValidateAndConnectSpreadsheet,
            )
            runCurrent()

            assertEquals(
                GoogleConnectionUiStatus.CONNECTED,
                viewModel.uiState.value.googleStatus,
            )
            assertTrue(effects.isEmpty())
        }

    @Test
    fun successfulSignOutClearsSpreadsheetConnectionAndEditorInput() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.google.saveSignedInAccount(
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                ),
            )
            fixture.google.saveConnectedSpreadsheet(
                spreadsheetId = SPREADSHEET_ID,
                spreadsheetTitle = "Work Log",
                validatedAt = Instant.parse("2026-07-26T14:00:00Z"),
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            viewModel.onEvent(
                SettingsEvent.EditSpreadsheetInput(SPREADSHEET_ID),
            )
            runCurrent()

            fixture.google.completeLocalSignOut()
            viewModel.onGoogleOperationResult(
                GoogleConnectionOperationResult.SignedOut,
            )
            runCurrent()

            assertEquals(
                GoogleConnectionUiStatus.SIGNED_OUT,
                viewModel.uiState.value.googleStatus,
            )
            assertEquals("", viewModel.uiState.value.spreadsheetInput)
            assertNull(viewModel.uiState.value.googleAccountId)
            assertNull(viewModel.uiState.value.connectedSpreadsheetId)
            assertNull(viewModel.uiState.value.connectedSpreadsheetTitle)
        }

    @Test
    fun deniedNotificationPermissionUsesInlineAutoExportRecovery() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(SettingsEvent.NotificationPermissionResult(false))
            runCurrent()

            assertEquals(
                AutomaticGoogleExportEnablementError.NOTIFICATION_PERMISSION_REQUIRED,
                viewModel.uiState.value.automaticGoogleExportEnablementError,
            )
            assertNull(viewModel.uiState.value.message)
        }

    private fun kotlinx.coroutines.test.TestScope.collectState(
        viewModel: SettingsViewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val settings = FakeSettingsRepository()
        val google = FakeGoogleConnectionRepository()
        val zone = FakeZoneIdProvider(ZoneId.of("America/Chicago"))

        fun viewModel() =
            SettingsViewModel(
                settingsRepository = settings,
                activeTimerRepository = active,
                zoneIdProvider = zone,
                googleConnectionRepository = google,
            )
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
