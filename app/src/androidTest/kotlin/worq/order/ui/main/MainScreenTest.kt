package worq.order.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ExportDestination
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsZeroTimerAndDisabledStart() {
        setMainContent(MainUiState.ready())

        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                "Tracked time 00:00:00. Timer stopped.",
            ).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(hasText("No Tasks for This Date"))
        composeRule.onNodeWithText("No Tasks for This Date").assertIsDisplayed()
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
                duration = "27:05:03",
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
            .onAllNodesWithText("27:05:03")
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
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(
                hasTestTag(MainScreenTestTags.taskRow("task-1")),
            )
        composeRule
            .onNodeWithTag(MainScreenTestTags.taskRow("task-1"))
            .performClick()
        composeRule
            .onNodeWithTag(MainScreenTestTags.TIMER_ACTION)
            .assertIsEnabled()
            .performClick()
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
    fun exportActionNamesDateAndDestination() {
        val events = mutableListOf<MainEvent>()
        setMainContent(
            state =
                MainUiState
                    .ready()
                    .copy(
                        canExport = true,
                        exportDestination = ExportDestination.GOOGLE_SHEETS,
                    ),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithText("Set up Google Sheets for Jul 24")
            .assertIsEnabled()
            .performClick()

        assertTrue(events.contains(MainEvent.Export))
    }

    @Test
    fun runningTimerRequiresStopBeforeExport() {
        setMainContent(
            state =
                MainUiState
                    .ready()
                    .copy(
                        timerAction = MainTimerAction.STOP,
                        canExport = false,
                    ),
        )

        composeRule
            .onNodeWithText("Stop Timer to Export")
            .assertIsNotEnabled()
    }

    @Test
    fun largeTextStacksBottomActionsAndKeepsMainContentScrollable() {
        val longDescription =
            "A long task description remains reachable on a narrow " +
                "large-text layout without overlapping primary actions."
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides
                    Density(
                        density = density.density,
                        fontScale = 2f,
                    ),
            ) {
                WorqOrderTheme(darkTheme = true) {
                    MainScreen(
                        uiState =
                            MainUiState
                                .ready()
                                .copy(
                                    canExport = true,
                                    tasks =
                                        listOf(
                                            task(
                                                id = "large-text-task",
                                                client =
                                                    "A Very Long Client Name for Layout Testing",
                                                description = longDescription,
                                            ),
                                        ),
                                ),
                        onEvent = {},
                    )
                }
            }
        }

        val exportBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.EXPORT_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val addBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.ADD_TASK_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(exportBounds.bottom <= addBounds.top)

        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(hasText(longDescription))
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
    }

    @Test
    fun timerAndDateSelectorRemainPinnedWhileLongTaskSectionScrolls() {
        val tasks =
            (1..20).map { index ->
                task(
                    id = "task-$index",
                    description = "Task number $index",
                )
            }
        setMainContent(
            MainUiState
                .ready()
                .copy(tasks = tasks),
        )

        val timerBeforeScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.TIMER)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val dateSelectorBeforeScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.DATE_SELECTOR)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(
                hasTestTag(MainScreenTestTags.taskRow("task-20")),
            )

        val timerAfterScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.TIMER)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val dateSelectorAfterScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.DATE_SELECTOR)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(timerBeforeScroll, timerAfterScroll)
        assertEquals(dateSelectorBeforeScroll, dateSelectorAfterScroll)
        composeRule
            .onNodeWithTag(MainScreenTestTags.taskRow("task-20"))
            .assertIsDisplayed()
    }

    @Test
    fun largeScaleShortLandscapeMovesActionsToTopAndKeepsTasksReachable() {
        val tasks =
            (1..8).map { index ->
                task(
                    id = "landscape-task-$index",
                    description = "Landscape task $index",
                )
            }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides
                    Density(
                        density = density.density,
                        fontScale = 2f,
                    ),
            ) {
                Box(
                    modifier =
                        androidx.compose.ui.Modifier
                            .width(640.dp)
                            .height(360.dp),
                ) {
                    WorqOrderTheme(darkTheme = false) {
                        MainScreen(
                            uiState =
                                MainUiState
                                    .ready()
                                    .copy(
                                        canExport = true,
                                        tasks = tasks,
                                    ),
                            onEvent = {},
                        )
                    }
                }
            }
        }

        val exportBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.EXPORT_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        composeRule.onNodeWithText("Export CSV").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Export Jul 24 as CSV")
            .assertIsDisplayed()
        val titleBounds =
            composeRule
                .onNodeWithText("WorqOrder")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        composeRule.onNodeWithText("Add task").assertIsDisplayed()
        val addTaskBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.ADD_TASK_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val settingsBounds =
            composeRule
                .onNodeWithContentDescription("Open settings")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "Landscape title and Export overlap: " +
                "title=$titleBounds, export=$exportBounds",
            titleBounds.right <= exportBounds.left,
        )
        assertTrue(
            "Landscape actions lack separation: " +
                "export=$exportBounds, add=$addTaskBounds",
            exportBounds.right < addTaskBounds.left,
        )
        assertTrue(
            "Add task overlaps Settings: " +
                "add=$addTaskBounds, settings=$settingsBounds",
            addTaskBounds.right <= settingsBounds.left,
        )
        assertTrue(
            "Landscape Export is too narrow: $exportBounds",
            exportBounds.width >= 144f,
        )
        assertTrue(
            "Landscape Add task is too narrow: $addTaskBounds",
            addTaskBounds.width >= 144f,
        )
        val timerBeforeScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.TIMER_CARD)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        composeRule
            .onNodeWithTag(MainScreenTestTags.TIMER)
            .fetchSemanticsNode()
        val dateSelectorBeforeScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.DATE_SELECTOR)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val contentBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.CONTENT)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "Short landscape must reserve task-list height; " +
                "timer=$timerBeforeScroll, date=$dateSelectorBeforeScroll, " +
                "content=$contentBounds",
            contentBounds.height > 0f,
        )

        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(
                hasTestTag(
                    MainScreenTestTags.taskRow("landscape-task-8"),
                ),
            )

        val timerAfterScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.TIMER_CARD)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        composeRule
            .onNodeWithTag(MainScreenTestTags.TIMER)
            .fetchSemanticsNode()
        val dateSelectorAfterScroll =
            composeRule
                .onNodeWithTag(MainScreenTestTags.DATE_SELECTOR)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(timerBeforeScroll, timerAfterScroll)
        assertEquals(dateSelectorBeforeScroll, dateSelectorAfterScroll)
        composeRule
            .onNodeWithTag(
                MainScreenTestTags.taskRow("landscape-task-8"),
            ).assertIsDisplayed()
    }

    @Test
    fun narrowPhoneStacksBottomActionsWithoutOverlap() {
        composeRule.setContent {
            Box(
                modifier =
                    androidx.compose.ui.Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
            ) {
                WorqOrderTheme(darkTheme = false) {
                    MainScreen(
                        uiState =
                            MainUiState
                                .ready()
                                .copy(canExport = true),
                        onEvent = {},
                    )
                }
            }
        }

        val exportBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.EXPORT_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val addBounds =
            composeRule
                .onNodeWithTag(MainScreenTestTags.ADD_TASK_ACTION)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(exportBounds.bottom <= addBounds.top)
    }

    @Test
    fun csvExportShowsDateProgressAndSuccessState() {
        var state by
            mutableStateOf(
                MainUiState
                    .ready()
                    .copy(
                        canExport = true,
                        exportDestination = ExportDestination.CSV,
                    ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = { event ->
                        if (event == MainEvent.Export) {
                            state =
                                state.copy(
                                    canExport = false,
                                    exportProgress = MainExportProgress.PREPARING,
                                )
                        }
                    },
                )
            }
        }

        composeRule
            .onNodeWithText("Export Jul 24 as CSV")
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Preparing CSV…").assertIsNotEnabled()

        state =
            state.copy(
                exportProgress = null,
                canExport = true,
                exportFeedback =
                    MainExportFeedback(
                        workDate = TODAY,
                        outcome = MainExportOutcome.SUCCESS,
                    ),
            )
        composeRule
            .onNodeWithText("Exported Jul 24, 2026 as CSV.")
            .assertIsDisplayed()
    }

    @Test
    fun xlsxExportNamesDateAndShowsProgressAndSuccess() {
        var state by
            mutableStateOf(
                MainUiState
                    .ready()
                    .copy(
                        canExport = true,
                        exportDestination = ExportDestination.XLSX,
                    ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = { event ->
                        if (event == MainEvent.Export) {
                            state =
                                state.copy(
                                    canExport = false,
                                    exportProgress = MainExportProgress.PREPARING,
                                )
                        }
                    },
                )
            }
        }

        composeRule
            .onNodeWithText("Export Jul 24 as XLSX")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithText("Preparing XLSX…")
            .assertIsNotEnabled()

        state =
            state.copy(
                canExport = true,
                exportProgress = null,
                exportFeedback =
                    MainExportFeedback(
                        workDate = TODAY,
                        outcome = MainExportOutcome.SUCCESS,
                        destination = ExportDestination.XLSX,
                    ),
            )
        composeRule
            .onNodeWithText("Exported Jul 24, 2026 as XLSX.")
            .assertIsDisplayed()
    }

    @Test
    fun googleExportShowsProgressSuccessAndRetryableFailure() {
        val events = mutableListOf<MainEvent>()
        var state by
            mutableStateOf(
                MainUiState
                    .ready()
                    .copy(
                        canExport = false,
                        exportDestination = ExportDestination.GOOGLE_SHEETS,
                        googleExportState = MainGoogleExportState.CONNECTED,
                        exportProgress = MainExportProgress.PREPARING,
                    ),
            )
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                MainScreen(
                    uiState = state,
                    onEvent = events::add,
                )
            }
        }

        composeRule
            .onNodeWithText("Exporting to Google Sheets…")
            .assertIsNotEnabled()

        state =
            state.copy(
                canExport = true,
                exportProgress = null,
                exportFeedback =
                    MainExportFeedback(
                        workDate = TODAY,
                        outcome = MainExportOutcome.SUCCESS,
                        destination = ExportDestination.GOOGLE_SHEETS,
                        tabName = "WorqOrder_2026-07-24",
                    ),
            )
        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(
                hasText("Submitted Jul 24, 2026 to Google Sheets."),
            )
        composeRule
            .onNodeWithText("Submitted Jul 24, 2026 to Google Sheets.")
            .assertIsDisplayed()

        state =
            state.copy(
                exportFeedback =
                    MainExportFeedback(
                        workDate = TODAY,
                        outcome = MainExportOutcome.OFFLINE,
                        destination = ExportDestination.GOOGLE_SHEETS,
                    ),
            )
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(events.contains(MainEvent.Export))
    }

    @Test
    fun googleOwnershipConflictNamesTabWithoutOfferingRetry() {
        setMainContent(
            state =
                MainUiState
                    .ready()
                    .copy(
                        exportDestination = ExportDestination.GOOGLE_SHEETS,
                        googleExportState = MainGoogleExportState.CONNECTED,
                        exportFeedback =
                            MainExportFeedback(
                                workDate = TODAY,
                                outcome =
                                    MainExportOutcome.TAB_NAME_CONFLICT,
                                destination =
                                    ExportDestination.GOOGLE_SHEETS,
                                tabName = "WorqOrder_2026-07-24",
                            ),
                    ),
        )

        composeRule
            .onNodeWithText(
                "The worksheet “WorqOrder_2026-07-24” already exists " +
                    "but is not owned by WorqOrder. Rename that worksheet, " +
                    "then retry.",
            ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
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

        composeRule
            .onNodeWithTag(MainScreenTestTags.CONTENT)
            .performScrollToNode(
                hasTestTag(MainScreenTestTags.taskRow("task-1")),
            )
        composeRule.onNodeWithContentDescription("Options for Client").performClick()
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

        composeRule.onNodeWithText("Timer Is Still Running").assertIsDisplayed()
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
