package worq.order.ui.settings

import java.time.ZoneId
import worq.order.data.ExportDestination
import worq.order.data.LandscapeHandedness
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode

enum class SettingsMessage {
    STOP_TIMER_BEFORE_TIME_ZONE_CHANGE,
    INVALID_TIME_ZONE,
    DATA_UNAVAILABLE,
}

enum class GoogleConnectionUiStatus {
    SIGNED_OUT,
    SIGNING_IN,
    SIGNED_IN_NO_SPREADSHEET,
    VALIDATING_SPREADSHEET,
    CONNECTED,
    AUTHORIZATION_EXPIRED,
    OFFLINE_ERROR,
    ERROR,
    SIGNING_OUT,
    DISCONNECTING,
}

enum class GoogleSettingsMessage {
    INVALID_SPREADSHEET_INPUT,
    NO_CREDENTIAL,
    CREDENTIAL_PROVIDER_UNAVAILABLE,
    SIGN_IN_FAILED,
    AUTHORIZATION_REQUIRED,
    PLAY_SERVICES_UNAVAILABLE,
    PICKER_RETURNED_DIFFERENT_FILE,
    NOT_FOUND_OR_NOT_GRANTED,
    NOT_GOOGLE_SPREADSHEET,
    READ_ONLY,
    CONTENT_MODIFICATION_RESTRICTED,
    OFFLINE,
    TIMEOUT,
    WORKSPACE_POLICY_BLOCKED,
    RATE_LIMITED,
    SERVER_FAILURE,
    MALFORMED_RESPONSE,
    LOCAL_STORAGE,
    SIGN_OUT_PARTIAL,
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
    val landscapeHandedness: LandscapeHandedness =
        LandscapeHandedness.RIGHT_HANDED,
    val isTimerRunning: Boolean = false,
    val isSaving: Boolean = false,
    val isZoneSelectorVisible: Boolean = false,
    val zoneSearchQuery: String = "",
    val zoneOptions: List<ZoneOptionUi> = emptyList(),
    val message: SettingsMessage? = null,
    val googleStatus: GoogleConnectionUiStatus =
        GoogleConnectionUiStatus.SIGNED_OUT,
    val googleAccountId: String? = null,
    val googleAccountDisplayName: String? = null,
    val spreadsheetInput: String = "",
    val connectedSpreadsheetId: String? = null,
    val connectedSpreadsheetTitle: String? = null,
    val googleMessage: GoogleSettingsMessage? = null,
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

    data class SelectLandscapeHandedness(
        val handedness: LandscapeHandedness,
    ) : SettingsEvent

    data class EditSpreadsheetInput(
        val input: String,
    ) : SettingsEvent

    data object SignInToGoogle : SettingsEvent

    data object ValidateAndConnectSpreadsheet : SettingsEvent

    data object RetryGoogleAuthorization : SettingsEvent

    data object RetrySpreadsheetValidation : SettingsEvent

    data object DisconnectSpreadsheet : SettingsEvent

    data object SignOutOfGoogle : SettingsEvent

    data object DismissGoogleMessage : SettingsEvent

    data object DismissMessage : SettingsEvent
}

sealed interface SettingsEffect {
    data object SignInToGoogle : SettingsEffect

    data class ValidateAndConnectSpreadsheet(
        val spreadsheetInput: String,
    ) : SettingsEffect

    data object DisconnectSpreadsheet : SettingsEffect

    data object SignOutOfGoogle : SettingsEffect
}
