package worq.order.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.ActiveTimerRepository
import worq.order.data.AppSettings
import worq.order.data.ExportDestination
import worq.order.data.GoogleConnectionRepository
import worq.order.data.GoogleSpreadsheetConnection
import worq.order.data.LandscapeHandedness
import worq.order.data.SettingsRepository
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.TimeZoneSettingResult
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.GeographicalZoneIds
import worq.order.export.google.GoogleConnectionFailure
import worq.order.export.google.GoogleConnectionOperationResult

private data class SettingsEditorState(
    val isSaving: Boolean = false,
    val isZoneSelectorVisible: Boolean = false,
    val zoneSearchQuery: String = "",
    val message: SettingsMessage? = null,
    val spreadsheetInput: String = "",
    val googleOperationStatus: GoogleConnectionUiStatus? = null,
    val googleStatusOverride: GoogleConnectionUiStatus? = null,
    val googleMessage: GoogleSettingsMessage? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val googleConnectionRepository: GoogleConnectionRepository,
) : ViewModel() {
    private val editorState = MutableStateFlow(SettingsEditorState())
    private val mutableEffects =
        MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)

    val effects = mutableEffects.asSharedFlow()

    val uiState =
        combine(
            settingsRepository.observeSettings(),
            activeTimerRepository.observeActiveTimer(),
            zoneIdProvider.observeZoneId(),
            googleConnectionRepository.observeConnection(),
            editorState,
        ) { settings, activeTimer, effectiveZoneId, googleConnection, editor ->
            settings.toUiState(
                effectiveZoneId = effectiveZoneId,
                isTimerRunning = activeTimer != null,
                googleConnection = googleConnection,
                editor = editor,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue =
                AppSettings().toUiState(
                    effectiveZoneId = zoneIdProvider.zoneId(),
                    isTimerRunning = false,
                    googleConnection = GoogleSpreadsheetConnection(),
                    editor = SettingsEditorState(),
                ),
        )

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectTheme -> setTheme(event.themeMode)
            SettingsEvent.SelectDeviceTimeZone ->
                updateTimeZoneMode(TimeZoneMode.DEVICE)
            SettingsEvent.SelectManualTimeZone -> selectManualTimeZoneMode()
            SettingsEvent.OpenZoneSelector -> openZoneSelector()
            is SettingsEvent.EditZoneSearch ->
                editorState.update { it.copy(zoneSearchQuery = event.query) }
            is SettingsEvent.SelectManualZone -> setManualZone(event.zoneId)
            SettingsEvent.DismissZoneSelector ->
                editorState.update {
                    it.copy(
                        isZoneSelectorVisible = false,
                        zoneSearchQuery = "",
                    )
                }
            is SettingsEvent.SelectExportDestination ->
                setExportDestination(event.destination)
            is SettingsEvent.SelectLandscapeHandedness ->
                setLandscapeHandedness(event.handedness)
            is SettingsEvent.EditSpreadsheetInput ->
                editorState.update {
                    it.copy(
                        spreadsheetInput = event.input,
                        googleMessage = null,
                        googleStatusOverride = null,
                    )
                }
            SettingsEvent.SignInToGoogle -> requestGoogleSignIn()
            SettingsEvent.ValidateAndConnectSpreadsheet ->
                requestSpreadsheetValidation()
            SettingsEvent.RetryGoogleAuthorization ->
                requestSpreadsheetValidation()
            SettingsEvent.RetrySpreadsheetValidation ->
                requestSpreadsheetValidation()
            SettingsEvent.DisconnectSpreadsheet ->
                requestGoogleOperation(
                    status = GoogleConnectionUiStatus.DISCONNECTING,
                    effect = SettingsEffect.DisconnectSpreadsheet,
                )
            SettingsEvent.SignOutOfGoogle ->
                requestGoogleOperation(
                    status = GoogleConnectionUiStatus.SIGNING_OUT,
                    effect = SettingsEffect.SignOutOfGoogle,
                )
            SettingsEvent.DismissGoogleMessage ->
                editorState.update {
                    it.copy(
                        googleMessage = null,
                        googleStatusOverride = null,
                    )
                }
            SettingsEvent.DismissMessage ->
                editorState.update { it.copy(message = null) }
        }
    }

    fun onGoogleOperationResult(result: GoogleConnectionOperationResult) {
        editorState.update { editor ->
            when (result) {
                is GoogleConnectionOperationResult.SignedIn ->
                    editor.copy(
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = null,
                    )
                is GoogleConnectionOperationResult.Connected ->
                    editor.copy(
                        spreadsheetInput = result.connection.spreadsheetId.orEmpty(),
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = null,
                    )
                GoogleConnectionOperationResult.Disconnected ->
                    editor.copy(
                        spreadsheetInput = "",
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = null,
                    )
                GoogleConnectionOperationResult.SignedOut ->
                    editor.copy(
                        spreadsheetInput = "",
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = null,
                    )
                GoogleConnectionOperationResult.SignOutPartiallyCompleted ->
                    editor.copy(
                        spreadsheetInput = "",
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = GoogleSettingsMessage.SIGN_OUT_PARTIAL,
                    )
                GoogleConnectionOperationResult.Canceled ->
                    editor.copy(
                        googleOperationStatus = null,
                        googleStatusOverride = null,
                        googleMessage = null,
                    )
                is GoogleConnectionOperationResult.InvalidSpreadsheetInput ->
                    editor.copy(
                        googleOperationStatus = null,
                        googleStatusOverride = GoogleConnectionUiStatus.ERROR,
                        googleMessage =
                            GoogleSettingsMessage.INVALID_SPREADSHEET_INPUT,
                    )
                is GoogleConnectionOperationResult.Failed ->
                    editor.copy(
                        googleOperationStatus = null,
                        googleStatusOverride =
                            result.reason.failureStatus(),
                        googleMessage = result.reason.toUiMessage(),
                    )
            }
        }
    }

    fun onGoogleOperationInterrupted() {
        editorState.update {
            it.copy(googleOperationStatus = null)
        }
    }

    private fun setTheme(themeMode: ThemeMode) {
        launchWrite {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    private fun setLandscapeHandedness(handedness: LandscapeHandedness) {
        launchWrite {
            settingsRepository.setLandscapeHandedness(handedness)
        }
    }

    private fun selectManualTimeZoneMode() {
        if (uiState.value.isTimerRunning) {
            showTimerRunningMessage()
            return
        }
        if (uiState.value.manualZoneId == null) {
            openZoneSelector()
        } else {
            updateTimeZoneMode(TimeZoneMode.MANUAL)
        }
    }

    private fun openZoneSelector() {
        if (uiState.value.isTimerRunning) {
            showTimerRunningMessage()
            return
        }
        editorState.update {
            it.copy(
                isZoneSelectorVisible = true,
                zoneSearchQuery = "",
                message = null,
            )
        }
    }

    private fun updateTimeZoneMode(timeZoneMode: TimeZoneMode) {
        launchTimeZoneWrite {
            settingsRepository.setTimeZoneMode(timeZoneMode)
        }
    }

    private fun setManualZone(zoneId: ZoneId) {
        launchTimeZoneWrite(closeSelectorOnSuccess = true) {
            settingsRepository.setManualZoneId(zoneId)
        }
    }

    private fun setExportDestination(destination: ExportDestination) {
        launchWrite {
            settingsRepository.setDefaultExportDestination(destination)
        }
    }

    private fun requestGoogleSignIn() {
        requestGoogleOperation(
            status = GoogleConnectionUiStatus.SIGNING_IN,
            effect = SettingsEffect.SignInToGoogle,
        )
    }

    private fun requestSpreadsheetValidation() {
        if (
            uiState.value.googleStatus ==
            GoogleConnectionUiStatus.CONNECTED
        ) {
            return
        }
        val input =
            editorState.value.spreadsheetInput
                .trim()
                .ifEmpty {
                    uiState.value.connectedSpreadsheetId.orEmpty()
                }
        editorState.update { it.copy(spreadsheetInput = input) }
        requestGoogleOperation(
            status = GoogleConnectionUiStatus.VALIDATING_SPREADSHEET,
            effect =
                SettingsEffect.ValidateAndConnectSpreadsheet(
                    spreadsheetInput = input,
                ),
        )
    }

    private fun requestGoogleOperation(
        status: GoogleConnectionUiStatus,
        effect: SettingsEffect,
    ) {
        if (editorState.value.googleOperationStatus != null) {
            return
        }
        editorState.update {
            it.copy(
                googleOperationStatus = status,
                googleStatusOverride = null,
                googleMessage = null,
            )
        }
        if (!mutableEffects.tryEmit(effect)) {
            onGoogleOperationInterrupted()
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        if (editorState.value.isSaving) {
            return
        }
        viewModelScope.launch {
            editorState.update { it.copy(isSaving = true, message = null) }
            runCatching { block() }
                .onSuccess {
                    editorState.update { it.copy(isSaving = false) }
                }.onFailure {
                    editorState.update {
                        it.copy(
                            isSaving = false,
                            message = SettingsMessage.DATA_UNAVAILABLE,
                        )
                    }
                }
        }
    }

    private fun launchTimeZoneWrite(
        closeSelectorOnSuccess: Boolean = false,
        block: suspend () -> TimeZoneSettingResult,
    ) {
        if (editorState.value.isSaving) {
            return
        }
        viewModelScope.launch {
            editorState.update { it.copy(isSaving = true, message = null) }
            val result =
                runCatching { block() }
                    .getOrElse {
                        editorState.update {
                            it.copy(
                                isSaving = false,
                                message = SettingsMessage.DATA_UNAVAILABLE,
                            )
                        }
                        return@launch
                    }
            editorState.update { editor ->
                when (result) {
                    is TimeZoneSettingResult.Updated ->
                        editor.copy(
                            isSaving = false,
                            isZoneSelectorVisible =
                                if (closeSelectorOnSuccess) {
                                    false
                                } else {
                                    editor.isZoneSelectorVisible
                                },
                            zoneSearchQuery =
                                if (closeSelectorOnSuccess) {
                                    ""
                                } else {
                                    editor.zoneSearchQuery
                                },
                        )
                    TimeZoneSettingResult.BlockedByActiveTimer ->
                        editor.copy(
                            isSaving = false,
                            isZoneSelectorVisible = false,
                            zoneSearchQuery = "",
                            message =
                                SettingsMessage.STOP_TIMER_BEFORE_TIME_ZONE_CHANGE,
                        )
                    TimeZoneSettingResult.InvalidManualZone ->
                        editor.copy(
                            isSaving = false,
                            message = SettingsMessage.INVALID_TIME_ZONE,
                        )
                }
            }
        }
    }

    private fun showTimerRunningMessage() {
        editorState.update {
            it.copy(
                message = SettingsMessage.STOP_TIMER_BEFORE_TIME_ZONE_CHANGE,
            )
        }
    }

    private fun AppSettings.toUiState(
        effectiveZoneId: ZoneId,
        isTimerRunning: Boolean,
        googleConnection: GoogleSpreadsheetConnection,
        editor: SettingsEditorState,
    ): SettingsUiState =
        SettingsUiState(
            themeMode = themeMode,
            timeZoneMode = timeZoneMode,
            manualZoneId = manualZoneId,
            effectiveZoneId = effectiveZoneId,
            defaultExportDestination = defaultExportDestination,
            landscapeHandedness = landscapeHandedness,
            isTimerRunning = isTimerRunning,
            isSaving = editor.isSaving,
            isZoneSelectorVisible = editor.isZoneSelectorVisible,
            zoneSearchQuery = editor.zoneSearchQuery,
            zoneOptions =
                if (editor.isZoneSelectorVisible) {
                    zoneOptions(editor.zoneSearchQuery)
                } else {
                    emptyList()
                },
            message = editor.message,
            googleStatus =
                editor.googleOperationStatus
                    ?: editor.googleStatusOverride
                    ?: googleConnection.defaultUiStatus(),
            googleAccountId = googleConnection.accountId,
            googleAccountDisplayName = googleConnection.accountDisplayName,
            spreadsheetInput = editor.spreadsheetInput,
            connectedSpreadsheetId = googleConnection.spreadsheetId,
            connectedSpreadsheetTitle = googleConnection.spreadsheetTitle,
            googleMessage = editor.googleMessage,
        )

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val activeTimerRepository: ActiveTimerRepository,
        private val zoneIdProvider: EffectiveZoneIdProvider,
        private val googleConnectionRepository: GoogleConnectionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                activeTimerRepository = activeTimerRepository,
                zoneIdProvider = zoneIdProvider,
                googleConnectionRepository = googleConnectionRepository,
            ) as T
        }
    }

    private companion object {
        val allZoneOptions =
            GeographicalZoneIds.available().map { zoneId ->
                ZoneOptionUi(
                    zoneId = zoneId,
                    friendlyName =
                        zoneId.id
                            .substringAfterLast('/')
                            .replace('_', ' '),
                )
            }

        fun zoneOptions(query: String): List<ZoneOptionUi> {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) {
                return allZoneOptions
            }
            return allZoneOptions.filter { option ->
                option.friendlyName.contains(normalizedQuery, ignoreCase = true) ||
                    option.zoneId.id.contains(normalizedQuery, ignoreCase = true)
            }
        }

        fun GoogleSpreadsheetConnection.defaultUiStatus():
            GoogleConnectionUiStatus =
            when {
                isConnected -> GoogleConnectionUiStatus.CONNECTED
                hasAccountHint ->
                    if (hasSpreadsheetMetadata) {
                        GoogleConnectionUiStatus.AUTHORIZATION_EXPIRED
                    } else {
                        GoogleConnectionUiStatus.SIGNED_IN_NO_SPREADSHEET
                    }
                else -> GoogleConnectionUiStatus.SIGNED_OUT
            }

        fun GoogleConnectionFailure.failureStatus(): GoogleConnectionUiStatus =
            when (this) {
                GoogleConnectionFailure.AUTHORIZATION_REQUIRED,
                GoogleConnectionFailure.UNAUTHORIZED,
                -> GoogleConnectionUiStatus.AUTHORIZATION_EXPIRED
                GoogleConnectionFailure.OFFLINE,
                GoogleConnectionFailure.TIMEOUT,
                -> GoogleConnectionUiStatus.OFFLINE_ERROR
                else -> GoogleConnectionUiStatus.ERROR
            }

        fun GoogleConnectionFailure.toUiMessage(): GoogleSettingsMessage =
            when (this) {
                GoogleConnectionFailure.NO_CREDENTIAL ->
                    GoogleSettingsMessage.NO_CREDENTIAL
                GoogleConnectionFailure.CREDENTIAL_PROVIDER_UNAVAILABLE ->
                    GoogleSettingsMessage.CREDENTIAL_PROVIDER_UNAVAILABLE
                GoogleConnectionFailure.SIGN_IN_FAILED ->
                    GoogleSettingsMessage.SIGN_IN_FAILED
                GoogleConnectionFailure.AUTHORIZATION_REQUIRED,
                GoogleConnectionFailure.UNAUTHORIZED,
                -> GoogleSettingsMessage.AUTHORIZATION_REQUIRED
                GoogleConnectionFailure.PLAY_SERVICES_UNAVAILABLE ->
                    GoogleSettingsMessage.PLAY_SERVICES_UNAVAILABLE
                GoogleConnectionFailure.PICKER_RETURNED_DIFFERENT_FILE ->
                    GoogleSettingsMessage.PICKER_RETURNED_DIFFERENT_FILE
                GoogleConnectionFailure.NOT_FOUND_OR_NOT_GRANTED ->
                    GoogleSettingsMessage.NOT_FOUND_OR_NOT_GRANTED
                GoogleConnectionFailure.NOT_GOOGLE_SPREADSHEET ->
                    GoogleSettingsMessage.NOT_GOOGLE_SPREADSHEET
                GoogleConnectionFailure.READ_ONLY ->
                    GoogleSettingsMessage.READ_ONLY
                GoogleConnectionFailure.CONTENT_MODIFICATION_RESTRICTED ->
                    GoogleSettingsMessage.CONTENT_MODIFICATION_RESTRICTED
                GoogleConnectionFailure.OFFLINE ->
                    GoogleSettingsMessage.OFFLINE
                GoogleConnectionFailure.TIMEOUT ->
                    GoogleSettingsMessage.TIMEOUT
                GoogleConnectionFailure.WORKSPACE_POLICY_BLOCKED ->
                    GoogleSettingsMessage.WORKSPACE_POLICY_BLOCKED
                GoogleConnectionFailure.RATE_LIMITED ->
                    GoogleSettingsMessage.RATE_LIMITED
                GoogleConnectionFailure.SERVER_FAILURE ->
                    GoogleSettingsMessage.SERVER_FAILURE
                GoogleConnectionFailure.MALFORMED_RESPONSE ->
                    GoogleSettingsMessage.MALFORMED_RESPONSE
                GoogleConnectionFailure.LOCAL_STORAGE ->
                    GoogleSettingsMessage.LOCAL_STORAGE
            }
    }
}
