package worq.order.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsZeroTimerAndDisabledStart() {
        setMainContent(MainUiState.ready())

        composeRule.onNodeWithText("00:00:00.000").assertIsDisplayed()
        composeRule.onNodeWithText("No tasks for this date").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MainScreenTestTags.TIMER_ACTION)
            .assertIsNotEnabled()
    }

    @Test
    fun taskRowShowsMetadataDurationSelectionRunningAndArchivedState() {
        val running =
            task(
                id = "task-1",
                client = "Legacy Client",
                description =
                    "A deliberately long description that remains bounded to the task row.",
                duration = "27:05:03.012",
                selected = true,
                running = true,
                archived = true,
                canSelect = false,
            )
        setMainContent(
            MainUiState
                .ready()
                .copy(
                    tasks = listOf(running),
                    timerText = running.totalDuration,
                    timerAction = MainTimerAction.STOP,
                    canStop = true,
                ),
        )

        composeRule.onNodeWithText("Legacy Client").assertIsDisplayed()
        composeRule
            .onAllNodesWithText("27:05:03.012")
            .assertCountEquals(2)
        composeRule.onNodeWithText("Archived client").assertIsDisplayed()
        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MainScreenTestTags.taskRow("task-1"))
            .assertIsSelected()
            .assertIsNotEnabled()
    }

    @Test
    fun taskTapAndTimerControlsEmitExpectedEvents() {
        val events = mutableListOf<MainEvent>()
        var state by
            mutableStateOf(
                MainUiState
                    .ready()
                    .copy(
                        tasks = listOf(task(id = "task-1")),
                        canStart = true,
                    ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = { event ->
                        events += event
                        if (event == MainEvent.StartTimer) {
                            state =
                                state.copy(
                                    timerAction = MainTimerAction.STOP,
                                    canStart = false,
                                    canStop = true,
                                )
                        }
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(MainScreenTestTags.taskRow("task-1"))
            .performClick()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Stop").assertIsEnabled().performClick()

        assertTrue(events.contains(MainEvent.SelectTask("task-1")))
        assertTrue(events.contains(MainEvent.StartTimer))
        assertTrue(events.contains(MainEvent.StopTimer))
    }

    @Test
    fun dateArrowsAndPickerReturnConcreteDateEvents() {
        val events = mutableListOf<MainEvent>()
        var state by mutableStateOf(MainUiState.ready())
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = { event ->
                        events += event
                        when (event) {
                            MainEvent.OpenDatePicker ->
                                state = state.copy(isDatePickerVisible = true)
                            is MainEvent.PickDate ->
                                state =
                                    state.copy(
                                        displayedDate = event.date,
                                        isDatePickerVisible = false,
                                    )
                            else -> Unit
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Previous day").performClick()
        composeRule.onNodeWithContentDescription("Next day").performClick()
        composeRule.onNodeWithContentDescription("Choose date").performClick()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()

        assertTrue(events.contains(MainEvent.PreviousDate))
        assertTrue(events.contains(MainEvent.NextDate))
        assertTrue(events.contains(MainEvent.OpenDatePicker))
        assertTrue(events.contains(MainEvent.PickDate(TODAY)))
    }

    @Test
    fun settingsAndAddTaskActionsUseViewModelEvents() {
        val events = mutableListOf<MainEvent>()
        setMainContent(
            state = MainUiState.ready(),
            onEvent = events::add,
        )

        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Add task").performClick()

        assertTrue(events.contains(MainEvent.OpenSettings))
        assertTrue(events.contains(MainEvent.OpenCreateTask))
    }

    @Test
    fun overflowMenuOffersEditAndDeleteEvents() {
        val events = mutableListOf<MainEvent>()
        var state by
            mutableStateOf(
                MainUiState
                    .ready()
                    .copy(tasks = listOf(task(id = "task-1"))),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = { event ->
                        events += event
                        when (event) {
                            is MainEvent.OpenTaskMenu ->
                                state = state.copy(openTaskMenuTaskId = event.taskId)
                            MainEvent.CloseTaskMenu ->
                                state = state.copy(openTaskMenuTaskId = null)
                            else -> Unit
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Options for Description").performClick()
        composeRule.onNodeWithText("Edit").assertIsEnabled().performClick()
        assertTrue(events.contains(MainEvent.EditTask("task-1")))

        state = state.copy(openTaskMenuTaskId = "task-1")
        composeRule.onNodeWithText("Delete").assertIsEnabled().performClick()
        assertTrue(events.contains(MainEvent.RequestDeleteTask("task-1")))
    }

    @Test
    fun browsingWhileRunningShowsIdentityAndTodayShortcut() {
        val state =
            MainUiState
                .ready()
                .copy(
                    displayedDate = TODAY.minusDays(1),
                    isToday = false,
                    timerAction = MainTimerAction.STOP,
                    canStop = true,
                    runningTask =
                        RunningTaskUi(
                            taskId = "task-1",
                            clientName = "Northwind",
                            description = "Current work",
                            workDate = TODAY,
                        ),
                )
        setMainContent(state)

        composeRule.onNodeWithText("Timer is still running").assertIsDisplayed()
        composeRule
            .onNodeWithText("Northwind · Current work · Jul 24, 2026")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    private fun setMainContent(
        state: MainUiState,
        onEvent: (MainEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = onEvent,
                )
            }
        }
    }

    private fun MainUiState.Companion.ready(): MainUiState =
        MainUiState(
            displayedDate = TODAY,
            today = TODAY,
            isToday = true,
            isLoading = false,
        )

    private fun task(
        id: String,
        client: String = "Client",
        description: String = "Description",
        duration: String = MainUiState.ZERO_DURATION,
        selected: Boolean = false,
        running: Boolean = false,
        archived: Boolean = false,
        canSelect: Boolean = true,
    ) = MainTaskItemUi(
        id = id,
        clientName = client,
        description = description,
        totalDuration = duration,
        isClientArchived = archived,
        isSelected = selected,
        isRunning = running,
        canSelect = canSelect,
        canModify = !running,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
    }
}
