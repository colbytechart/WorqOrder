package worq.order.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import worq.order.R
import worq.order.data.ExportDestination
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.ui.theme.WorqOrderDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenClientManagement: () -> Unit,
    showGoogleSetupRequired: Boolean = false,
) {
    if (uiState.isZoneSelectorVisible) {
        ZoneSelectorDialog(
            query = uiState.zoneSearchQuery,
            zones = uiState.zoneOptions,
            onQueryChanged = { onEvent(SettingsEvent.EditZoneSearch(it)) },
            onSelect = { onEvent(SettingsEvent.SelectManualZone(it.zoneId)) },
            onDismiss = { onEvent(SettingsEvent.DismissZoneSelector) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.padding(scaffoldPadding),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = WorqOrderDimens.ScreenPadding,
                    vertical = WorqOrderDimens.SectionSpacing,
                ),
            verticalArrangement =
                Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
        ) {
            item {
                ListItem(
                    modifier =
                        Modifier.clickable(onClick = onOpenClientManagement),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.client_management))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.client_management_summary))
                    },
                    trailingContent = {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
            uiState.message?.let { message ->
                item {
                    SettingsMessageCard(
                        message = message,
                        onDismiss = { onEvent(SettingsEvent.DismissMessage) },
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.appearance)) {
                    SettingsChoiceRow(
                        title = stringResource(R.string.system_theme),
                        supportingText =
                            stringResource(R.string.system_theme_summary),
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        enabled = !uiState.isSaving,
                        onClick = {
                            onEvent(SettingsEvent.SelectTheme(ThemeMode.SYSTEM))
                        },
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.light_theme),
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        enabled = !uiState.isSaving,
                        onClick = {
                            onEvent(SettingsEvent.SelectTheme(ThemeMode.LIGHT))
                        },
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.dark_theme),
                        selected = uiState.themeMode == ThemeMode.DARK,
                        enabled = !uiState.isSaving,
                        onClick = {
                            onEvent(SettingsEvent.SelectTheme(ThemeMode.DARK))
                        },
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.time_zone)) {
                    Text(
                        text =
                            stringResource(
                                R.string.effective_time_zone,
                                uiState.effectiveZoneId.id,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(WorqOrderDimens.ItemPadding),
                    )
                    if (uiState.isTimerRunning) {
                        Text(
                            text =
                                stringResource(
                                    R.string.stop_timer_before_time_zone_change,
                                ),
                            color = MaterialTheme.colorScheme.error,
                            modifier =
                                Modifier.padding(
                                    horizontal = WorqOrderDimens.ItemPadding,
                                ),
                        )
                    }
                    SettingsChoiceRow(
                        title = stringResource(R.string.use_device_time_zone),
                        supportingText =
                            stringResource(R.string.use_device_time_zone_summary),
                        selected = uiState.timeZoneMode == TimeZoneMode.DEVICE,
                        enabled = !uiState.isSaving && !uiState.isTimerRunning,
                        onClick = {
                            onEvent(SettingsEvent.SelectDeviceTimeZone)
                        },
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.use_manual_time_zone),
                        supportingText =
                            uiState.manualZoneId?.id
                                ?: stringResource(R.string.no_manual_time_zone_selected),
                        selected = uiState.timeZoneMode == TimeZoneMode.MANUAL,
                        enabled = !uiState.isSaving && !uiState.isTimerRunning,
                        onClick = {
                            onEvent(SettingsEvent.SelectManualTimeZone)
                        },
                    )
                    OutlinedButton(
                        onClick = { onEvent(SettingsEvent.OpenZoneSelector) },
                        enabled = !uiState.isSaving && !uiState.isTimerRunning,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(WorqOrderDimens.ItemPadding),
                    ) {
                        Text(stringResource(R.string.choose_time_zone))
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.export_default)) {
                    SettingsChoiceRow(
                        title = stringResource(R.string.csv),
                        selected =
                            uiState.defaultExportDestination ==
                                ExportDestination.CSV,
                        enabled = !uiState.isSaving,
                        onClick = {
                            onEvent(
                                SettingsEvent.SelectExportDestination(
                                    ExportDestination.CSV,
                                ),
                            )
                        },
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.google_sheets),
                        supportingText =
                            stringResource(R.string.google_sheets_not_connected),
                        selected =
                            uiState.defaultExportDestination ==
                                ExportDestination.GOOGLE_SHEETS,
                        enabled = !uiState.isSaving,
                        onClick = {
                            onEvent(
                                SettingsEvent.SelectExportDestination(
                                    ExportDestination.GOOGLE_SHEETS,
                                ),
                            )
                        },
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        if (showGoogleSetupRequired) {
                            androidx.compose.material3.CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            androidx.compose.material3.CardDefaults.cardColors()
                        },
                ) {
                    Column(
                        modifier = Modifier.padding(WorqOrderDimens.CardPadding),
                        verticalArrangement =
                            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                    ) {
                        Text(
                            text =
                                if (showGoogleSetupRequired) {
                                    stringResource(R.string.google_sheets_setup_required)
                                } else {
                                    stringResource(R.string.google_sheets_connection)
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.google_sheets_connection_unavailable,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(WorqOrderDimens.CardPadding),
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = WorqOrderDimens.IconButtonSize)
                .clickable(
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    this.selected = selected
                }.padding(horizontal = WorqOrderDimens.ItemPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = WorqOrderDimens.ItemPadding),
        ) {
            Text(
                text = title,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ZoneSelectorDialog(
    query: String,
    zones: List<ZoneOptionUi>,
    onQueryChanged: (String) -> Unit,
    onSelect: (ZoneOptionUi) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time_zone)) },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(R.string.search_time_zones)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (zones.isEmpty()) {
                    Text(stringResource(R.string.no_time_zones_found))
                } else {
                    LazyColumn(
                        modifier =
                            Modifier.heightIn(
                                max = WorqOrderDimens.ZoneSelectorMaxHeight,
                            ),
                    ) {
                        items(
                            items = zones,
                            key = { it.zoneId.id },
                        ) { zone ->
                            ListItem(
                                modifier =
                                    Modifier.clickable { onSelect(zone) },
                                headlineContent = { Text(zone.friendlyName) },
                                supportingContent = { Text(zone.zoneId.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsMessageCard(
    message: SettingsMessage,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(WorqOrderDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            Text(
                text =
                    stringResource(
                        when (message) {
                            SettingsMessage.STOP_TIMER_BEFORE_TIME_ZONE_CHANGE ->
                                R.string.stop_timer_before_time_zone_change
                            SettingsMessage.INVALID_TIME_ZONE ->
                                R.string.invalid_time_zone
                            SettingsMessage.DATA_UNAVAILABLE ->
                                R.string.settings_data_unavailable
                        },
                    ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
