package worq.order.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import worq.order.R
import worq.order.ui.theme.WorqOrderDimens
import worq.order.ui.theme.WorqOrderTheme

object MainScreenTestTags {
    const val TIMER = "main_timer"
    const val TIMER_ACTION = "main_timer_action"
    const val TASK_LIST = "main_task_list"

    fun taskRow(taskId: String): String = "main_task_$taskId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onEvent: (MainEvent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    currentOnEvent(MainEvent.LifecycleResumed)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (uiState.isDatePickerVisible) {
        MainDatePickerDialog(
            initialDate = uiState.displayedDate,
            onDateSelected = { onEvent(MainEvent.PickDate(it)) },
            onDismiss = { onEvent(MainEvent.DismissDatePicker) },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = { onEvent(MainEvent.OpenSettings) },
                    ) {
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
                onExport = {},
                onCreateTask = { onEvent(MainEvent.OpenCreateTask) },
            )
        },
    ) { scaffoldPadding ->
        MainContent(
            uiState = uiState,
            contentPadding = scaffoldPadding,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun MainContent(
    uiState: MainUiState,
    contentPadding: PaddingValues,
    onEvent: (MainEvent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = WorqOrderDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
    ) {
        TimerCard(
            uiState = uiState,
            onStart = { onEvent(MainEvent.StartTimer) },
            onStop = { onEvent(MainEvent.StopTimer) },
        )
        DateSelector(
            displayedDate = uiState.displayedDate,
            isToday = uiState.isToday,
            onPreviousDate = { onEvent(MainEvent.PreviousDate) },
            onNextDate = { onEvent(MainEvent.NextDate) },
            onChooseDate = { onEvent(MainEvent.OpenDatePicker) },
            onReturnToToday = { onEvent(MainEvent.ReturnToToday) },
        )
        if (uiState.isTimerRunning && !uiState.isToday) {
            RunningTaskBanner(
                runningTask = uiState.runningTask,
                onReturnToToday = { onEvent(MainEvent.ReturnToToday) },
            )
        }
        uiState.message?.let { message ->
            MainMessageBanner(
                message = message,
                onDismiss = { onEvent(MainEvent.DismissMessage) },
            )
        }
        TaskList(
            uiState = uiState,
            onSelectTask = { onEvent(MainEvent.SelectTask(it)) },
            onOpenTaskMenu = { onEvent(MainEvent.OpenTaskMenu(it)) },
            onCloseTaskMenu = { onEvent(MainEvent.CloseTaskMenu) },
            onRetry = { onEvent(MainEvent.RetryData) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@Composable
private fun MainBottomActions(
    canExport: Boolean,
    onExport: () -> Unit,
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
            onClick = onExport,
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
private fun TimerCard(
    uiState: MainUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isStopping = uiState.timerAction == MainTimerAction.STOP
    val actionEnabled = if (isStopping) uiState.canStop else uiState.canStart
    val timerDescription =
        stringResource(
            if (isStopping) {
                R.string.timer_running_semantics
            } else {
                R.string.timer_stopped_semantics
            },
            uiState.timerText,
        )
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WorqOrderDimens.TimerCardHeight),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(WorqOrderDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            Text(
                text = stringResource(R.string.tracked_time),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = uiState.timerText,
                modifier =
                    Modifier
                        .testTag(MainScreenTestTags.TIMER)
                        .semantics {
                            contentDescription = timerDescription
                            stateDescription = timerDescription
                        },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Button(
                onClick = if (isStopping) onStop else onStart,
                enabled = actionEnabled,
                modifier =
                    Modifier
                        .testTag(MainScreenTestTags.TIMER_ACTION)
                        .heightIn(min = WorqOrderDimens.ActionButtonHeight),
            ) {
                if (uiState.isTimerOperationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(WorqOrderDimens.InlineProgressSize),
                        strokeWidth = WorqOrderDimens.InlineProgressStrokeWidth,
                    )
                } else {
                    Icon(
                        imageVector =
                            if (isStopping) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.PlayArrow
                            },
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        if (isStopping) {
                            R.string.stop
                        } else {
                            R.string.start
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DateSelector(
    displayedDate: LocalDate,
    isToday: Boolean,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onChooseDate: () -> Unit,
    onReturnToToday: () -> Unit,
) {
    val formattedDate =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .format(displayedDate)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPreviousDate,
                modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_day),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.work_date),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text =
                        if (isToday) {
                            stringResource(R.string.today_with_date, formattedDate)
                        } else {
                            formattedDate
                        },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(
                onClick = onChooseDate,
                modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = stringResource(R.string.choose_date),
                )
            }
            IconButton(
                onClick = onNextDate,
                modifier = Modifier.size(WorqOrderDimens.IconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_day),
                )
            }
        }
        if (!isToday) {
            TextButton(onClick = onReturnToToday) {
                Text(stringResource(R.string.return_to_today))
            }
        }
    }
}

@Composable
private fun RunningTaskBanner(
    runningTask: RunningTaskUi?,
    onReturnToToday: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(WorqOrderDimens.ItemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.timer_still_running),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (runningTask != null) {
                    val formattedWorkDate =
                        DateTimeFormatter
                            .ofLocalizedDate(FormatStyle.MEDIUM)
                            .format(runningTask.workDate)
                    Text(
                        text =
                            stringResource(
                                R.string.running_task_summary,
                                runningTask.clientName,
                                runningTask.description,
                                formattedWorkDate,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onReturnToToday) {
                Text(stringResource(R.string.today))
            }
        }
    }
}

@Composable
private fun MainMessageBanner(
    message: MainMessage,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(WorqOrderDimens.ItemPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(message.stringResource()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss_message),
                )
            }
        }
    }
}

@Composable
private fun TaskList(
    uiState: MainUiState,
    onSelectTask: (String) -> Unit,
    onOpenTaskMenu: (String) -> Unit,
    onCloseTaskMenu: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(
            text = stringResource(R.string.task_list),
            style = MaterialTheme.typography.titleMedium,
        )
        when {
            uiState.isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(MainScreenTestTags.TASK_LIST),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.hasTaskLoadError -> {
                TaskLoadError(
                    onRetry = onRetry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(MainScreenTestTags.TASK_LIST),
                )
            }
            uiState.tasks.isEmpty() -> {
                EmptyTaskList(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(MainScreenTestTags.TASK_LIST),
                )
            }
            else -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(MainScreenTestTags.TASK_LIST),
                    contentPadding =
                        PaddingValues(
                            bottom = WorqOrderDimens.SectionSpacing,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                ) {
                    items(
                        items = uiState.tasks,
                        key = MainTaskItemUi::id,
                    ) { task ->
                        TaskRow(
                            task = task,
                            menuExpanded = uiState.openTaskMenuTaskId == task.id,
                            onSelect = { onSelectTask(task.id) },
                            onOpenMenu = { onOpenTaskMenu(task.id) },
                            onCloseMenu = onCloseTaskMenu,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: MainTaskItemUi,
    menuExpanded: Boolean,
    onSelect: () -> Unit,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    val rowStateDescription =
        when {
            task.isRunning -> stringResource(R.string.running)
            task.isSelected -> stringResource(R.string.task_selected_state)
            else -> stringResource(R.string.task_not_selected_state)
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MainScreenTestTags.taskRow(task.id))
                .selectable(
                    selected = task.isSelected,
                    enabled = task.canSelect,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ).semantics {
                    selected = task.isSelected
                    stateDescription = rowStateDescription
                },
        colors =
            if (task.isSelected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(WorqOrderDimens.ItemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.TaskTextSpacing),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                ) {
                    Text(
                        text = task.clientName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.isClientArchived) {
                        StatusLabel(stringResource(R.string.archived))
                    }
                }
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                ) {
                    Text(
                        text = task.totalDuration,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    when {
                        task.isRunning ->
                            StatusLabel(stringResource(R.string.running))
                        task.isSelected ->
                            StatusLabel(stringResource(R.string.task_selected_state))
                    }
                }
            }
            Box {
                IconButton(onClick = onOpenMenu) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription =
                            stringResource(
                                R.string.task_options,
                                task.description,
                            ),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onCloseMenu,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_task_deferred)) },
                        onClick = {},
                        enabled = false,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_task_deferred)) },
                        onClick = {},
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier =
                Modifier.padding(
                    horizontal = WorqOrderDimens.StatusHorizontalPadding,
                    vertical = WorqOrderDimens.StatusVerticalPadding,
                ),
            style = MaterialTheme.typography.labelSmall,
        )
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

@Composable
private fun TaskLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    text = stringResource(R.string.task_load_failed),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState =
        androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis =
                initialDate
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        onDateSelected(
                            Instant
                                .ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate(),
                        )
                    }
                },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = true,
        )
    }
}

private fun MainMessage.stringResource(): Int =
    when (this) {
        MainMessage.SELECT_A_TASK_FIRST -> R.string.select_task_first
        MainMessage.LIVE_TIMING_TODAY_ONLY -> R.string.live_timing_today_only
        MainMessage.TIMER_ALREADY_RUNNING -> R.string.timer_already_running
        MainMessage.STOP_BEFORE_SWITCHING -> R.string.stop_before_switching
        MainMessage.SELECTED_TASK_MISSING -> R.string.selected_task_missing
        MainMessage.NO_ACTIVE_TIMER -> R.string.no_active_timer
        MainMessage.CLOCK_CHANGED -> R.string.clock_changed
        MainMessage.DATA_UNAVAILABLE -> R.string.data_unavailable
        MainMessage.TASK_ACTIONS_DEFERRED -> R.string.task_actions_deferred
    }

@Preview(showBackground = true)
@Composable
private fun MainScreenDarkPreview() {
    WorqOrderTheme(darkTheme = true) {
        MainScreen(
            uiState =
                MainUiState(
                    displayedDate = LocalDate.of(2026, 7, 24),
                    today = LocalDate.of(2026, 7, 24),
                    isLoading = false,
                    tasks =
                        listOf(
                            MainTaskItemUi(
                                id = "task-1",
                                clientName = "Northwind",
                                description = "Review work order",
                                totalDuration = "01:24:18.450",
                                isClientArchived = false,
                                isSelected = true,
                                isRunning = false,
                                canSelect = true,
                            ),
                        ),
                    timerText = "01:24:18.450",
                    canStart = true,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenLightPreview() {
    WorqOrderTheme(darkTheme = false) {
        MainScreen(
            uiState =
                MainUiState(
                    displayedDate = LocalDate.of(2026, 7, 24),
                    today = LocalDate.of(2026, 7, 24),
                    isLoading = false,
                ),
            onEvent = {},
        )
    }
}
