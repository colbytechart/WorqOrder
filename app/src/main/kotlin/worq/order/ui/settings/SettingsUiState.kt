package worq.order.ui.settings

import java.time.ZoneId
import worq.order.data.ExportDestination
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode

enum class SettingsMessage {
    STOP_TIMER_BEFORE_TIME_ZONE_CHANGE,
    INVALID_TIME_ZONE,
    DATA_UNAVAILABLE,
}

data class ZoneOptionUi(
    val zoneId: ZoneId,
    val friendlyName: String,
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val timeZoneMode: TimeZoneMode = TimeZoneMode.DEVICE,
    val manualZoneId: ZoneId? = null,
    val effectiveZoneId: ZoneId = ZoneId.of("UTC"),
    val defaultExportDestination: ExportDestination = ExportDestination.CSV,
    val isTimerRunning: Boolean = false,
    val isSaving: Boolean = false,
    val isZoneSelectorVisible: Boolean = false,
    val zoneSearchQuery: String = "",
    val zoneOptions: List<ZoneOptionUi> = emptyList(),
    val message: SettingsMessage? = null,
)

sealed interface SettingsEvent {
    data class SelectTheme(
        val themeMode: ThemeMode,
    ) : SettingsEvent

    data object SelectDeviceTimeZone : SettingsEvent

    data object SelectManualTimeZone : SettingsEvent

    data object OpenZoneSelector : SettingsEvent

    data class EditZoneSearch(
        val query: String,
    ) : SettingsEvent

    data class SelectManualZone(
        val zoneId: ZoneId,
    ) : SettingsEvent

    data object DismissZoneSelector : SettingsEvent

    data class SelectExportDestination(
        val destination: ExportDestination,
    ) : SettingsEvent

    data object DismissMessage : SettingsEvent
}
