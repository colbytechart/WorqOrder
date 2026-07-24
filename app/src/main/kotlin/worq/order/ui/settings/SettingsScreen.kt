package worq.order.ui.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.PlaceholderDestination

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenClientManagement: () -> Unit,
) {
    PlaceholderDestination(
        titleRes = R.string.settings,
        onNavigateBack = onNavigateBack,
    ) {
        Text(stringResource(R.string.settings_placeholder))
        Button(onClick = onOpenClientManagement) {
            Text(stringResource(R.string.open_client_management))
        }
    }
}
