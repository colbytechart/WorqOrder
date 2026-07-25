package worq.order.ui.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.ExportDestination
import worq.order.data.NewDailyTask
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule

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
                    ExportDestination.GOOGLE_SHEETS,
                ),
            )
            runCurrent()

            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
            assertEquals(
                ExportDestination.GOOGLE_SHEETS,
                viewModel.uiState.value.defaultExportDestination,
            )

            viewModel.onEvent(SettingsEvent.SelectTheme(ThemeMode.SYSTEM))
            runCurrent()
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
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
        val zone = FakeZoneIdProvider(ZoneId.of("America/Chicago"))

        fun viewModel() =
            SettingsViewModel(
                settingsRepository = settings,
                activeTimerRepository = active,
                zoneIdProvider = zone,
            )
    }
}
