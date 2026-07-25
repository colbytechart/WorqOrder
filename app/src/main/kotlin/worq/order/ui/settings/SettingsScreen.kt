package worq.order.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.theme.WorqOrderDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenClientManagement: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                    .padding(horizontal = WorqOrderDimens.ScreenPadding),
            verticalArrangement =
                Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.settings_overview),
                style = MaterialTheme.typography.bodyLarge,
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenClientManagement),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.client_management))
                },
                supportingContent = {
                    Text(stringResource(R.string.client_management_summary))
                },
                trailingContent = {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
            Text(
                text = stringResource(R.string.settings_preferences_deferred),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
