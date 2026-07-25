package worq.order.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.PlaceholderDestination
import worq.order.ui.clients.ClientEditorDialog
import worq.order.ui.clients.RestoreArchivedClientDialog
import worq.order.ui.theme.WorqOrderDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    uiState: CreateTaskUiState,
    onEvent: (CreateTaskEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_task)) },
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(WorqOrderDimens.ScreenPadding),
            verticalArrangement =
                Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
        ) {
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
                            onClick = {
                                onEvent(CreateTaskEvent.RetryClients)
                            },
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
                            text =
                                stringResource(
                                    R.string.no_active_clients_supporting,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    ClientSelector(
                        uiState = uiState,
                        onEvent = onEvent,
                    )
            }
            TextButton(
                onClick = {
                    onEvent(CreateTaskEvent.OpenAddClient)
                },
                enabled = !uiState.isLoadingClients,
            ) {
                Text(stringResource(R.string.add_client_inline))
            }
            Text(
                text = stringResource(R.string.task_client_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.task_creation_milestone_notice),
                style = MaterialTheme.typography.bodyMedium,
            )
            uiState.message?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                when (message) {
                                    CreateTaskMessage.DATA_UNAVAILABLE ->
                                        R.string.client_data_unavailable
                                    CreateTaskMessage.CLIENT_NOT_FOUND ->
                                        R.string.client_not_found
                                    CreateTaskMessage.RESTORE_NAME_CONFLICT ->
                                        R.string.restore_client_conflict
                                },
                            ),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            onEvent(CreateTaskEvent.DismissMessage)
                        },
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.create_task_disabled))
                }
            }
        }
    }

    uiState.addClientEditor?.let { editor ->
        ClientEditorDialog(
            editor = editor,
            onNameChanged = {
                onEvent(CreateTaskEvent.EditNewClientName(it))
            },
            onConfirm = {
                onEvent(CreateTaskEvent.ConfirmAddClient)
            },
            onDismiss = {
                onEvent(CreateTaskEvent.DismissAddClient)
            },
        )
    }
    uiState.restoreOffer?.let { offer ->
        RestoreArchivedClientDialog(
            offer = offer,
            onConfirm = {
                onEvent(CreateTaskEvent.ConfirmRestoreOffer)
            },
            onDismiss = {
                onEvent(CreateTaskEvent.DismissRestoreOffer)
            },
        )
    }
}

@Composable
private fun ClientSelector(
    uiState: CreateTaskUiState,
    onEvent: (CreateTaskEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                onEvent(CreateTaskEvent.OpenClientMenu)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text =
                    uiState.selectedClient?.name
                        ?: stringResource(R.string.choose_client),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.change_client),
            )
        }
        DropdownMenu(
            expanded = uiState.isClientMenuExpanded,
            onDismissRequest = {
                onEvent(CreateTaskEvent.DismissClientMenu)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            uiState.activeClients.forEach { client ->
                DropdownMenuItem(
                    text = { Text(client.name) },
                    onClick = {
                        onEvent(CreateTaskEvent.SelectClient(client.id))
                    },
                )
            }
        }
    }
}

@Composable
fun EditTaskScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
) {
    PlaceholderDestination(
        titleRes = R.string.edit_task,
        onNavigateBack = onNavigateBack,
    ) {
        Text(stringResource(R.string.edit_task_placeholder, taskId))
    }
}
