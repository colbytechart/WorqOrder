package worq.order.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.clients.ClientItemUi

@Composable
internal fun TaskClientSelector(
    activeClients: List<ClientItemUi>,
    selectedClientName: String?,
    expanded: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onOpen,
            enabled = enabled && activeClients.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedClientName ?: stringResource(R.string.choose_client),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.change_client),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            activeClients.forEach { client ->
                DropdownMenuItem(
                    text = { Text(client.name) },
                    onClick = { onSelect(client.id) },
                )
            }
        }
    }
}
