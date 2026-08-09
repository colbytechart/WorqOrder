package worq.order.export.automatic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ActiveTimerRepository
import worq.order.data.AppSettings
import worq.order.data.AutomaticGooglePendingReason
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.ExportDestination
import worq.order.data.GoogleSpreadsheetConnection
import worq.order.data.TimerSplitBoundary
import worq.order.export.google.GoogleSheetExportReceipt
import worq.order.export.google.GoogleSheetsExportOperationResult
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.testing.FakeGoogleConnectionRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider

class AutomaticGoogleExportManagerTest {
    @Test
    fun enablingCapturesDateZoneAndConnectionThenSchedulesOneUniqueTarget() = runTest {
        val fixture = Fixture()

        assertEquals(AutomaticGoogleExportSettingResult.Enabled, fixture.manager.setEnabled(true))

        val settings = fixture.settings.readSettings()
        assertTrue(settings.automaticGoogleExportEnabled)
        assertEquals(WORK_DATE, settings.automaticGoogleTargetDate)
        assertEquals(ZONE, settings.automaticGoogleTargetZoneId)
        assertEquals(1, fixture.scheduler.targets.size)
        assertEquals(WORK_DATE, fixture.scheduler.targets.single().workDate)
    }

    @Test
    fun runningTimerBecomesPendingWithoutExportOrEarlyNotification() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        fixture.activeTimers.value = activeTimer()
        val target = fixture.scheduler.targets.single()

        fixture.manager.runScheduled(target.workDate.toEpochDay(), target.zoneId.id, target.connectionKey)

        assertEquals(0, fixture.exportCalls)
        assertEquals(
            AutomaticGooglePendingReason.TIMER_RUNNING,
            fixture.settings.readSettings().automaticGooglePendingReason,
        )
        assertFalse(fixture.notifier.posted)
        fixture.activeTimers.value = null
        fixture.manager.onTimerStopped()
        assertTrue(fixture.notifier.posted)
    }

    @Test
    fun successAdvancesExactlyOneDateAndRemainsSilent() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()

        fixture.manager.runScheduled(target.workDate.toEpochDay(), target.zoneId.id, target.connectionKey)

        assertEquals(1, fixture.exportCalls)
        assertEquals(WORK_DATE.plusDays(1), fixture.settings.readSettings().automaticGoogleTargetDate)
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
        assertFalse(fixture.notifier.posted)
    }

    @Test
    fun disablingClearsDurableTargetAndCancelsWork() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)

        fixture.manager.setEnabled(false)

        val settings = fixture.settings.readSettings()
        assertFalse(settings.automaticGoogleExportEnabled)
        assertNull(settings.automaticGoogleTargetDate)
        assertTrue(fixture.scheduler.canceled)
    }

    @Test
    fun enablingExplainsMissingSpreadsheetConnection() = runTest {
        val fixture =
            Fixture(
                googleConnection = GoogleSpreadsheetConnection(),
            )

        assertEquals(
            AutomaticGoogleExportSettingResult.SpreadsheetConnectionRequired,
            fixture.manager.setEnabled(true),
        )
    }

    @Test
    fun enablingExplainsWrongExportDestination() = runTest {
        val fixture =
            Fixture(
                appSettings = AppSettings(defaultExportDestination = ExportDestination.CSV),
            )

        assertEquals(
            AutomaticGoogleExportSettingResult.GoogleSheetsDestinationRequired,
            fixture.manager.setEnabled(true),
        )
    }

    @Test
    fun enablingExplainsMissingNotificationPermission() = runTest {
        val fixture = Fixture(notificationPermissionRequired = true)

        assertEquals(
            AutomaticGoogleExportSettingResult.NotificationPermissionRequired,
            fixture.manager.setEnabled(true),
        )
    }

    @Test
    fun enablingExplainsNotificationsDisabledInDeviceSettings() = runTest {
        val fixture = Fixture(notificationSettingsDisabled = true)

        assertEquals(
            AutomaticGoogleExportSettingResult.NotificationSettingsRequired,
            fixture.manager.setEnabled(true),
        )
    }

    private class Fixture(
        appSettings: AppSettings =
            AppSettings(defaultExportDestination = ExportDestination.GOOGLE_SHEETS),
        googleConnection: GoogleSpreadsheetConnection =
            GoogleSpreadsheetConnection(
                accountId = "person@example.com",
                spreadsheetId = "spreadsheet-id",
                spreadsheetTitle = "Work Log",
                isValidatedForCurrentAccount = true,
            ),
        notificationPermissionRequired: Boolean = false,
        notificationSettingsDisabled: Boolean = false,
    ) {
        val settings =
            FakeSettingsRepository(appSettings)
        val connection =
            FakeGoogleConnectionRepository(googleConnection)
        val activeTimers = StubActiveTimerRepository()
        val scheduler = FakeScheduler()
        val notifier =
            FakeNotifier(
                permissionRequired = notificationPermissionRequired,
                settingsDisabled = notificationSettingsDisabled,
            )
        var exportCalls = 0
        val manager =
            AutomaticGoogleExportManager(
                settingsRepository = settings,
                connectionRepository = connection,
                activeTimerRepository = activeTimers,
                zoneIdProvider = FakeZoneIdProvider(ZONE),
                clock = FakeUtcClock(NOW),
                exportDate = { date ->
                    exportCalls += 1
                    GoogleSheetsExportOperationResult.Success(
                        GoogleSheetExportReceipt(
                            workDate = date,
                            exportedAt = NOW,
                            spreadsheetId = "spreadsheet-id",
                            spreadsheetTitle = "Work Log",
                            tabName = "WorqOrder_$date",
                            dataRowCount = 0,
                        ),
                    )
                },
                workScheduler = scheduler,
                notifier = notifier,
            )
    }

    private class FakeScheduler : AutomaticGoogleExportWorkScheduler {
        val targets = mutableListOf<AutomaticGoogleExportTarget>()
        var canceled = false

        override fun schedule(target: AutomaticGoogleExportTarget, now: Instant) {
            targets.clear()
            targets += target
        }

        override fun cancel() {
            canceled = true
            targets.clear()
        }
    }

    private class FakeNotifier(
        private val permissionRequired: Boolean = false,
        private val settingsDisabled: Boolean = false,
    ) : AutomaticGoogleExportAttentionNotifier {
        var posted = false
        override fun requiresRuntimePermission(): Boolean = permissionRequired
        override fun notificationsDisabledInSettings(): Boolean = settingsDisabled
        override fun postAttentionRequired() { posted = true }
        override fun cancel() { posted = false }
    }

    private class StubActiveTimerRepository : ActiveTimerRepository {
        val flow = MutableStateFlow<ActiveTimer?>(null)
        var value: ActiveTimer?
            get() = flow.value
            set(value) { flow.value = value }
        override fun observeActiveTimer(): Flow<ActiveTimer?> = flow
        override suspend fun readActiveTimer(): ActiveTimer? = value
        override suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot? = null
        override suspend fun createActiveInterval(taskId: String, boundaryZoneId: ZoneId, start: Instant) =
            CreateActiveIntervalResult.AlreadyActive
        override suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot? = null
        override suspend fun normalizeActiveInterval(expectedIntervalId: String, boundaries: List<TimerSplitBoundary>): ActiveTimerSnapshot? = null
        override suspend fun closeActiveInterval(expectedIntervalId: String, boundaries: List<TimerSplitBoundary>, stop: Instant): ActiveTimerSnapshot? = null
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val WORK_DATE: LocalDate = LocalDate.of(2026, 8, 6)
        val NOW: Instant = Instant.parse("2026-08-06T16:00:00Z")
        fun activeTimer() =
            ActiveTimer("interval", "task", ZONE, NOW, NOW)
    }
}
