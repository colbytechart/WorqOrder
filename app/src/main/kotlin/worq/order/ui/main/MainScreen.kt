package worq.order.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import worq.order.R
import worq.order.ui.theme.WorqOrderDimens
import worq.order.ui.theme.WorqOrderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onOpenSettings: () -> Unit,
    onCreateTask: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            MainBottomActions(
                canExport = uiState.canExport,
                onCreateTask = onCreateTask,
            )
        },
    ) { scaffoldPadding ->
        MainContent(
            uiState = uiState,
            contentPadding = scaffoldPadding,
        )
    }
}

@Composable
private fun MainContent(
    uiState: MainUiState,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(WorqOrderDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
    ) {
        TimerCard(canStart = uiState.canStart)
        DateSelector()
        EmptyTaskList(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = WorqOrderDimens.TaskListMinimumHeight),
        )
    }
}

@Composable
private fun MainBottomActions(
    canExport: Boolean,
    onCreateTask: () -> Unit,
) {
    BottomAppBar(
        contentPadding =
            PaddingValues(
                horizontal = WorqOrderDimens.ScreenPadding,
                vertical = WorqOrderDimens.BottomActionVerticalPadding,
            ),
    ) {
        FilledTonalButton(
            onClick = {},
            enabled = canExport,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = WorqOrderDimens.ActionButtonHeight),
        ) {
            Text(stringResource(R.string.export))
        }
        Spacer(Modifier.width(WorqOrderDimens.BottomActionSpacing))
        Button(
            onClick = onCreateTask,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = WorqOrderDimens.ActionButtonHeight),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.add_task))
        }
    }
}

@Composable
private fun TimerCard(canStart: Boolean) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(WorqOrderDimens.TimerCardHeight),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WorqOrderDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = stringResource(R.string.tracked_time),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.timer_zero),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {},
                enabled = canStart,
            ) {
                Text(stringResource(R.string.start))
            }
        }
    }
}

@Composable
private fun DateSelector() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_day),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.work_date),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = stringResource(R.string.date_preview),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(
            onClick = {},
            modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = stringResource(R.string.choose_date),
            )
        }
        IconButton(
            onClick = {},
            modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_day),
            )
        }
    }
}

@Composable
private fun EmptyTaskList(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WorqOrderDimens.CardPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                Text(
                    text = stringResource(R.string.task_list),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.empty_task_list),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.empty_task_list_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenDarkPreview() {
    WorqOrderTheme(darkTheme = true) {
        MainScreen(
            uiState = MainUiState(),
            onOpenSettings = {},
            onCreateTask = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenLightPreview() {
    WorqOrderTheme(darkTheme = false) {
        MainScreen(
            uiState = MainUiState(),
            onOpenSettings = {},
            onCreateTask = {},
        )
    }
}
