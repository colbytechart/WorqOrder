package worq.order.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TimeZoneMode {
    DEVICE,
    MANUAL,
}

enum class ExportDestination {
    CSV,
    GOOGLE_SHEETS,
}

enum class ExportAttemptOutcome {
    SUCCESS,
    CANCELED,
    FAILED,
}

enum class ExportErrorCategory {
    PREPARATION,
    CLOCK_CHANGED,
    ACTIVE_TIMER_CHANGED,
    OUTPUT,
    PARTIAL_OUTPUT,
}

data class LastExportAttempt(
    val destination: ExportDestination,
    val workDate: LocalDate,
    val attemptedAt: Instant,
    val outcome: ExportAttemptOutcome,
    val errorCategory: ExportErrorCategory? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val timeZoneMode: TimeZoneMode = TimeZoneMode.DEVICE,
    val manualZoneId: ZoneId? = null,
    val defaultExportDestination: ExportDestination = ExportDestination.CSV,
    val lastExportAttempt: LastExportAttempt? = null,
)

sealed interface TimeZoneSettingResult {
    data class Updated(
        val settings: AppSettings,
    ) : TimeZoneSettingResult

    data object BlockedByActiveTimer : TimeZoneSettingResult

    data object InvalidManualZone : TimeZoneSettingResult
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun readSettings(): AppSettings

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setTimeZoneMode(timeZoneMode: TimeZoneMode): TimeZoneSettingResult

    suspend fun setManualZoneId(zoneId: ZoneId): TimeZoneSettingResult

    suspend fun setDefaultExportDestination(destination: ExportDestination)

    suspend fun recordLastExportAttempt(attempt: LastExportAttempt)
}
