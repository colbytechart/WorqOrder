package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ActiveTimerRepository
import worq.order.data.AppSettings
import worq.order.data.AutomaticGooglePendingReason
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.ExportDestination
import worq.order.data.ExportAttemptOutcome
import worq.order.data.LastExportAttempt
import worq.order.data.LandscapeHandedness
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.TimeZoneSettingResult
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.timer.TimerOperationLock

@RunWith(AndroidJUnit4::class)
class PreferencesSettingsRepositoryTest {
    @Test
    fun defaultsAndAllChoicesPersistAcrossRepositoryRecreation() {
        runBlocking {
            val file = testFile("settings-persistence")
            val activeTimers = MutableActiveTimerRepository()
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            var repository =
                PreferencesSettingsRepository(
                    dataStore = dataStore,
                    activeTimerRepository = activeTimers,
                    timerOperationLock = TimerOperationLock(),
                )

            assertEquals(AppSettings(), repository.readSettings())
            repository.setThemeMode(ThemeMode.LIGHT)
            repository.setManualZoneId(ZoneId.of("America/Los_Angeles"))
            repository.setDefaultExportDestination(
                ExportDestination.XLSX,
            )
            val exportAttempt =
                LastExportAttempt(
                    destination = ExportDestination.CSV,
                    workDate = LocalDate.of(2026, 7, 24),
                    attemptedAt = Instant.parse("2026-07-24T20:00:00Z"),
                    outcome = ExportAttemptOutcome.SUCCESS,
                )
            repository.recordLastExportAttempt(exportAttempt)
            repository.setSelectedEmployeeId("employee-1")
            repository.setLandscapeHandedness(LandscapeHandedness.LEFT_HANDED)
            repository.setAutomaticGoogleExportEnabled(true)
            repository.setAutomaticGoogleExportTarget(
                workDate = LocalDate.of(2026, 7, 24),
                zoneId = ZoneId.of("America/New_York"),
                connectionKey = "account\nspreadsheet",
                pendingReason = AutomaticGooglePendingReason.TIMER_RUNNING,
            )
            scope.cancel()
            scope.coroutineContext.job.join()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            repository =
                PreferencesSettingsRepository(
                    dataStore = dataStore,
                    activeTimerRepository = activeTimers,
                    timerOperationLock = TimerOperationLock(),
                )
            val restored = repository.readSettings()
            assertEquals(ThemeMode.LIGHT, restored.themeMode)
            assertEquals(TimeZoneMode.MANUAL, restored.timeZoneMode)
            assertEquals(
                ZoneId.of("America/Los_Angeles"),
                restored.manualZoneId,
            )
            assertEquals(
                ExportDestination.XLSX,
                restored.defaultExportDestination,
            )
            assertEquals(exportAttempt, restored.lastExportAttempt)
            assertEquals("employee-1", restored.selectedEmployeeId)
            assertEquals(LandscapeHandedness.LEFT_HANDED, restored.landscapeHandedness)
            assertTrue(restored.automaticGoogleExportEnabled)
            assertEquals(LocalDate.of(2026, 7, 24), restored.automaticGoogleTargetDate)
            assertEquals(
                ZoneId.of("America/New_York"),
                restored.automaticGoogleTargetZoneId,
            )
            assertEquals(
                "account\nspreadsheet",
                restored.automaticGoogleTargetConnectionKey,
            )
            assertEquals(
                AutomaticGooglePendingReason.TIMER_RUNNING,
                restored.automaticGooglePendingReason,
            )

            repository.setThemeMode(ThemeMode.DARK)
            repository.setTimeZoneMode(TimeZoneMode.DEVICE)
            repository.setDefaultExportDestination(ExportDestination.CSV)
            val changed = repository.readSettings()
            assertEquals(ThemeMode.DARK, changed.themeMode)
            assertEquals(TimeZoneMode.DEVICE, changed.timeZoneMode)
            assertEquals(ExportDestination.CSV, changed.defaultExportDestination)
            repository.setThemeMode(ThemeMode.SYSTEM)
            repository.setAutomaticGoogleExportEnabled(false)
            assertNull(repository.readSettings().automaticGoogleTargetDate)
            assertNull(repository.readSettings().automaticGoogleTargetZoneId)
            assertNull(repository.readSettings().automaticGoogleTargetConnectionKey)
            assertNull(repository.readSettings().automaticGooglePendingReason)

            scope.cancel()
            scope.coroutineContext.job.join()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            repository =
                PreferencesSettingsRepository(
                    dataStore = dataStore,
                    activeTimerRepository = activeTimers,
                    timerOperationLock = TimerOperationLock(),
                )
            assertEquals(ThemeMode.SYSTEM, repository.readSettings().themeMode)

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }

    @Test
    fun unknownEnumsAndInvalidManualZoneUseSafeDefaults() {
        runBlocking {
            val file = testFile("settings-corrupt-values")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("theme_mode")] = "SEPIA"
                preferences[stringPreferencesKey("time_zone_mode")] = "MANUAL"
                preferences[stringPreferencesKey("manual_zone_id")] = "UTC"
                preferences[stringPreferencesKey("default_export_destination")] =
                    "LOTUS"
                preferences[stringPreferencesKey("landscape_handedness")] = "UPSIDE_DOWN"
                preferences[stringPreferencesKey("automatic_google_pending_reason")] = "UNKNOWN"
            }
            val repository =
                PreferencesSettingsRepository(
                    dataStore = dataStore,
                    activeTimerRepository = MutableActiveTimerRepository(),
                    timerOperationLock = TimerOperationLock(),
                )

            val settings = repository.readSettings()
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
            assertEquals(TimeZoneMode.DEVICE, settings.timeZoneMode)
            assertNull(settings.manualZoneId)
            assertEquals(
                ExportDestination.CSV,
                settings.defaultExportDestination,
            )
            assertEquals(LandscapeHandedness.RIGHT_HANDED, settings.landscapeHandedness)
            assertNull(settings.automaticGoogleTargetDate)
            assertNull(settings.automaticGooglePendingReason)

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }

    @Test
    fun timeZoneMutationRejectsFixedZoneAndIsBlockedByActiveTimer() {
        runBlocking {
            val file = testFile("settings-timer-guard")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            val activeTimers = MutableActiveTimerRepository()
            val repository =
                PreferencesSettingsRepository(
                    dataStore = dataStore,
                    activeTimerRepository = activeTimers,
                    timerOperationLock = TimerOperationLock(),
                )

            assertEquals(
                TimeZoneSettingResult.InvalidManualZone,
                repository.setManualZoneId(ZoneId.of("UTC")),
            )
            activeTimers.setRunning(true)
            assertEquals(
                TimeZoneSettingResult.BlockedByActiveTimer,
                repository.setManualZoneId(ZoneId.of("America/New_York")),
            )
            assertTrue(repository.readSettings().timeZoneMode == TimeZoneMode.DEVICE)

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }

    private fun testFile(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.filesDir, "$name.preferences_pb").also(File::delete)
    }

    private class MutableActiveTimerRepository : ActiveTimerRepository {
        private val state = MutableStateFlow<ActiveTimer?>(null)

        fun setRunning(isRunning: Boolean) {
            state.value =
                if (isRunning) {
                    ActiveTimer(
                        intervalId = "interval",
                        taskId = "task",
                        boundaryZoneId = ZoneId.of("America/New_York"),
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH,
                    )
                } else {
                    null
                }
        }

        override fun observeActiveTimer(): Flow<ActiveTimer?> = state

        override suspend fun readActiveTimer(): ActiveTimer? = state.value

        override suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot? = null

        override suspend fun createActiveInterval(
            taskId: String,
            boundaryZoneId: ZoneId,
            start: Instant,
        ): CreateActiveIntervalResult = error("Not needed by this test")

        override suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot? = null

        override suspend fun closeActiveIntervalAtBoundary(
            expectedIntervalId: String,
            boundary: Instant,
        ): ActiveTimerSnapshot? = null

        override suspend fun closeActiveInterval(
            expectedIntervalId: String,
            stop: Instant,
        ): ActiveTimerSnapshot? = null
    }
}
