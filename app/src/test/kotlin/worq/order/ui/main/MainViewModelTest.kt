package worq.order.ui.main

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.model.DailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.CurrentDateProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.TimerCoordinator
import worq.order.timer.TimerOperationLock

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emptyStateAndStartWithoutSelectionAreSafe() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertTrue(viewModel.uiState.value.tasks.isEmpty())
            assertFalse(viewModel.uiState.value.canStart)
            assertEquals(MainUiState.ZERO_DURATION, viewModel.uiState.value.timerText)

            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            assertEquals(
                MainMessage.SELECT_A_TASK_FIRST,
                viewModel.uiState.value.message,
            )
        }

    @Test
    fun datedTaskCanBeSelectedAndItsCompletedTotalIsDisplayed() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY, description = "Prepare report")
            fixture.tasks.insertCompletedInterval(
                taskId = task.id,
                start = Instant.parse("2026-07-24T13:00:00Z"),
                stop = Instant.parse("2026-07-24T14:00:00Z"),
                wasManuallyEdited = false,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(1, state.tasks.size)
            assertTrue(state.tasks.single().isSelected)
            assertTrue(state.canStart)
            assertEquals("01:00:00.000", state.timerText)
        }

    @Test
    fun historicalSelectionDisplaysItsTotalButCannotStart() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val historical = fixture.addTask(TODAY.minusDays(1))
            fixture.tasks.insertCompletedInterval(
                taskId = historical.id,
                start = Instant.parse("2026-07-23T13:00:00Z"),
                stop = Instant.parse("2026-07-23T13:30:00Z"),
                wasManuallyEdited = false,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(historical.id))
            runCurrent()

            assertEquals(TODAY.minusDays(1), viewModel.uiState.value.displayedDate)
            assertEquals("00:30:00.000", viewModel.uiState.value.timerText)
            assertFalse(viewModel.uiState.value.canStart)
        }

    @Test
    fun startUsesMonotonicTicksAndStopFreezesAccumulatedTotal() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.tasks.insertCompletedInterval(
                taskId = task.id,
                start = NOW.minusSeconds(3_600),
                stop = NOW,
                wasManuallyEdited = false,
            )
            fixture.clock.instant = NOW.plusSeconds(60)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            assertEquals("01:00:00.000", viewModel.uiState.value.timerText)
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()
            assertEquals(MainTimerAction.STOP, viewModel.uiState.value.timerAction)

            fixture.clock.instant = fixture.clock.instant.plusSeconds(1)
            fixture.monotonic.nanos += Duration.ofSeconds(1).toNanos()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS)
            runCurrent()
            assertEquals("01:00:01.000", viewModel.uiState.value.timerText)

            viewModel.onEvent(MainEvent.StopTimer)
            runCurrent()
            assertEquals(MainTimerAction.START, viewModel.uiState.value.timerAction)
            assertEquals("01:00:01.000", viewModel.uiState.value.timerText)

            fixture.monotonic.nanos += Duration.ofMinutes(10).toNanos()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS * 2)
            runCurrent()
            assertEquals("01:00:01.000", viewModel.uiState.value.timerText)
        }

    @Test
    fun runningTimerLocksTaskSwitchingAndRemainsVisibleWhileBrowsing() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val first = fixture.addTask(TODAY, seriesId = "series-1")
            val second = fixture.addTask(TODAY, seriesId = "series-2")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(first.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            viewModel.onEvent(MainEvent.SelectTask(second.id))
            runCurrent()

            assertEquals(
                MainMessage.STOP_BEFORE_SWITCHING,
                viewModel.uiState.value.message,
            )
            assertTrue(viewModel.uiState.value.tasks.single { it.id == first.id }.isRunning)
            assertFalse(viewModel.uiState.value.tasks.single { it.id == second.id }.canSelect)

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            assertFalse(viewModel.uiState.value.isToday)
            assertEquals(MainTimerAction.STOP, viewModel.uiState.value.timerAction)
            assertTrue(viewModel.uiState.value.canStop)
            assertEquals(first.id, viewModel.uiState.value.runningTask?.taskId)
        }

    @Test
    fun dateArrowsPickerAndTodayShortcutUpdateDisplayedDate() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = Fixture().viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            assertEquals(TODAY.minusDays(1), viewModel.uiState.value.displayedDate)

            viewModel.onEvent(MainEvent.NextDate)
            runCurrent()
            assertEquals(TODAY, viewModel.uiState.value.displayedDate)

            val picked = LocalDate.of(2026, 6, 1)
            viewModel.onEvent(MainEvent.OpenDatePicker)
            viewModel.onEvent(MainEvent.PickDate(picked))
            runCurrent()
            assertEquals(picked, viewModel.uiState.value.displayedDate)
            assertFalse(viewModel.uiState.value.isDatePickerVisible)

            viewModel.onEvent(MainEvent.ReturnToToday)
            runCurrent()
            assertEquals(TODAY, viewModel.uiState.value.displayedDate)
        }

    @Test
    fun navigationEventsCarryTheCurrentPresentationContext() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = Fixture().viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()

            val createEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.OpenCreateTask)
            assertEquals(
                MainEffect.NavigateToCreateTask(TODAY.minusDays(1)),
                createEffect.await(),
            )

            val settingsEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.OpenSettings)
            assertEquals(MainEffect.NavigateToSettings, settingsEffect.await())
        }

    @Test
    fun repositoryBackedSelectionSurvivesViewModelRecreation() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val first = fixture.viewModel()
            collectState(first)
            runCurrent()
            first.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            assertTrue(first.uiState.value.tasks.single().isSelected)

            val recreated = fixture.viewModel()
            collectState(recreated)
            runCurrent()

            assertTrue(recreated.uiState.value.tasks.single().isSelected)
            assertTrue(recreated.uiState.value.canStart)
        }

    private fun kotlinx.coroutines.test.TestScope.collectState(
        viewModel: MainViewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val selection = FakeSelectedTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val clock = FakeUtcClock(NOW)
        val zone = FakeZoneIdProvider(NEW_YORK)
        val monotonic = FakeMonotonicTimeSource()
        private val currentDateProvider = CurrentDateProvider(clock, zone)
        private val operationLock = TimerOperationLock()
        private val liveTimerSession = LiveTimerSession(monotonic)
        private val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selection,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = currentDateProvider,
                zoneIdProvider = zone,
            )
        private val timerCoordinator =
            TimerCoordinator(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                zoneIdProvider = zone,
                liveTimerSession = liveTimerSession,
                operationLock = operationLock,
            )
        private val normalizer =
            ActiveTimerNormalizer(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                liveTimerSession = liveTimerSession,
                operationLock = operationLock,
            )
        private val taskMutationCoordinator =
            TaskMutationCoordinator(
                taskRepository = tasks,
                selectionCoordinator = selectionCoordinator,
                currentDateProvider = currentDateProvider,
                zoneIdProvider = zone,
            )

        fun viewModel() =
            MainViewModel(
                taskRepository = tasks,
                activeTimerRepository = active,
                selectionCoordinator = selectionCoordinator,
                timerCoordinator = timerCoordinator,
                activeTimerNormalizer = normalizer,
                liveTimerSession = liveTimerSession,
                utcClock = clock,
                zoneIdProvider = zone,
                currentDateProvider = currentDateProvider,
                taskMutationCoordinator = taskMutationCoordinator,
            )

        suspend fun addTask(
            date: LocalDate,
            description: String = "Task",
            seriesId: String = "series-1",
        ): DailyTask =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = description,
                    workDate = date,
                    zoneId = NEW_YORK,
                    seriesId = seriesId,
                ),
            )
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
