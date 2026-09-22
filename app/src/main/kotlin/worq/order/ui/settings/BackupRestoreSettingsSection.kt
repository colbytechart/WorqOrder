package worq.order.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import worq.order.R
import worq.order.data.BackupRestoreStatus
import worq.order.ui.theme.WorqOrderDimens

@Composable
internal fun BackupRestoreSettingsSection(
    uiState: BackupRestoreUiState,
    onEvent: (BackupRestoreEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.backup_restore)) {
        Column(
            modifier = Modifier.padding(WorqOrderDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            BackupRestoreSettingsContent(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
internal fun BackupRestoreConfirmationDialogs(
    uiState: BackupRestoreUiState,
    onEvent: (BackupRestoreEvent) -> Unit,
) {
    when (uiState.confirmation) {
        BackupRestoreConfirmation.IMPORT ->
            ImportBackupConfirmationDialog(
                onDismiss = { onEvent(BackupRestoreEvent.DismissImportConfirmation) },
                onConfirm = { onEvent(BackupRestoreEvent.ConfirmImport) },
            )
        BackupRestoreConfirmation.RESTORE ->
            RestoreBackupConfirmationDialog(
                onDismiss = { onEvent(BackupRestoreEvent.DismissRestoreConfirmation) },
                onConfirm = { onEvent(BackupRestoreEvent.ConfirmRestore) },
            )
        null -> Unit
    }
}

@Composable
private fun BackupRestoreSettingsContent(
    uiState: BackupRestoreUiState,
    onEvent: (BackupRestoreEvent) -> Unit,
) {
    if (uiState.isTimerRunning) {
        Text(
            text = stringResource(R.string.backup_restore_timer_running),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    backupRestoreOperationText(uiState.operation)?.let { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    uiState.status?.let { status ->
        val text = backupRestoreStatusText(status.status)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion =
                            if (status.isError) {
                                LiveRegionMode.Assertive
                            } else {
                                LiveRegionMode.Polite
                            }
                        if (status.isError) {
                            error(text)
                        }
                    },
        ) {
            Text(
                text = text,
                color =
                    if (status.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
            TextButton(onClick = { onEvent(BackupRestoreEvent.DismissStatus) }) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        OutlinedButton(
            onClick = { onEvent(BackupRestoreEvent.ImportBackup) },
            enabled = uiState.canImportBackup,
            modifier =
                Modifier
                    .weight(1f)
                    .sizeIn(minHeight = WorqOrderDimens.IconButtonSize),
        ) {
            Text(stringResource(R.string.import_backup))
        }
        Button(
            onClick = { onEvent(BackupRestoreEvent.CreateBackup) },
            enabled = uiState.canCreateBackup,
            modifier =
                Modifier
                    .weight(1f)
                    .sizeIn(minHeight = WorqOrderDimens.IconButtonSize),
        ) {
            Text(stringResource(R.string.create_backup))
        }
    }
    Button(
        onClick = { onEvent(BackupRestoreEvent.RestorePreviousState) },
        enabled = uiState.canRestore,
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = WorqOrderDimens.IconButtonSize),
    ) {
        Text(stringResource(R.string.restore))
    }
}

@Composable
private fun ImportBackupConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.replace_all_worqorder_data)) },
        text = { Text(stringResource(R.string.replace_all_worqorder_data_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreBackupConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_previous_state_question)) },
        text = { Text(stringResource(R.string.restore_previous_state_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun backupRestoreOperationText(operation: BackupRestoreOperation): String? =
    when (operation) {
        BackupRestoreOperation.CREATING_BACKUP ->
            stringResource(R.string.creating_backup)
        BackupRestoreOperation.STAGING_IMPORT ->
            stringResource(R.string.reading_backup)
        BackupRestoreOperation.IMPORTING ->
            stringResource(R.string.importing_backup)
        BackupRestoreOperation.RESTORING ->
            stringResource(R.string.restoring_previous_state)
        BackupRestoreOperation.IDLE,
        BackupRestoreOperation.CHOOSING_BACKUP_DESTINATION,
        BackupRestoreOperation.CHOOSING_IMPORT_SOURCE,
        BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION,
        BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION,
        -> null
    }

@Composable
private fun backupRestoreStatusText(status: BackupRestoreStatus): String =
    stringResource(
        when (status) {
            BackupRestoreStatus.BACKUP_CREATED -> R.string.backup_created
            BackupRestoreStatus.BACKUP_TIMER_RUNNING -> R.string.backup_timer_running
            BackupRestoreStatus.BACKUP_INVALID_LOCAL_STATE -> R.string.backup_invalid_local_state
            BackupRestoreStatus.BACKUP_OUTPUT_FAILED -> R.string.backup_output_failed
            BackupRestoreStatus.BACKUP_OUTPUT_PARTIAL -> R.string.backup_output_partial
            BackupRestoreStatus.IMPORT_INVALID_ARCHIVE -> R.string.import_invalid_archive
            BackupRestoreStatus.IMPORT_STORAGE_FAILURE -> R.string.import_storage_failure
            BackupRestoreStatus.IMPORT_TIMER_RUNNING -> R.string.import_timer_running
            BackupRestoreStatus.IMPORT_SUCCEEDED -> R.string.import_succeeded
            BackupRestoreStatus.IMPORT_FAILED -> R.string.import_failed
            BackupRestoreStatus.RESTORE_UNAVAILABLE -> R.string.restore_unavailable
            BackupRestoreStatus.RESTORE_TIMER_RUNNING -> R.string.restore_timer_running
            BackupRestoreStatus.RESTORE_SUCCEEDED -> R.string.restore_succeeded
            BackupRestoreStatus.RESTORE_FAILED -> R.string.restore_failed
            BackupRestoreStatus.RECOVERY_REQUIRED -> R.string.backup_recovery_required
        },
    )
