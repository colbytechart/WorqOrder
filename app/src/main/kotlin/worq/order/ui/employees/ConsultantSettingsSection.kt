package worq.order.ui.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import worq.order.R
import worq.order.ui.WorqOrderTextInputDefaults
import worq.order.ui.theme.WorqOrderDimens

@Composable
fun ConsultantSelectionSection(
    uiState: ConsultantSettingsUiState,
    onEvent: (ConsultantSettingsEvent) -> Unit,
) {
    when {
        uiState.isLoading ->
            CircularProgressIndicator(
                modifier = Modifier.padding(WorqOrderDimens.CardPadding),
            )
        uiState.hasLoadError ->
            ConsultantLoadError(
                onRetry = { onEvent(ConsultantSettingsEvent.Retry) },
            )
        else ->
            ConsultantSelection(
                uiState = uiState,
                onEvent = onEvent,
            )
    }
}

@Composable
private fun ConsultantSelection(
    uiState: ConsultantSettingsUiState,
    onEvent: (ConsultantSettingsEvent) -> Unit,
) {
    Column {
        uiState.message?.let { message ->
            ConsultantMessage(
                message = message,
                onDismiss = { onEvent(ConsultantSettingsEvent.DismissMessage) },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WorqOrderDimens.ItemPadding),
        ) {
            OutlinedButton(
                onClick = { onEvent(ConsultantSettingsEvent.OpenSelectionMenu) },
                enabled =
                    uiState.activeConsultants.isNotEmpty() &&
                        !uiState.isSavingSelection &&
                        uiState.pendingConsultantId == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        uiState.selectedConsultant?.name
                            ?: stringResource(R.string.choose_consultant),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = uiState.isSelectionMenuExpanded,
                onDismissRequest = {
                    onEvent(ConsultantSettingsEvent.DismissSelectionMenu)
                },
            ) {
                uiState.activeConsultants.forEach { consultant ->
                    DropdownMenuItem(
                        text = { Text(consultant.name) },
                        onClick = {
                            onEvent(
                                ConsultantSettingsEvent.SelectConsultant(consultant.id),
                            )
                        },
                        modifier =
                            Modifier.semantics {
                                selected = consultant.id == uiState.selectedConsultantId
                                role = Role.RadioButton
                            },
                    )
                }
            }
        }
        if (uiState.activeConsultants.isEmpty()) {
            Text(
                text = stringResource(R.string.no_active_consultants_supporting),
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(WorqOrderDimens.ItemPadding)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultantManagementScreen(
    uiState: ConsultantSettingsUiState,
    onEvent: (ConsultantSettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.consultant_management)) },
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            when {
                uiState.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.hasLoadError ->
                    ConsultantLoadError(
                        onRetry = { onEvent(ConsultantSettingsEvent.Retry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                else ->
                    ConsultantManagementList(
                        uiState = uiState,
                        onEvent = onEvent,
                    )
            }
        }
    }

    uiState.editor?.let { editor ->
        ConsultantEditorDialog(
            editor = editor,
            onNameChanged = { onEvent(ConsultantSettingsEvent.EditName(it)) },
            onConfirm = { onEvent(ConsultantSettingsEvent.ConfirmEditor) },
            onDismiss = { onEvent(ConsultantSettingsEvent.DismissEditor) },
        )
    }
    uiState.archiveConfirmation?.let { confirmation ->
        ArchiveConsultantDialog(
            confirmation = confirmation,
            onConfirm = { onEvent(ConsultantSettingsEvent.ConfirmArchive) },
            onDismiss = { onEvent(ConsultantSettingsEvent.DismissArchive) },
        )
    }
    uiState.restoreOffer?.let { offer ->
        RestoreArchivedConsultantDialog(
            offer = offer,
            onConfirm = { onEvent(ConsultantSettingsEvent.ConfirmRestoreOffer) },
            onDismiss = { onEvent(ConsultantSettingsEvent.DismissRestoreOffer) },
        )
    }
}

@Composable
private fun ConsultantManagementList(
    uiState: ConsultantSettingsUiState,
    onEvent: (ConsultantSettingsEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = WorqOrderDimens.ScreenPadding,
                vertical = WorqOrderDimens.ItemSpacing,
            ),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        uiState.message?.let { message ->
            item(key = "consultant-message") {
                ConsultantMessage(
                    message = message,
                    onDismiss = { onEvent(ConsultantSettingsEvent.DismissMessage) },
                )
            }
        }
        item(key = "add-consultant") {
            FilledTonalButton(
                onClick = { onEvent(ConsultantSettingsEvent.OpenAddConsultant) },
                enabled = uiState.pendingConsultantId == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.add_consultant),
                    modifier = Modifier.padding(start = WorqOrderDimens.ItemSpacing),
                )
            }
        }
        item(key = "active-heading") {
            ConsultantHeading(R.string.active_consultants)
        }
        if (uiState.activeConsultants.isEmpty()) {
            item(key = "active-empty") {
                EmptyConsultantSection(
                    title = stringResource(R.string.no_active_consultants),
                    supporting =
                        stringResource(R.string.no_active_consultants_management_supporting),
                )
            }
        } else {
            items(
                items = uiState.activeConsultants,
                key = ConsultantItemUi::id,
            ) { consultant ->
                ActiveConsultantRow(
                    consultant = consultant,
                    isSelected = consultant.id == uiState.selectedConsultantId,
                    isPending = consultant.id == uiState.pendingConsultantId,
                    onRename = {
                        onEvent(
                            ConsultantSettingsEvent.OpenRenameConsultant(consultant.id),
                        )
                    },
                    onArchive = {
                        onEvent(ConsultantSettingsEvent.RequestArchive(consultant.id))
                    },
                )
                HorizontalDivider()
            }
        }
        item(key = "archived-heading") {
            ConsultantHeading(R.string.archived_consultants)
        }
        if (uiState.archivedConsultants.isEmpty()) {
            item(key = "archived-empty") {
                EmptyConsultantSection(
                    title = stringResource(R.string.no_archived_consultants),
                    supporting = stringResource(R.string.no_archived_consultants_supporting),
                )
            }
        } else {
            items(
                items = uiState.archivedConsultants,
                key = ConsultantItemUi::id,
            ) { consultant ->
                ArchivedConsultantRow(
                    consultant = consultant,
                    isPending = consultant.id == uiState.pendingConsultantId,
                    onRestore = {
                        onEvent(ConsultantSettingsEvent.RestoreConsultant(consultant.id))
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyConsultantSection(
    title: String,
    supporting: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(WorqOrderDimens.ItemPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.TaskTextSpacing),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConsultantHeading(resource: Int) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(WorqOrderDimens.ItemPadding),
    )
}

@Composable
private fun ActiveConsultantRow(
    consultant: ConsultantItemUi,
    isSelected: Boolean,
    isPending: Boolean,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    ListItem(
        modifier = Modifier.semantics { selected = isSelected },
        headlineContent = { Text(consultant.name) },
        supportingContent = {
            Text(
                stringResource(
                    if (isSelected) {
                        R.string.selected_consultant_status
                    } else {
                        R.string.active_consultant_status
                    },
                ),
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename, enabled = !isPending) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription =
                            stringResource(R.string.rename_consultant_action, consultant.name),
                    )
                }
                IconButton(onClick = onArchive, enabled = !isPending) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription =
                            stringResource(R.string.remove_consultant_action, consultant.name),
                    )
                }
            }
        },
    )
}

@Composable
private fun ArchivedConsultantRow(
    consultant: ConsultantItemUi,
    isPending: Boolean,
    onRestore: () -> Unit,
) {
    val description = stringResource(R.string.restore_consultant_action, consultant.name)
    ListItem(
        headlineContent = { Text(consultant.name) },
        supportingContent = { Text(stringResource(R.string.archived_consultant_status)) },
        trailingContent = {
            TextButton(
                onClick = onRestore,
                enabled = !isPending,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(stringResource(R.string.restore_consultant))
            }
        },
    )
}

@Composable
private fun ConsultantEditorDialog(
    editor: ConsultantEditorUiState,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldError = editor.fieldError?.fieldErrorText()
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (editor.mode == ConsultantEditorMode.ADD) {
                        R.string.add_consultant_title
                    } else {
                        R.string.rename_consultant
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChanged,
                enabled = !editor.isSaving,
                label = { Text(stringResource(R.string.consultant_name)) },
                supportingText = {
                    Text(
                        text = fieldError ?: stringResource(R.string.consultant_name_limit),
                        modifier =
                            if (fieldError == null) {
                                Modifier
                            } else {
                                Modifier.semantics {
                                    error(fieldError)
                                    liveRegion = LiveRegionMode.Assertive
                                }
                            },
                    )
                },
                isError = fieldError != null,
                singleLine = true,
                keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(WorqOrderDimens.InlineProgressSize),
                        strokeWidth = WorqOrderDimens.InlineProgressStrokeWidth,
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ArchiveConsultantDialog(
    confirmation: ArchiveConsultantConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!confirmation.isArchiving) onDismiss() },
        title = {
            Text(
                stringResource(
                    R.string.remove_consultant_title,
                    confirmation.consultantName,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (confirmation.wasSelected) {
                        R.string.remove_selected_consultant_explanation
                    } else {
                        R.string.remove_consultant_explanation
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !confirmation.isArchiving) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !confirmation.isArchiving) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreArchivedConsultantDialog(
    offer: ArchivedConsultantRestoreOffer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!offer.isRestoring) onDismiss() },
        title = { Text(stringResource(R.string.restore_archived_consultant_title)) },
        text = {
            Text(
                stringResource(
                    R.string.restore_archived_consultant_explanation,
                    offer.consultantName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !offer.isRestoring) {
                Text(stringResource(R.string.restore_consultant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !offer.isRestoring) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConsultantMessage(
    message: ConsultantSettingsMessage,
    onDismiss: () -> Unit,
) {
    ListItem(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        headlineContent = {
            Text(
                text =
                    stringResource(
                        when (message) {
                            ConsultantSettingsMessage.DATA_UNAVAILABLE ->
                                R.string.consultant_data_unavailable
                            ConsultantSettingsMessage.CONSULTANT_NOT_FOUND ->
                                R.string.consultant_not_found
                            ConsultantSettingsMessage.CONSULTANT_ARCHIVED ->
                                R.string.consultant_archived_during_selection
                            ConsultantSettingsMessage.RESTORE_NAME_CONFLICT ->
                                R.string.restore_consultant_conflict
                        },
                    ),
                color = MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                )
            }
        },
    )
}

@Composable
private fun ConsultantLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(WorqOrderDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(stringResource(R.string.consultants_load_failed))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ConsultantNameFieldError.fieldErrorText(): String =
    stringResource(
        when (this) {
            ConsultantNameFieldError.BLANK -> R.string.consultant_name_blank
            ConsultantNameFieldError.TOO_LONG -> R.string.consultant_name_too_long
            ConsultantNameFieldError.DUPLICATE_ACTIVE -> R.string.consultant_name_duplicate
        },
    )
