package worq.order.export.automatic

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.AutomaticGooglePendingReason
import worq.order.data.ExportDestination
import worq.order.data.GoogleConnectionRepository
import worq.order.data.GoogleSpreadsheetConnection
import worq.order.data.SettingsRepository
import worq.order.export.google.GoogleSheetsExportFailure
import worq.order.export.google.GoogleSheetsExportOperationResult
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.UtcClock

sealed interface AutomaticGoogleExportSettingResult {
    data object Enabled : AutomaticGoogleExportSettingResult
    data object Disabled : AutomaticGoogleExportSettingResult
    data object SpreadsheetConnectionRequired : AutomaticGoogleExportSettingResult
    data object GoogleSheetsDestinationRequired : AutomaticGoogleExportSettingResult
    data object NotificationPermissionRequired : AutomaticGoogleExportSettingResult
    data object NotificationSettingsRequired : AutomaticGoogleExportSettingResult
    data object Failed : AutomaticGoogleExportSettingResult
}

interface AutomaticGoogleExportController {
    suspend fun setEnabled(enabled: Boolean): AutomaticGoogleExportSettingResult

    suspend fun onTimerStopped()

    suspend fun completeInteractiveExport(
        workDate: LocalDate,
        result: GoogleSheetsExportOperationResult,
    )
}

class AutomaticGoogleExportManager(
    private val settingsRepository: SettingsRepository,
    private val connectionRepository: GoogleConnectionRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val clock: UtcClock,
    private val exportDate: suspend (LocalDate) -> GoogleSheetsExportOperationResult,
    private val workScheduler: AutomaticGoogleExportWorkScheduler,
    private val notifier: AutomaticGoogleExportAttentionNotifier,
) : AutomaticGoogleExportController {
    private val mutex = Mutex()

    override suspend fun setEnabled(enabled: Boolean): AutomaticGoogleExportSettingResult =
        mutex.withLock {
            if (!enabled) {
                settingsRepository.setAutomaticGoogleExportEnabled(false)
                cancelWorkAndNotification()
                return@withLock AutomaticGoogleExportSettingResult.Disabled
            }
            val connection = connectionRepository.readConnection()
            if (!connection.isConnected) {
                return@withLock AutomaticGoogleExportSettingResult.SpreadsheetConnectionRequired
            }
            if (settingsRepository.readSettings().defaultExportDestination != ExportDestination.GOOGLE_SHEETS) {
                return@withLock AutomaticGoogleExportSettingResult.GoogleSheetsDestinationRequired
            }
            if (notifier.requiresRuntimePermission()) {
                return@withLock AutomaticGoogleExportSettingResult.NotificationPermissionRequired
            }
            if (notifier.notificationsDisabledInSettings()) {
                return@withLock AutomaticGoogleExportSettingResult.NotificationSettingsRequired
            }
            settingsRepository.setAutomaticGoogleExportEnabled(true)
            val zoneId = zoneIdProvider.zoneId()
            val date = clock.now().atZone(zoneId).toLocalDate()
            persistAndSchedule(date, zoneId, connection.connectionKey())
            AutomaticGoogleExportSettingResult.Enabled
        }

    suspend fun reconcile() = mutex.withLock {
        val settings = settingsRepository.readSettings()
        if (!settings.automaticGoogleExportEnabled ||
            settings.defaultExportDestination != ExportDestination.GOOGLE_SHEETS
        ) {
            cancelWorkAndNotification()
            return@withLock
        }
        val connection = connectionRepository.readConnection()
        if (!connection.isConnected) {
            settingsRepository.setAutomaticGoogleExportEnabled(false)
            cancelWorkAndNotification()
            return@withLock
        }
        val date = settings.automaticGoogleTargetDate
        val zoneId = settings.automaticGoogleTargetZoneId
        val key = settings.automaticGoogleTargetConnectionKey
        if (date == null || zoneId == null || key == null) {
            val effectiveZone = zoneIdProvider.zoneId()
            persistAndSchedule(
                clock.now().atZone(effectiveZone).toLocalDate(),
                effectiveZone,
                connection.connectionKey(),
            )
        } else if (settings.automaticGooglePendingReason == null) {
            schedule(date, zoneId, key)
        }
    }

    suspend fun runScheduled(
        workDateEpochDay: Long,
        zoneIdText: String,
        connectionKey: String,
    ) = mutex.withLock {
        val date = runCatching { LocalDate.ofEpochDay(workDateEpochDay) }.getOrNull() ?: return@withLock
        val zoneId = runCatching { ZoneId.of(zoneIdText) }.getOrNull() ?: return@withLock
        val settings = settingsRepository.readSettings()
        if (!settings.automaticGoogleExportEnabled ||
            settings.defaultExportDestination != ExportDestination.GOOGLE_SHEETS ||
            settings.automaticGoogleTargetDate != date ||
            settings.automaticGoogleTargetZoneId != zoneId ||
            settings.automaticGoogleTargetConnectionKey != connectionKey
        ) return@withLock
        val connection = connectionRepository.readConnection()
        if (!connection.isConnected || connection.connectionKey() != connectionKey) {
            markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.AUTHORIZATION_REQUIRED, true)
            return@withLock
        }
        if (activeTimerRepository.readActiveTimer() != null) {
            markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.TIMER_RUNNING, false)
            return@withLock
        }
        val result =
            try {
                exportDate(date)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                GoogleSheetsExportOperationResult.Failed(
                    GoogleSheetsExportFailure.LOCAL_STORAGE,
                )
            }
        handleResult(date, zoneId, connectionKey, result)
    }

    override suspend fun onTimerStopped() = mutex.withLock {
        val settings = settingsRepository.readSettings()
        if (settings.automaticGooglePendingReason == AutomaticGooglePendingReason.TIMER_RUNNING) {
            notifier.postAttentionRequired()
        }
    }

    override suspend fun completeInteractiveExport(
        workDate: LocalDate,
        result: GoogleSheetsExportOperationResult,
    ) = mutex.withLock {
        val settings = settingsRepository.readSettings()
        val zoneId = settings.automaticGoogleTargetZoneId ?: return@withLock
        val key = settings.automaticGoogleTargetConnectionKey ?: return@withLock
        if (settings.automaticGoogleTargetDate != workDate) return@withLock
        handleResult(workDate, zoneId, key, result)
    }

    private suspend fun handleResult(
        date: LocalDate,
        zoneId: ZoneId,
        connectionKey: String,
        result: GoogleSheetsExportOperationResult,
    ) {
        when (result) {
            is GoogleSheetsExportOperationResult.Success -> {
                notifier.cancel()
                persistAndSchedule(
                    date.plusDays(1),
                    zoneIdProvider.zoneId(),
                    connectionKey,
                )
            }
            GoogleSheetsExportOperationResult.ActiveTimerChanged ->
                markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.TIMER_RUNNING, false)
            GoogleSheetsExportOperationResult.AuthorizationRequired,
            GoogleSheetsExportOperationResult.Canceled,
            -> markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.AUTHORIZATION_REQUIRED, true)
            GoogleSheetsExportOperationResult.SetupRequired ->
                markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.AUTHORIZATION_REQUIRED, true)
            GoogleSheetsExportOperationResult.ClockChanged ->
                markPending(date, zoneId, connectionKey, AutomaticGooglePendingReason.LOCAL_STORAGE, true)
            is GoogleSheetsExportOperationResult.Failed ->
                markPending(date, zoneId, connectionKey, result.reason.toPendingReason(), true)
        }
    }

    private suspend fun persistAndSchedule(date: LocalDate, zoneId: ZoneId, connectionKey: String) {
        settingsRepository.setAutomaticGoogleExportTarget(date, zoneId, connectionKey)
        schedule(date, zoneId, connectionKey)
    }

    private suspend fun markPending(
        date: LocalDate,
        zoneId: ZoneId,
        connectionKey: String,
        reason: AutomaticGooglePendingReason,
        notify: Boolean,
    ) {
        settingsRepository.setAutomaticGoogleExportTarget(date, zoneId, connectionKey, reason)
        workScheduler.cancel()
        if (notify) notifier.postAttentionRequired()
    }

    private fun schedule(date: LocalDate, zoneId: ZoneId, connectionKey: String) {
        workScheduler.schedule(
            AutomaticGoogleExportTarget(date, zoneId, connectionKey),
            clock.now(),
        )
    }

    private fun cancelWorkAndNotification() {
        workScheduler.cancel()
        notifier.cancel()
    }

    private fun GoogleSpreadsheetConnection.connectionKey(): String =
        "${accountId.orEmpty()}\n${spreadsheetId.orEmpty()}"

    private fun GoogleSheetsExportFailure.toPendingReason(): AutomaticGooglePendingReason =
        when (this) {
            GoogleSheetsExportFailure.LOCAL_STORAGE -> AutomaticGooglePendingReason.LOCAL_STORAGE
            GoogleSheetsExportFailure.PLAY_SERVICES_UNAVAILABLE -> AutomaticGooglePendingReason.GOOGLE_PLAY_SERVICES
            GoogleSheetsExportFailure.OFFLINE -> AutomaticGooglePendingReason.OFFLINE
            GoogleSheetsExportFailure.TIMEOUT -> AutomaticGooglePendingReason.TIMEOUT
            GoogleSheetsExportFailure.NOT_FOUND_OR_NOT_GRANTED -> AutomaticGooglePendingReason.NOT_FOUND_OR_NOT_GRANTED
            GoogleSheetsExportFailure.PERMISSION_DENIED -> AutomaticGooglePendingReason.PERMISSION_DENIED
            GoogleSheetsExportFailure.RATE_LIMITED -> AutomaticGooglePendingReason.RATE_LIMITED
            GoogleSheetsExportFailure.SERVER_FAILURE -> AutomaticGooglePendingReason.SERVER_FAILURE
            GoogleSheetsExportFailure.MALFORMED_RESPONSE -> AutomaticGooglePendingReason.MALFORMED_RESPONSE
            GoogleSheetsExportFailure.TAB_NAME_CONFLICT -> AutomaticGooglePendingReason.TAB_NAME_CONFLICT
            GoogleSheetsExportFailure.SCHEMA_CONFLICT -> AutomaticGooglePendingReason.SCHEMA_CONFLICT
            GoogleSheetsExportFailure.AMBIGUOUS_REMOTE_RESULT -> AutomaticGooglePendingReason.AMBIGUOUS_REMOTE_RESULT
        }

}
