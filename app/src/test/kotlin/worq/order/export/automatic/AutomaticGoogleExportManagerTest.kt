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
import worq.order.export.google.GoogleSheetExportReceipt
import worq.order.export.google.GoogleSheetsExportFailure
import worq.order.export.google.GoogleSheetsExportOperationResult
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.testing.FakeGoogleConnectionRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.timer.ActiveTimerBoundaryReconciler
import worq.order.timer.NormalizeTimerResult

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
        fixture.clock.instant = AFTER_BOUNDARY

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
        fixture.clock.instant = AFTER_BOUNDARY

        fixture.manager.runScheduled(target.workDate.toEpochDay(), target.zoneId.id, target.connectionKey)

        assertEquals(1, fixture.exportCalls)
        assertEquals(WORK_DATE.plusDays(1), fixture.settings.readSettings().automaticGoogleTargetDate)
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
        assertFalse(fixture.notifier.posted)
    }

    @Test
    fun foregroundReconcileRunsAnOverdueTargetWithoutWaitingForWorkManager() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        fixture.clock.instant = AFTER_BOUNDARY

        fixture.manager.reconcile()

        assertEquals(1, fixture.exportCalls)
        assertEquals(
            WORK_DATE.plusDays(1),
            fixture.settings.readSettings().automaticGoogleTargetDate,
        )
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
        assertFalse(fixture.notifier.posted)
    }

    @Test
    fun authorizationPendingSurvivesReconcileAndRemainsActionable() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()
        fixture.clock.instant = AFTER_BOUNDARY
        fixture.exportResult = GoogleSheetsExportOperationResult.AuthorizationRequired

        fixture.manager.runScheduled(
            target.workDate.toEpochDay(),
            target.zoneId.id,
            target.connectionKey,
        )
        fixture.connection.markAuthorizationRequired()
        fixture.notifier.posted = false
        fixture.manager.reconcile()

        val settings = fixture.settings.readSettings()
        assertTrue(settings.automaticGoogleExportEnabled)
        assertEquals(WORK_DATE, settings.automaticGoogleTargetDate)
        assertEquals(
            AutomaticGooglePendingReason.AUTHORIZATION_REQUIRED,
            settings.automaticGooglePendingReason,
        )
        assertTrue(fixture.notifier.posted)
    }

    @Test
    fun boundaryStopRunsAnOverdueTargetWithoutWaitingForWorkManager() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        fixture.clock.instant = AFTER_BOUNDARY

        fixture.manager.onTimerStopped()

        assertEquals(1, fixture.exportCalls)
        assertEquals(
            WORK_DATE.plusDays(1),
            fixture.settings.readSettings().automaticGoogleTargetDate,
        )
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
        assertFalse(fixture.notifier.posted)
    }

    @Test
    fun repeatedDeliveryOfCompletedWorkerDoesNotDuplicateExport() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()
        fixture.clock.instant = AFTER_BOUNDARY

        fixture.manager.runScheduled(
            target.workDate.toEpochDay(),
            target.zoneId.id,
            target.connectionKey,
        )
        fixture.manager.runScheduled(
            target.workDate.toEpochDay(),
            target.zoneId.id,
            target.connectionKey,
        )

        assertEquals(1, fixture.exportCalls)
        assertEquals(
            WORK_DATE.plusDays(1),
            fixture.settings.readSettings().automaticGoogleTargetDate,
        )
    }

    @Test
    fun scheduledWorkBeforeTheCapturedDatesBoundaryDoesNotExportEarly() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()

        fixture.manager.runScheduled(target.workDate.toEpochDay(), target.zoneId.id, target.connectionKey)

        assertEquals(0, fixture.exportCalls)
        assertEquals(WORK_DATE, fixture.settings.readSettings().automaticGoogleTargetDate)
        assertEquals(1, fixture.scheduler.targets.size)
        assertEquals(WORK_DATE, fixture.scheduler.targets.single().workDate)
    }

    @Test
    fun staleEarlyWorkerCannotReplaceTheCurrentCapturedTarget() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val staleTarget = fixture.scheduler.targets.single()
        fixture.settings.setAutomaticGoogleExportTarget(
            workDate = WORK_DATE.plusDays(1),
            zoneId = ZONE,
            connectionKey = staleTarget.connectionKey,
        )
        fixture.scheduler.targets.clear()

        fixture.manager.runScheduled(
            staleTarget.workDate.toEpochDay(),
            staleTarget.zoneId.id,
            staleTarget.connectionKey,
        )

        assertEquals(0, fixture.exportCalls)
        assertTrue(fixture.scheduler.targets.isEmpty())
        assertEquals(
            WORK_DATE.plusDays(1),
            fixture.settings.readSettings().automaticGoogleTargetDate,
        )
    }

    @Test
    fun lateWorkNormalizesAStaleTargetIntervalBeforeExporting() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()
        fixture.clock.instant = AFTER_BOUNDARY
        fixture.activeTimers.value = activeTimer()
        fixture.onNormalize = {
            fixture.activeTimers.value = null
            NormalizeTimerResult.NoActiveTimer
        }

        fixture.manager.runScheduled(target.workDate.toEpochDay(), target.zoneId.id, target.connectionKey)

        assertEquals(1, fixture.normalizationCount)
        assertEquals(1, fixture.exportCalls)
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
    }

    @Test
    fun competingBoundaryCloseDoesNotLeaveACompletedTargetPending() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()
        fixture.clock.instant = AFTER_BOUNDARY
        fixture.onNormalize = { NormalizeTimerResult.ActiveTimerChanged }

        fixture.manager.runScheduled(
            target.workDate.toEpochDay(),
            target.zoneId.id,
            target.connectionKey,
        )

        assertEquals(1, fixture.exportCalls)
        assertNull(fixture.settings.readSettings().automaticGooglePendingReason)
    }

    @Test
    fun localBoundaryFailureIsPersistedAndSurfacedWithoutExport() = runTest {
        val fixture = Fixture()
        fixture.manager.setEnabled(true)
        val target = fixture.scheduler.targets.single()
        fixture.clock.instant = AFTER_BOUNDARY
        fixture.onNormalize = { error("local read failed") }

        fixture.manager.runScheduled(
            target.workDate.toEpochDay(),
            target.zoneId.id,
            target.connectionKey,
        )

        assertEquals(0, fixture.exportCalls)
        assertEquals(
            AutomaticGooglePendingReason.LOCAL_STORAGE,
            fixture.settings.readSettings().automaticGooglePendingReason,
        )
        assertTrue(fixture.notifier.posted)
    }

    @Test
    fun automaticFailuresPersistTypedPendingReasonsWithoutRetrying() = runTest {
        val cases =
            listOf(
                GoogleSheetsExportOperationResult.AuthorizationRequired to
                    AutomaticGooglePendingReason.AUTHORIZATION_REQUIRED,
                GoogleSheetsExportOperationResult.Failed(GoogleSheetsExportFailure.OFFLINE) to
                    AutomaticGooglePendingReason.OFFLINE,
                GoogleSheetsExportOperationResult.Failed(GoogleSheetsExportFailure.PERMISSION_DENIED) to
                    AutomaticGooglePendingReason.PERMISSION_DENIED,
                GoogleSheetsExportOperationResult.Failed(GoogleSheetsExportFailure.RATE_LIMITED) to
                    AutomaticGooglePendingReason.RATE_LIMITED,
            )

        cases.forEach { (exportResult, expectedReason) ->
            val fixture = Fixture()
            fixture.manager.setEnabled(true)
            fixture.exportResult = exportResult
            fixture.clock.instant = AFTER_BOUNDARY
            val target = fixture.scheduler.targets.single()

            fixture.manager.runScheduled(
                target.workDate.toEpochDay(),
                target.zoneId.id,
                target.connectionKey,
            )

            assertEquals(
                expectedReason,
                fixture.settings.readSettings().automaticGooglePendingReason,
            )
            assertTrue(fixture.scheduler.targets.isEmpty())
        }
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
        val clock = FakeUtcClock(NOW)
        val scheduler = FakeScheduler()
        val notifier =
            FakeNotifier(
                permissionRequired = notificationPermissionRequired,
                settingsDisabled = notificationSettingsDisabled,
            )
        var exportCalls = 0
        var exportResult: GoogleSheetsExportOperationResult =
            GoogleSheetsExportOperationResult.Success(
                GoogleSheetExportReceipt(
                    workDate = WORK_DATE,
                    exportedAt = NOW,
                    spreadsheetId = "spreadsheet-id",
                    spreadsheetTitle = "Work Log",
                    tabName = "WorqOrder_$WORK_DATE",
                    dataRowCount = 0,
                ),
            )
        var normalizationCount = 0
        var onNormalize: () -> NormalizeTimerResult = {
            NormalizeTimerResult.NoActiveTimer
        }
        val manager =
            AutomaticGoogleExportManager(
                settingsRepository = settings,
                connectionRepository = connection,
                activeTimerRepository = activeTimers,
                boundaryReconciler =
                    ActiveTimerBoundaryReconciler {
                        normalizationCount += 1
                        onNormalize()
                    },
                zoneIdProvider = FakeZoneIdProvider(ZONE),
                clock = clock,
                exportDate = { date ->
                    exportCalls += 1
                    when (val result = exportResult) {
                        is GoogleSheetsExportOperationResult.Success ->
                            result.copy(
                                receipt = result.receipt.copy(workDate = date),
                            )
                        else -> result
                    }
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
        override suspend fun closeActiveIntervalAtBoundary(
            expectedIntervalId: String,
            boundary: Instant,
        ): ActiveTimerSnapshot? = null
        override suspend fun closeActiveInterval(
            expectedIntervalId: String,
            stop: Instant,
        ): ActiveTimerSnapshot? = null
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val WORK_DATE: LocalDate = LocalDate.of(2026, 8, 6)
        val NOW: Instant = Instant.parse("2026-08-06T16:00:00Z")
        val AFTER_BOUNDARY: Instant = Instant.parse("2026-08-07T04:01:00Z")
        fun activeTimer() =
            ActiveTimer("interval", "task", ZONE, NOW, NOW)
    }
}
