package worq.order.data

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

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val timeZoneMode: TimeZoneMode = TimeZoneMode.DEVICE,
    val manualZoneId: ZoneId? = null,
    val defaultExportDestination: ExportDestination = ExportDestination.CSV,
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
}
