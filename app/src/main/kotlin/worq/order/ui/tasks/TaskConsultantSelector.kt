package worq.order.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import worq.order.R
import worq.order.ui.employees.ConsultantItemUi
import worq.order.ui.theme.WorqOrderDimens

@Composable
internal fun TaskConsultantSelector(
    activeConsultants: List<ConsultantItemUi>,
    selectedConsultantName: String?,
    expanded: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val selectorEnabled = enabled && activeConsultants.isNotEmpty()
    val isError = errorMessage != null
    val errorColor = MaterialTheme.colorScheme.error
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onOpen,
                enabled = selectorEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    if (isError) {
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = errorColor,
                            disabledContentColor = errorColor,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                border =
                    if (isError) {
                        BorderStroke(1.dp, errorColor)
                    } else {
                        ButtonDefaults.outlinedButtonBorder(enabled = selectorEnabled)
                    },
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
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = errorColor,
                modifier =
                    Modifier.semantics {
                        error(message)
                        liveRegion = LiveRegionMode.Assertive
                    },
            )
        }
    }
}
