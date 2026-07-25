package worq.order.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import worq.order.data.ActiveTimerRepository
import worq.order.data.AppSettings
import worq.order.data.ExportDestination
import worq.order.data.SettingsRepository
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.TimeZoneSettingResult
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.GeographicalZoneIds

private data class SettingsEditorState(
    val isSaving: Boolean = false,
    val isZoneSelectorVisible: Boolean = false,
    val zoneSearchQuery: String = "",
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val zoneIdProvider: EffectiveZoneIdProvider,
) : ViewModel() {
    private val editorState = MutableStateFlow(SettingsEditorState())

    val uiState =
        combine(
            settingsRepository.observeSettings(),
            activeTimerRepository.observeActiveTimer(),
            zoneIdProvider.observeZoneId(),
            editorState,
        ) { settings, activeTimer, effectiveZoneId, editor ->
            settings.toUiState(
                effectiveZoneId = effectiveZoneId,
                isTimerRunning = activeTimer != null,
                editor = editor,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue =
                AppSettings().toUiState(
                    effectiveZoneId = zoneIdProvider.zoneId(),
                    isTimerRunning = false,
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
            SettingsEvent.DismissMessage ->
                editorState.update { it.copy(message = null) }
        }
    }

    private fun setTheme(themeMode: ThemeMode) {
        launchWrite {
            settingsRepository.setThemeMode(themeMode)
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
        editor: SettingsEditorState,
    ): SettingsUiState =
        SettingsUiState(
            themeMode = themeMode,
            timeZoneMode = timeZoneMode,
            manualZoneId = manualZoneId,
            effectiveZoneId = effectiveZoneId,
            defaultExportDestination = defaultExportDestination,
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
        )

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val activeTimerRepository: ActiveTimerRepository,
        private val zoneIdProvider: EffectiveZoneIdProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                activeTimerRepository = activeTimerRepository,
                zoneIdProvider = zoneIdProvider,
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
    }
}
