package worq.order.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import worq.order.R
import worq.order.data.MAX_TASK_DESCRIPTION_CODE_POINTS
import worq.order.data.MAX_TASK_NOTES_CODE_POINTS
import worq.order.data.MAX_TASK_PURCHASES_CODE_POINTS
import worq.order.data.TaskMetadataValidationError
import worq.order.ui.clients.ClientEditorDialog
import worq.order.ui.clients.RestoreArchivedClientDialog
import worq.order.ui.WorqOrderTextInputDefaults
import worq.order.ui.theme.WorqOrderDimens

object CreateTaskScreenTestTags {
    const val DESCRIPTION = "create_task_description"
    const val PURCHASES = "create_task_purchases"
    const val MILEAGE = "create_task_mileage"
    const val NOTES = "create_task_notes"
    const val FOOTER = "create_task_footer"
    const val CANCEL = "create_task_cancel"
    const val CREATE = "create_task_confirm"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    uiState: CreateTaskUiState,
    onEvent: (CreateTaskEvent) -> Unit,
) {
    val tagPicker = uiState.tagPicker
    BackHandler {
        if (tagPicker != null) {
            onEvent(CreateTaskEvent.DismissTagPicker)
        } else {
            onEvent(CreateTaskEvent.RequestClose)
        }
    }

    if (tagPicker == null) {
        val liveTextErrors = uiState.liveTextErrors()
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
        bottomBar = {
            CreateTaskActionFooter(
                uiState = uiState,
                onEvent = onEvent,
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
                        errorMessage =
                            uiState.message
                                ?.takeIf(CreateTaskMessage::isClientSelectorError)
                                ?.let { message -> stringResource(message.stringResource()) },
                    )
            }
            TextButton(
                onClick = { onEvent(CreateTaskEvent.OpenAddClient) },
                enabled = !uiState.isLoadingClients && !uiState.isSavingTask,
            ) {
                Text(stringResource(R.string.add_client_inline))
            }
            when {
                uiState.isLoadingConsultant -> CircularProgressIndicator()
                uiState.selectedConsultantId == null -> {
                    val consultantError =
                        uiState.message
                            ?.takeIf(CreateTaskMessage::isConsultantSelectorError)
                            ?.let { message -> stringResource(message.stringResource()) }
                            ?: stringResource(R.string.task_consultant_required)
                    OutlinedButton(
                        onClick = { onEvent(CreateTaskEvent.OpenConsultantSettings) },
                        enabled = !uiState.isSavingTask,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                                disabledContentColor = MaterialTheme.colorScheme.error,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.open_consultant_settings))
                    }
                    Text(
                        text = consultantError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics {
                                error(consultantError)
                                liveRegion = LiveRegionMode.Assertive
                            },
                    )
                }
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
                keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
                isError =
                    TaskMetadataValidationError.DESCRIPTION_REQUIRED in
                        uiState.metadataErrors ||
                        TaskMetadataValidationError.DESCRIPTION_TOO_LONG in
                        liveTextErrors,
                supportingText = {
                    TaskTextSupportingText(
                        value = composedTaskText(uiState.description, uiState.descriptionTagSelections),
                        maxCodePoints = MAX_TASK_DESCRIPTION_CODE_POINTS,
                        blankError =
                            TaskMetadataValidationError.DESCRIPTION_REQUIRED in
                                uiState.metadataErrors,
                    )
                },
            )
            TaskTagControls(
                selections = uiState.descriptionTagSelections,
                catalog = uiState.descriptionCatalogTags,
                enabled = !uiState.isSavingTask,
                onOpenPicker = { onEvent(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION)) },
                onUseUpdatedVersion = {
                    onEvent(CreateTaskEvent.UseUpdatedTagVersion(TaskTagField.DESCRIPTION, it))
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
                keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
                isError =
                    TaskMetadataValidationError.PURCHASES_TOO_LONG in
                        liveTextErrors,
                supportingText = {
                    TaskTextSupportingText(
                        value =
                            composedTaskText(
                                uiState.hardwareSoftwarePurchases,
                                uiState.purchaseTagSelections,
                            ),
                        maxCodePoints = MAX_TASK_PURCHASES_CODE_POINTS,
                    )
                },
            )
            TaskTagControls(
                selections = uiState.purchaseTagSelections,
                catalog = uiState.purchaseCatalogTags,
                enabled = !uiState.isSavingTask,
                modifier = Modifier.padding(bottom = WorqOrderDimens.SectionSpacing),
                onOpenPicker = {
                    onEvent(
                        CreateTaskEvent.OpenTagPicker(TaskTagField.HARDWARE_SOFTWARE_PURCHASES),
                    )
                },
                onUseUpdatedVersion = {
                    onEvent(
                        CreateTaskEvent.UseUpdatedTagVersion(
                            TaskTagField.HARDWARE_SOFTWARE_PURCHASES,
                            it,
                        ),
                    )
                },
            )
            TaskWorkTypeSelector(
                selected = uiState.workType,
                enabled = !uiState.isSavingTask,
                onSelect = { onEvent(CreateTaskEvent.SelectWorkType(it)) },
            )
            TaskBillingStatusSelector(
                selected = uiState.billingStatus,
                enabled = !uiState.isSavingTask,
                onSelect = { onEvent(CreateTaskEvent.SelectBillingStatus(it)) },
            )
            TaskMileageField(
                value = uiState.mileage,
                validationErrors = uiState.metadataErrors,
                enabled = !uiState.isSavingTask,
                onValueChange = { onEvent(CreateTaskEvent.EditMileage(it)) },
                modifier = Modifier.testTag(CreateTaskScreenTestTags.MILEAGE),
            )
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { onEvent(CreateTaskEvent.EditNotes(it)) },
                label = { Text(stringResource(R.string.notes)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CreateTaskScreenTestTags.NOTES),
                enabled = !uiState.isSavingTask,
                minLines = 2,
                maxLines = 6,
                keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
                isError = TaskMetadataValidationError.NOTES_TOO_LONG in liveTextErrors,
                supportingText = {
                    TaskTextSupportingText(
                        value = uiState.notes,
                        maxCodePoints = MAX_TASK_NOTES_CODE_POINTS,
                    )
                },
            )
            uiState.message?.takeUnless(CreateTaskMessage::isSelectorError)?.let { message ->
                Text(
                    text = stringResource(message.stringResource()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        }
    } else {
        TaskTagPickerOverlay(
            picker = tagPicker,
            catalog =
                if (tagPicker.field == TaskTagField.DESCRIPTION) {
                    uiState.descriptionCatalogTags
                } else {
                    uiState.purchaseCatalogTags
                },
            onSearchChanged = { onEvent(CreateTaskEvent.EditTagSearch(it)) },
            onClearSearch = { onEvent(CreateTaskEvent.ClearTagSearch) },
            onToggle = { onEvent(CreateTaskEvent.ToggleTagPickerItem(it)) },
            onSelectAll = { onEvent(CreateTaskEvent.SelectAllVisibleTagPickerItems) },
            onDeselectAll = { onEvent(CreateTaskEvent.DeselectAllVisibleTagPickerItems) },
            onCancel = { onEvent(CreateTaskEvent.DismissTagPicker) },
            onApply = { onEvent(CreateTaskEvent.ApplyTagPicker) },
            onCreateInline = { onEvent(CreateTaskEvent.OpenInlineTagCreate) },
        )
    }

    uiState.tagInlineEditor?.let { editor ->
        TaskTagInlineEditorDialog(
            editor = editor,
            onTextChanged = { onEvent(CreateTaskEvent.EditInlineTagText(it)) },
            onConfirm = { onEvent(CreateTaskEvent.ConfirmInlineTagCreate) },
            onDismiss = { onEvent(CreateTaskEvent.DismissInlineTagCreate) },
        )
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

@Composable
private fun CreateTaskActionFooter(
    uiState: CreateTaskUiState,
    onEvent: (CreateTaskEvent) -> Unit,
) {
    BottomAppBar(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentPadding =
            PaddingValues(
                horizontal = WorqOrderDimens.ScreenPadding,
                vertical = WorqOrderDimens.BottomActionVerticalPadding,
            ),
        windowInsets = BottomAppBarDefaults.windowInsets,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(CreateTaskScreenTestTags.FOOTER),
            horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            OutlinedButton(
                onClick = { onEvent(CreateTaskEvent.RequestClose) },
                enabled = !uiState.isSavingTask,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = WorqOrderDimens.ActionButtonHeight)
                        .testTag(CreateTaskScreenTestTags.CANCEL),
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
                        uiState.selectedConsultantId != null &&
                        uiState.liveTextErrors().isEmpty(),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = WorqOrderDimens.ActionButtonHeight)
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

private fun CreateTaskUiState.liveTextErrors(): Set<TaskMetadataValidationError> =
    projectedTagTextErrors(
        description = description,
        descriptionSelections = descriptionTagSelections,
        purchases = hardwareSoftwarePurchases,
        purchaseSelections = purchaseTagSelections,
        notes = notes,
    )

private fun CreateTaskMessage.stringResource(): Int =
    when (this) {
        CreateTaskMessage.DATA_UNAVAILABLE -> R.string.data_unavailable
        CreateTaskMessage.CLIENT_NOT_FOUND -> R.string.client_not_found
        CreateTaskMessage.RESTORE_NAME_CONFLICT -> R.string.restore_client_conflict
        CreateTaskMessage.CLIENT_REQUIRED -> R.string.task_client_required
        CreateTaskMessage.CLIENT_ARCHIVED -> R.string.task_client_archived_during_edit
        CreateTaskMessage.CONSULTANT_REQUIRED -> R.string.task_consultant_required
        CreateTaskMessage.CONSULTANT_ARCHIVED -> R.string.consultant_archived_during_selection
        CreateTaskMessage.TAG_DATA_UNAVAILABLE -> R.string.tag_picker_data_unavailable
    }

private fun CreateTaskMessage.isClientSelectorError(): Boolean =
    this == CreateTaskMessage.CLIENT_REQUIRED ||
        this == CreateTaskMessage.CLIENT_NOT_FOUND ||
        this == CreateTaskMessage.CLIENT_ARCHIVED

private fun CreateTaskMessage.isConsultantSelectorError(): Boolean =
    this == CreateTaskMessage.CONSULTANT_REQUIRED ||
        this == CreateTaskMessage.CONSULTANT_ARCHIVED

private fun CreateTaskMessage.isSelectorError(): Boolean =
    isClientSelectorError() || isConsultantSelectorError()
