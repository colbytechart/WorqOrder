package worq.order.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.AppSettings
import worq.order.data.ExportDestination
import worq.order.data.ExportAttemptOutcome
import worq.order.data.ExportErrorCategory
import worq.order.data.LastExportAttempt
import worq.order.data.SettingsRepository
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.TimeZoneSettingResult
import worq.order.timer.GeographicalZoneIds
import worq.order.timer.TimerOperationLock

class PreferencesSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val activeTimerRepository: ActiveTimerRepository,
    private val timerOperationLock: TimerOperationLock,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException || error is CorruptionException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map(::settingsFromPreferences)

    override suspend fun readSettings(): AppSettings =
        observeSettings().first()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setTimeZoneMode(
        timeZoneMode: TimeZoneMode,
    ): TimeZoneSettingResult =
        timerOperationLock.mutex.withLock {
            if (activeTimerRepository.readActiveTimer() != null) {
                return@withLock TimeZoneSettingResult.BlockedByActiveTimer
            }
            val current = readSettings()
            if (timeZoneMode == TimeZoneMode.MANUAL && current.manualZoneId == null) {
                return@withLock TimeZoneSettingResult.InvalidManualZone
            }
            dataStore.edit { preferences ->
                preferences[TIME_ZONE_MODE] = timeZoneMode.name
            }
            TimeZoneSettingResult.Updated(readSettings())
        }

    override suspend fun setManualZoneId(
        zoneId: ZoneId,
    ): TimeZoneSettingResult {
        if (!GeographicalZoneIds.isSelectable(zoneId)) {
            return TimeZoneSettingResult.InvalidManualZone
        }
        return timerOperationLock.mutex.withLock {
            if (activeTimerRepository.readActiveTimer() != null) {
                return@withLock TimeZoneSettingResult.BlockedByActiveTimer
            }
            dataStore.edit { preferences ->
                preferences[MANUAL_ZONE_ID] = zoneId.id
                preferences[TIME_ZONE_MODE] = TimeZoneMode.MANUAL.name
            }
            TimeZoneSettingResult.Updated(readSettings())
        }
    }

    override suspend fun setDefaultExportDestination(
        destination: ExportDestination,
    ) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_EXPORT_DESTINATION] = destination.name
        }
    }

    override suspend fun recordLastExportAttempt(attempt: LastExportAttempt) {
        dataStore.edit { preferences ->
            preferences[LAST_EXPORT_DESTINATION] = attempt.destination.name
            preferences[LAST_EXPORT_WORK_DATE] = attempt.workDate.toEpochDay()
            preferences[LAST_EXPORT_ATTEMPTED_AT] = attempt.attemptedAt.toEpochMilli()
            preferences[LAST_EXPORT_OUTCOME] = attempt.outcome.name
            if (attempt.errorCategory == null) {
                preferences.remove(LAST_EXPORT_ERROR_CATEGORY)
            } else {
                preferences[LAST_EXPORT_ERROR_CATEGORY] =
                    attempt.errorCategory.name
            }
        }
    }

    private fun settingsFromPreferences(preferences: Preferences): AppSettings {
        val themeMode =
            preferences[THEME_MODE]
                .enumOrDefault(ThemeMode.SYSTEM)
        val manualZoneId =
            preferences[MANUAL_ZONE_ID]
                ?.let { storedId -> runCatching { ZoneId.of(storedId) }.getOrNull() }
                ?.takeIf(GeographicalZoneIds::isSelectable)
        val requestedZoneMode =
            preferences[TIME_ZONE_MODE]
                .enumOrDefault(TimeZoneMode.DEVICE)
        val timeZoneMode =
            if (requestedZoneMode == TimeZoneMode.MANUAL && manualZoneId == null) {
                TimeZoneMode.DEVICE
            } else {
                requestedZoneMode
            }
        val exportDestination =
            preferences[DEFAULT_EXPORT_DESTINATION]
                .enumOrDefault(ExportDestination.CSV)
        val lastExportAttempt = preferences.lastExportAttemptOrNull()
        return AppSettings(
            themeMode = themeMode,
            timeZoneMode = timeZoneMode,
            manualZoneId = manualZoneId,
            defaultExportDestination = exportDestination,
            lastExportAttempt = lastExportAttempt,
        )
    }

    private fun Preferences.lastExportAttemptOrNull(): LastExportAttempt? {
        val destination =
            this[LAST_EXPORT_DESTINATION]
                ?.let { stored ->
                    enumValues<ExportDestination>().firstOrNull {
                        it.name == stored
                    }
                } ?: return null
        val workDate =
            this[LAST_EXPORT_WORK_DATE]
                ?.let { epochDay ->
                    runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull()
                } ?: return null
        val attemptedAt =
            this[LAST_EXPORT_ATTEMPTED_AT]
                ?.let { epochMillis ->
                    runCatching { Instant.ofEpochMilli(epochMillis) }.getOrNull()
                } ?: return null
        val outcome =
            this[LAST_EXPORT_OUTCOME]
                ?.let { stored ->
                    enumValues<ExportAttemptOutcome>().firstOrNull {
                        it.name == stored
                    }
                } ?: return null
        val errorCategory =
            this[LAST_EXPORT_ERROR_CATEGORY]
                ?.let { stored ->
                    enumValues<ExportErrorCategory>().firstOrNull {
                        it.name == stored
                    }
                }
        return LastExportAttempt(
            destination = destination,
            workDate = workDate,
            attemptedAt = attemptedAt,
            outcome = outcome,
            errorCategory = errorCategory,
        )
    }

    private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
        this
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TIME_ZONE_MODE = stringPreferencesKey("time_zone_mode")
        val MANUAL_ZONE_ID = stringPreferencesKey("manual_zone_id")
        val DEFAULT_EXPORT_DESTINATION =
            stringPreferencesKey("default_export_destination")
        val LAST_EXPORT_DESTINATION =
            stringPreferencesKey("last_export_destination")
        val LAST_EXPORT_WORK_DATE =
            longPreferencesKey("last_export_work_date_epoch_day")
        val LAST_EXPORT_ATTEMPTED_AT =
            longPreferencesKey("last_export_attempted_at_epoch_ms")
        val LAST_EXPORT_OUTCOME =
            stringPreferencesKey("last_export_outcome")
        val LAST_EXPORT_ERROR_CATEGORY =
            stringPreferencesKey("last_export_error_category")
    }
}
