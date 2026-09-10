package worq.order.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import worq.order.R
import worq.order.data.MAX_TASK_DESCRIPTION_CODE_POINTS
import worq.order.data.MAX_TASK_PURCHASES_CODE_POINTS
import worq.order.data.TaskMetadataValidationError
import worq.order.domain.ManualIntervalValidationError
import worq.order.domain.OverlapOffsetChoice
import worq.order.ui.WorqOrderTextInputDefaults
import worq.order.ui.theme.WorqOrderDimens
import worq.order.util.ClockTimeFormatter

object EditTaskScreenTestTags {
    const val CONSULTANT = "edit_task_consultant"
    const val MILEAGE = "edit_task_mileage"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(
    uiState: EditTaskUiState,
    onEvent: (EditTaskEvent) -> Unit,
) {
    BackHandler { onEvent(EditTaskEvent.RequestClose) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_task)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(EditTaskEvent.RequestClose) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        when {
            uiState.isLoading ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            uiState.hasLoadError ->
                TaskEditUnavailable(
                    message = stringResource(R.string.task_load_failed),
                    onRetry = { onEvent(EditTaskEvent.Retry) },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            uiState.taskMissing ->
                TaskEditUnavailable(
                    message = stringResource(R.string.task_no_longer_exists),
                    onRetry = null,
                    modifier = Modifier.padding(scaffoldPadding),
                )
            else ->
                EditTaskContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    modifier = Modifier.padding(scaffoldPadding),
                )
        }
    }

    EditTaskDialogs(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun EditTaskContent(
    uiState: EditTaskUiState,
    onEvent: (EditTaskEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedClientActive =
        uiState.activeClients.any { it.id == uiState.selectedClientId }
    val selectedConsultantActive =
        uiState.activeConsultants.any { it.id == uiState.selectedConsultantId }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WorqOrderDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
    ) {
        uiState.workDate?.let { date ->
            Text(
                text =
                    stringResource(
                        R.string.task_for_date,
                        DateTimeFormatter
                            .ofLocalizedDate(FormatStyle.MEDIUM)
                            .format(date),
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        uiState.zoneId?.let { zone ->
            Text(
                text = stringResource(R.string.task_time_zone, zone.id),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.isRunning) {
            Text(
                text = stringResource(R.string.running_task_edit_blocked),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.task_client),
            style = MaterialTheme.typography.titleMedium,
        )
        if (uiState.activeClients.isEmpty()) {
            Text(
                text = stringResource(R.string.no_active_clients_for_edit),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            TaskClientSelector(
                activeClients = uiState.activeClients,
                selectedClientName = uiState.selectedClientName,
                expanded = uiState.isClientMenuExpanded,
                enabled = !uiState.isRunning && !uiState.isSavingMetadata,
                onOpen = { onEvent(EditTaskEvent.OpenClientMenu) },
                onDismiss = { onEvent(EditTaskEvent.DismissClientMenu) },
                onSelect = { onEvent(EditTaskEvent.SelectClient(it)) },
            )
        }
        if (!selectedClientActive) {
            Text(
                text = stringResource(R.string.task_client_archived_during_edit),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.consultant),
            style = MaterialTheme.typography.titleMedium,
        )
        if (uiState.activeConsultants.isEmpty()) {
            Text(
                text = stringResource(R.string.no_active_consultants_supporting),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            TaskConsultantSelector(
                activeConsultants = uiState.activeConsultants,
                selectedConsultantName = uiState.selectedConsultantName,
                expanded = uiState.isConsultantMenuExpanded,
                enabled = !uiState.isRunning && !uiState.isSavingMetadata,
                onOpen = { onEvent(EditTaskEvent.OpenConsultantMenu) },
                onDismiss = { onEvent(EditTaskEvent.DismissConsultantMenu) },
                onSelect = { onEvent(EditTaskEvent.SelectConsultant(it)) },
            )
        }
        if (!selectedConsultantActive) {
            Text(
                text = stringResource(R.string.task_consultant_unavailable),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(EditTaskScreenTestTags.CONSULTANT),
            )
        }
        OutlinedTextField(
            value = uiState.description,
            onValueChange = { onEvent(EditTaskEvent.EditDescription(it)) },
            label = { Text(stringResource(R.string.task_description)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isRunning && !uiState.isSavingMetadata,
            minLines = 2,
            maxLines = 5,
            keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
            isError =
                TaskMetadataValidationError.DESCRIPTION_REQUIRED in uiState.metadataErrors ||
                    TaskMetadataValidationError.DESCRIPTION_TOO_LONG in uiState.metadataErrors,
            supportingText = {
                TaskTextSupportingText(
                    value = uiState.description,
                    maxCodePoints = MAX_TASK_DESCRIPTION_CODE_POINTS,
                    blankError =
                        TaskMetadataValidationError.DESCRIPTION_REQUIRED in
                            uiState.metadataErrors,
                    tooLongError =
                        TaskMetadataValidationError.DESCRIPTION_TOO_LONG in
                            uiState.metadataErrors,
                )
            },
        )
        OutlinedTextField(
            value = uiState.hardwareSoftwarePurchases,
            onValueChange = {
                onEvent(EditTaskEvent.EditHardwareSoftwarePurchases(it))
            },
            label = { Text(stringResource(R.string.hardware_software_purchases)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isRunning && !uiState.isSavingMetadata,
            minLines = 2,
            maxLines = 6,
            keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
            isError =
                TaskMetadataValidationError.PURCHASES_TOO_LONG in uiState.metadataErrors,
            supportingText = {
                TaskTextSupportingText(
                    value = uiState.hardwareSoftwarePurchases,
                    maxCodePoints = MAX_TASK_PURCHASES_CODE_POINTS,
                    tooLongError =
                        TaskMetadataValidationError.PURCHASES_TOO_LONG in
                            uiState.metadataErrors,
                )
            },
        )
        TaskWorkTypeSelector(
            selected = uiState.workType,
            enabled = !uiState.isRunning && !uiState.isSavingMetadata,
            onSelect = { onEvent(EditTaskEvent.SelectWorkType(it)) },
        )
        TaskBillingStatusSelector(
            selected = uiState.billingStatus,
            enabled = !uiState.isRunning && !uiState.isSavingMetadata,
            onSelect = { onEvent(EditTaskEvent.SelectBillingStatus(it)) },
        )
        TaskMileageField(
            value = uiState.mileage,
            validationErrors = uiState.metadataErrors,
            enabled = !uiState.isRunning && !uiState.isSavingMetadata,
            onValueChange = { onEvent(EditTaskEvent.EditMileage(it)) },
            modifier = Modifier.testTag(EditTaskScreenTestTags.MILEAGE),
        )
        Button(
            onClick = { onEvent(EditTaskEvent.SaveMetadata) },
            enabled =
                !uiState.isRunning &&
                    !uiState.isSavingMetadata &&
                    selectedClientActive &&
                    selectedConsultantActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_task_changes))
        }
        uiState.message?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(message.stringResource()),
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics {
                                liveRegion = LiveRegionMode.Assertive
                            },
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { onEvent(EditTaskEvent.DismissMessage) }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        Text(
            text = stringResource(R.string.task_total_duration, uiState.totalDuration),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.billing_minutes, uiState.billingMinutes),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.interval),
                style = MaterialTheme.typography.titleLarge,
            )
            if (uiState.interval == null) {
                Button(
                    onClick = { onEvent(EditTaskEvent.OpenAddInterval) },
                    enabled = !uiState.isRunning,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.add_interval))
                }
            }
        }
        val interval = uiState.interval
        if (interval == null) {
            Text(
                text = stringResource(R.string.no_interval),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            IntervalCard(
                interval = interval,
                enabled = !uiState.isRunning && !interval.isRunning,
                onEdit = {
                    onEvent(EditTaskEvent.OpenEditInterval(interval.id))
                },
                onDelete = {
                    onEvent(EditTaskEvent.RequestDeleteInterval(interval.id))
                },
            )
        }
        OutlinedButton(
            onClick = { onEvent(EditTaskEvent.RequestDeleteTask) },
            enabled = !uiState.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
            )
            Text(stringResource(R.string.delete_task))
        }
    }
}

@Composable
private fun IntervalCard(
    interval: IntervalItemUi,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(WorqOrderDimens.ItemPadding),
            verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            Text(stringResource(R.string.interval_start, interval.startText))
            Text(
                stringResource(
                    R.string.interval_stop,
                    if (interval.isRunning) {
                        stringResource(R.string.running)
                    } else {
                        interval.stopText
                    },
                ),
            )
            if (interval.isRunning) {
                Text(
                    text = stringResource(R.string.running),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                ) {
                    TextButton(onClick = onEdit, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.edit_interval))
                    }
                    TextButton(onClick = onDelete, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.delete_interval))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskEditUnavailable(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(WorqOrderDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message)
        onRetry?.let {
            Button(onClick = it) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTaskDialogs(
    uiState: EditTaskUiState,
    onEvent: (EditTaskEvent) -> Unit,
) {
    val editor = uiState.intervalEditor
    if (editor != null) {
        IntervalEditorDialog(editor = editor, onEvent = onEvent)
        val endpoint = editor.timePickerEndpoint
        if (endpoint != null) {
            val initial =
                when (endpoint) {
                    IntervalEndpoint.START -> editor.startLocal.toLocalTime()
                    IntervalEndpoint.STOP -> editor.stopLocal.toLocalTime()
                }
            val pickerState =
                rememberTimePickerState(
                    initialHour = initial.hour,
                    initialMinute = initial.minute,
                    is24Hour = false,
                )
            AlertDialog(
                onDismissRequest = { onEvent(EditTaskEvent.DismissTimePicker) },
                title = {
                    Text(
                        stringResource(
                            if (endpoint == IntervalEndpoint.START) {
                                R.string.choose_start_time
                            } else {
                                R.string.choose_stop_time
                            },
                        ),
                    )
                },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onEvent(
                                EditTaskEvent.SetEditorTime(
                                    endpoint = endpoint,
                                    hour = pickerState.hour,
                                    minute = pickerState.minute,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onEvent(EditTaskEvent.DismissTimePicker) },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
    if (uiState.showDiscardConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.discard_task_changes_title),
            message = stringResource(R.string.discard_task_changes_message),
            confirmLabel = stringResource(R.string.discard),
            onConfirm = { onEvent(EditTaskEvent.ConfirmDiscard) },
            onDismiss = { onEvent(EditTaskEvent.DismissDiscard) },
        )
    }
    if (uiState.showTaskDeleteConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.delete_task_title),
            message = stringResource(R.string.delete_task_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { onEvent(EditTaskEvent.ConfirmDeleteTask) },
            onDismiss = { onEvent(EditTaskEvent.DismissDeleteTask) },
        )
    }
    uiState.intervalPendingDeletion?.let { interval ->
        ConfirmDialog(
            title = stringResource(R.string.delete_interval_title),
            message = stringResource(R.string.delete_interval_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { onEvent(EditTaskEvent.ConfirmDeleteInterval) },
            onDismiss = { onEvent(EditTaskEvent.DismissDeleteInterval) },
        )
    }
}

@Composable
private fun IntervalEditorDialog(
    editor: IntervalEditorUiState,
    onEvent: (EditTaskEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(EditTaskEvent.DismissIntervalEditor) },
        title = {
            Text(
                stringResource(
                    if (editor.intervalId == null) {
                        R.string.add_interval_title
                    } else {
                        R.string.edit_interval_title
                    },
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                TextButton(
                    onClick = {
                        onEvent(EditTaskEvent.OpenTimePicker(IntervalEndpoint.START))
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.interval_start,
                            ClockTimeFormatter.format(editor.startLocal),
                        ),
                    )
                }
                OffsetChoices(
                    label = stringResource(R.string.start_time_occurrence),
                    choices = editor.startOffsetChoices,
                    selected = editor.startOverlapChoice,
                    onSelect = {
                        onEvent(
                            EditTaskEvent.SelectOffset(
                                IntervalEndpoint.START,
                                it,
                            ),
                        )
                    },
                )
                TextButton(
                    onClick = {
                        onEvent(EditTaskEvent.OpenTimePicker(IntervalEndpoint.STOP))
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.interval_stop,
                            ClockTimeFormatter.format(editor.stopLocal),
                        ),
                    )
                }
                OffsetChoices(
                    label = stringResource(R.string.stop_time_occurrence),
                    choices = editor.stopOffsetChoices,
                    selected = editor.stopOverlapChoice,
                    onSelect = {
                        onEvent(
                            EditTaskEvent.SelectOffset(
                                IntervalEndpoint.STOP,
                                it,
                            ),
                        )
                    },
                )
                editor.validationErrors.forEach { error ->
                    Text(
                        text = stringResource(error.stringResource()),
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(EditTaskEvent.SaveInterval) },
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(EditTaskEvent.DismissIntervalEditor) },
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun OffsetChoices(
    label: String,
    choices: List<OffsetChoiceUi>,
    selected: OverlapOffsetChoice?,
    onSelect: (OverlapOffsetChoice) -> Unit,
) {
    if (choices.isEmpty()) {
        return
    }
    Text(label, style = MaterialTheme.typography.labelLarge)
    choices.forEach { option ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected == option.choice,
                onClick = { onSelect(option.choice) },
            )
            Text(
                stringResource(
                    if (option.choice == OverlapOffsetChoice.EARLIER_OFFSET) {
                        R.string.earlier_occurrence
                    } else {
                        R.string.later_occurrence
                    },
                    option.offsetLabel,
                ),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun EditTaskMessage.stringResource(): Int =
    when (this) {
        EditTaskMessage.DATA_UNAVAILABLE -> R.string.data_unavailable
        EditTaskMessage.TASK_NOT_FOUND -> R.string.task_no_longer_exists
        EditTaskMessage.CLIENT_UNAVAILABLE -> R.string.task_client_archived_during_edit
        EditTaskMessage.CONSULTANT_UNAVAILABLE -> R.string.task_consultant_unavailable
        EditTaskMessage.RUNNING_TASK -> R.string.running_task_edit_blocked
        EditTaskMessage.TASK_ALREADY_HAS_INTERVAL -> R.string.task_already_has_interval
        EditTaskMessage.INTERVAL_NOT_FOUND -> R.string.interval_no_longer_exists
        EditTaskMessage.RUNNING_INTERVAL -> R.string.running_interval_edit_blocked
        EditTaskMessage.INTERVAL_CHANGED -> R.string.interval_changed_retry
    }

private fun ManualIntervalValidationError.stringResource(): Int =
    when (this) {
        ManualIntervalValidationError.START_IN_DST_GAP ->
            R.string.start_time_dst_gap
        ManualIntervalValidationError.STOP_IN_DST_GAP ->
            R.string.stop_time_dst_gap
        ManualIntervalValidationError.START_DST_OVERLAP_REQUIRES_CHOICE ->
            R.string.start_time_choose_occurrence
        ManualIntervalValidationError.STOP_DST_OVERLAP_REQUIRES_CHOICE ->
            R.string.stop_time_choose_occurrence
        ManualIntervalValidationError.MANUAL_INTERVAL_MUST_BE_CLOSED ->
            R.string.manual_interval_must_be_closed
        ManualIntervalValidationError.RUNNING_INTERVAL_CANNOT_BE_EDITED ->
            R.string.running_interval_edit_blocked
        ManualIntervalValidationError.START_MUST_PRECEDE_STOP ->
            R.string.start_before_stop
        ManualIntervalValidationError.OUTSIDE_TASK_DATE ->
            R.string.interval_outside_task_date
        ManualIntervalValidationError.OVERLAPS_EXISTING_INTERVAL ->
            R.string.interval_overlap
        ManualIntervalValidationError.OVERLAPS_OPEN_INTERVAL ->
            R.string.interval_overlap_running
        ManualIntervalValidationError.WOULD_CREATE_SECOND_OPEN_INTERVAL ->
            R.string.manual_interval_must_be_closed
    }
