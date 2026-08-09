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
import worq.order.ui.employees.ConsultantItemUi

@Composable
internal fun TaskConsultantSelector(
    activeConsultants: List<ConsultantItemUi>,
    selectedConsultantName: String?,
    expanded: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onOpen,
            enabled = enabled && activeConsultants.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedConsultantName ?: stringResource(R.string.choose_consultant),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.change_consultant),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            activeConsultants.forEach { consultant ->
                DropdownMenuItem(
                    text = { Text(consultant.name) },
                    onClick = { onSelect(consultant.id) },
                )
            }
        }
    }
}
