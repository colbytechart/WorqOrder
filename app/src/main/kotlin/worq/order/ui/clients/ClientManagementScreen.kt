package worq.order.ui.clients

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.PlaceholderDestination

@Composable
fun ClientManagementScreen(onNavigateBack: () -> Unit) {
    PlaceholderDestination(
        titleRes = R.string.client_management,
        onNavigateBack = onNavigateBack,
    ) {
        Text(stringResource(R.string.client_management_placeholder))
    }
}
