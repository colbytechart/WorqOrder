package worq.order.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import worq.order.R
import worq.order.ui.theme.WorqOrderDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientManagementScreen(
    uiState: ClientManagementUiState,
    onEvent: (ClientManagementEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.client_management)) },
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                uiState.hasLoadError ->
                    ClientLoadError(
                        onRetry = { onEvent(ClientManagementEvent.Retry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                else ->
                    ClientList(
                        uiState = uiState,
                        onEvent = onEvent,
                    )
            }
        }
    }

    uiState.editor?.let { editor ->
        ClientEditorDialog(
            editor = editor,
            onNameChanged = {
                onEvent(ClientManagementEvent.EditName(it))
            },
            onConfirm = {
                onEvent(ClientManagementEvent.ConfirmEditor)
            },
            onDismiss = {
                onEvent(ClientManagementEvent.DismissEditor)
            },
        )
    }
    uiState.archiveConfirmation?.let { confirmation ->
        ArchiveClientDialog(
            confirmation = confirmation,
            onConfirm = {
                onEvent(ClientManagementEvent.ConfirmArchive)
            },
            onDismiss = {
                onEvent(ClientManagementEvent.DismissArchive)
            },
        )
    }
    uiState.restoreOffer?.let { offer ->
        RestoreArchivedClientDialog(
            offer = offer,
            onConfirm = {
                onEvent(ClientManagementEvent.ConfirmRestoreOffer)
            },
            onDismiss = {
                onEvent(ClientManagementEvent.DismissRestoreOffer)
            },
        )
    }
}

@Composable
private fun ClientList(
    uiState: ClientManagementUiState,
    onEvent: (ClientManagementEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = WorqOrderDimens.ScreenPadding,
                vertical = WorqOrderDimens.ItemSpacing,
            ),
        verticalArrangement =
            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        uiState.message?.let { message ->
            item(key = "client-message") {
                ClientMessage(
                    message = message,
                    onDismiss = {
                        onEvent(ClientManagementEvent.DismissMessage)
                    },
                )
            }
        }
        item(key = "add-client") {
            FilledTonalButton(
                onClick = {
                    onEvent(ClientManagementEvent.OpenAddClient)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.add_client),
                    modifier =
                        Modifier.padding(
                            start = WorqOrderDimens.ItemSpacing,
                        ),
                )
            }
        }
        item(key = "active-heading") {
            Text(
                text = stringResource(R.string.active_clients),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.padding(
                        top = WorqOrderDimens.ItemSpacing,
                    ),
            )
        }
        if (uiState.activeClients.isEmpty()) {
            item(key = "active-empty") {
                EmptyClientSection(
                    title = stringResource(R.string.no_active_clients),
                    supporting = stringResource(R.string.no_active_clients_supporting),
                )
            }
        } else {
            items(
                items = uiState.activeClients,
                key = ClientItemUi::id,
            ) { client ->
                ActiveClientRow(
                    client = client,
                    isPending = uiState.pendingClientId == client.id,
                    onRename = {
                        onEvent(ClientManagementEvent.OpenRenameClient(client.id))
                    },
                    onArchive = {
                        onEvent(ClientManagementEvent.RequestArchive(client.id))
                    },
                )
                HorizontalDivider()
            }
        }
        item(key = "archived-heading") {
            Text(
                text = stringResource(R.string.archived_clients),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.padding(
                        top = WorqOrderDimens.SectionSpacing,
                    ),
            )
        }
        if (uiState.archivedClients.isEmpty()) {
            item(key = "archived-empty") {
                EmptyClientSection(
                    title = stringResource(R.string.no_archived_clients),
                    supporting = stringResource(R.string.archived_client_status),
                )
            }
        } else {
            items(
                items = uiState.archivedClients,
                key = ClientItemUi::id,
            ) { client ->
                ArchivedClientRow(
                    client = client,
                    isPending = uiState.pendingClientId == client.id,
                    onRestore = {
                        onEvent(ClientManagementEvent.RestoreClient(client.id))
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ActiveClientRow(
    client: ClientItemUi,
    isPending: Boolean,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(client.name) },
        supportingContent = {
            Text(stringResource(R.string.active_client_status))
        },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onRename,
                    enabled = !isPending,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription =
                            stringResource(
                                R.string.rename_client_action,
                                client.name,
                            ),
                    )
                }
                IconButton(
                    onClick = onArchive,
                    enabled = !isPending,
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription =
                            stringResource(
                                R.string.remove_client_action,
                                client.name,
                            ),
                    )
                }
            }
        },
    )
}

@Composable
private fun ArchivedClientRow(
    client: ClientItemUi,
    isPending: Boolean,
    onRestore: () -> Unit,
) {
    val restoreContentDescription =
        stringResource(
            R.string.restore_client_action,
            client.name,
        )
    ListItem(
        headlineContent = { Text(client.name) },
        supportingContent = {
            Text(stringResource(R.string.archived_client_status))
        },
        trailingContent = {
            TextButton(
                onClick = onRestore,
                enabled = !isPending,
                modifier =
                    Modifier.semantics {
                        contentDescription = restoreContentDescription
                    },
            ) {
                Text(stringResource(R.string.restore_client))
            }
        },
    )
}

@Composable
private fun EmptyClientSection(
    title: String,
    supporting: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(WorqOrderDimens.ItemPadding),
        verticalArrangement =
            Arrangement.spacedBy(WorqOrderDimens.TaskTextSpacing),
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
private fun ClientLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.padding(WorqOrderDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(stringResource(R.string.clients_load_failed))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ClientMessage(
    message: ClientManagementMessage,
    onDismiss: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text =
                    stringResource(
                        when (message) {
                            ClientManagementMessage.DATA_UNAVAILABLE ->
                                R.string.client_data_unavailable
                            ClientManagementMessage.CLIENT_NOT_FOUND ->
                                R.string.client_not_found
                            ClientManagementMessage.RESTORE_NAME_CONFLICT ->
                                R.string.restore_client_conflict
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
private fun ArchiveClientDialog(
    confirmation: ArchiveClientConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!confirmation.isArchiving) {
                onDismiss()
            }
        },
        title = {
            Text(
                stringResource(
                    R.string.remove_client_title,
                    confirmation.clientName,
                ),
            )
        },
        text = {
            Text(stringResource(R.string.remove_client_explanation))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !confirmation.isArchiving,
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !confirmation.isArchiving,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
