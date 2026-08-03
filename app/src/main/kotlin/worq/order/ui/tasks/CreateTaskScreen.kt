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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import worq.order.ui.clients.ClientEditorDialog
import worq.order.ui.clients.RestoreArchivedClientDialog
import worq.order.ui.theme.WorqOrderDimens

object CreateTaskScreenTestTags {
    const val DESCRIPTION = "create_task_description"
    const val PURCHASES = "create_task_purchases"
    const val CREATE = "create_task_confirm"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    uiState: CreateTaskUiState,
    onEvent: (CreateTaskEvent) -> Unit,
) {
    BackHandler { onEvent(CreateTaskEvent.RequestClose) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_task)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(CreateTaskEvent.RequestClose) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(WorqOrderDimens.ScreenPadding),
            verticalArrangement =
                Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.task_for_date,
                        DateTimeFormatter
                            .ofLocalizedDate(FormatStyle.MEDIUM)
                            .format(uiState.workDate),
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.consultant),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                uiState.isLoadingConsultant -> CircularProgressIndicator()
                uiState.selectedConsultantName != null ->
                    Text(
                        stringResource(
                            R.string.selected_consultant,
                            uiState.selectedConsultantName,
                        ),
                    )
                else -> {
                    Text(
                        text = stringResource(R.string.task_consultant_required),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    OutlinedButton(
                        onClick = { onEvent(CreateTaskEvent.OpenConsultantSettings) },
                        enabled = !uiState.isSavingTask,
                    ) {
                        Text(stringResource(R.string.open_consultant_settings))
                    }
                }
            }
            Text(
                text = stringResource(R.string.task_client),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                uiState.isLoadingClients ->
                    CircularProgressIndicator()
                uiState.hasClientLoadError ->
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                    ) {
                        Text(stringResource(R.string.clients_load_failed))
                        Button(
                            onClick = { onEvent(CreateTaskEvent.RetryClients) },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                uiState.activeClients.isEmpty() ->
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                    ) {
                        Text(
                            text = stringResource(R.string.no_active_clients),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.no_active_clients_supporting),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    TaskClientSelector(
                        activeClients = uiState.activeClients,
                        selectedClientName = uiState.selectedClient?.name,
                        expanded = uiState.isClientMenuExpanded,
                        enabled = !uiState.isSavingTask,
                        onOpen = { onEvent(CreateTaskEvent.OpenClientMenu) },
                        onDismiss = { onEvent(CreateTaskEvent.DismissClientMenu) },
                        onSelect = { onEvent(CreateTaskEvent.SelectClient(it)) },
                    )
            }
            TextButton(
                onClick = { onEvent(CreateTaskEvent.OpenAddClient) },
                enabled = !uiState.isLoadingClients && !uiState.isSavingTask,
            ) {
                Text(stringResource(R.string.add_client_inline))
            }
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { onEvent(CreateTaskEvent.EditDescription(it)) },
                label = { Text(stringResource(R.string.task_description)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CreateTaskScreenTestTags.DESCRIPTION),
                enabled = !uiState.isSavingTask,
                minLines = 2,
                maxLines = 5,
                isError =
                    TaskMetadataValidationError.DESCRIPTION_REQUIRED in
                        uiState.metadataErrors ||
                        TaskMetadataValidationError.DESCRIPTION_TOO_LONG in
                        uiState.metadataErrors,
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
                    onEvent(CreateTaskEvent.EditHardwareSoftwarePurchases(it))
                },
                label = {
                    Text(stringResource(R.string.hardware_software_purchases))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CreateTaskScreenTestTags.PURCHASES),
                enabled = !uiState.isSavingTask,
                minLines = 2,
                maxLines = 6,
                isError =
                    TaskMetadataValidationError.PURCHASES_TOO_LONG in
                        uiState.metadataErrors,
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
            uiState.message?.let { message ->
                Text(
                    text = stringResource(message.stringResource()),
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                        },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                OutlinedButton(
                    onClick = { onEvent(CreateTaskEvent.RequestClose) },
                    enabled = !uiState.isSavingTask,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onEvent(CreateTaskEvent.CreateTask) },
                    enabled =
                        !uiState.isSavingTask &&
                            !uiState.isLoadingClients &&
                            !uiState.isLoadingConsultant &&
                            !uiState.hasClientLoadError &&
                            !uiState.hasConsultantLoadError &&
                            uiState.activeClients.isNotEmpty() &&
                            uiState.selectedConsultantId != null,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(CreateTaskScreenTestTags.CREATE),
                ) {
                    if (uiState.isSavingTask) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.create))
                    }
                }
            }
        }
    }

    uiState.addClientEditor?.let { editor ->
        ClientEditorDialog(
            editor = editor,
            onNameChanged = { onEvent(CreateTaskEvent.EditNewClientName(it)) },
            onConfirm = { onEvent(CreateTaskEvent.ConfirmAddClient) },
            onDismiss = { onEvent(CreateTaskEvent.DismissAddClient) },
        )
    }
    uiState.restoreOffer?.let { offer ->
        RestoreArchivedClientDialog(
            offer = offer,
            onConfirm = { onEvent(CreateTaskEvent.ConfirmRestoreOffer) },
            onDismiss = { onEvent(CreateTaskEvent.DismissRestoreOffer) },
        )
    }
    if (uiState.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { onEvent(CreateTaskEvent.DismissDiscard) },
            title = { Text(stringResource(R.string.discard_task_changes_title)) },
            text = { Text(stringResource(R.string.discard_task_changes_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(CreateTaskEvent.ConfirmDiscard) }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CreateTaskEvent.DismissDiscard) }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }
}

private fun CreateTaskMessage.stringResource(): Int =
    when (this) {
        CreateTaskMessage.DATA_UNAVAILABLE -> R.string.data_unavailable
        CreateTaskMessage.CLIENT_NOT_FOUND -> R.string.client_not_found
        CreateTaskMessage.RESTORE_NAME_CONFLICT -> R.string.restore_client_conflict
        CreateTaskMessage.CLIENT_REQUIRED -> R.string.task_client_required
        CreateTaskMessage.CLIENT_ARCHIVED -> R.string.task_client_archived_during_edit
        CreateTaskMessage.CONSULTANT_REQUIRED -> R.string.task_consultant_required
        CreateTaskMessage.CONSULTANT_ARCHIVED -> R.string.consultant_archived_during_selection
    }
