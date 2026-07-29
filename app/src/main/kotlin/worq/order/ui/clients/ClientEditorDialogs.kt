package worq.order.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import worq.order.ui.theme.WorqOrderDimens

@Composable
fun ClientEditorDialog(
    editor: ClientEditorUiState,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldError = editor.fieldError?.fieldErrorText()
    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                stringResource(
                    when (editor.mode) {
                        ClientEditorMode.ADD -> R.string.add_client_title
                        ClientEditorMode.RENAME -> R.string.rename_client
                    },
                ),
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChanged,
                    enabled = !editor.isSaving,
                    label = { Text(stringResource(R.string.client_name)) },
                    supportingText = {
                        Text(
                            text =
                                fieldError
                                    ?: stringResource(R.string.client_name_limit),
                            modifier =
                                if (fieldError != null) {
                                    Modifier.semantics {
                                        error(fieldError)
                                        liveRegion = LiveRegionMode.Assertive
                                    }
                                } else {
                                    Modifier
                                },
                        )
                    },
                    isError = fieldError != null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !editor.isSaving,
            ) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier =
                            androidx.compose.ui.Modifier.size(
                                WorqOrderDimens.InlineProgressSize,
                            ),
                        strokeWidth = WorqOrderDimens.InlineProgressStrokeWidth,
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun RestoreArchivedClientDialog(
    offer: ArchivedClientRestoreOffer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!offer.isRestoring) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.restore_archived_client_title))
        },
        text = {
            Text(
                stringResource(
                    R.string.restore_archived_client_explanation,
                    offer.clientName,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !offer.isRestoring,
            ) {
                Text(stringResource(R.string.restore_client))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !offer.isRestoring,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ClientNameFieldError.fieldErrorText(): String =
    stringResource(
        when (this) {
            ClientNameFieldError.BLANK -> R.string.client_name_blank
            ClientNameFieldError.TOO_LONG -> R.string.client_name_too_long
            ClientNameFieldError.DUPLICATE_ACTIVE -> R.string.client_name_duplicate
        },
    )
