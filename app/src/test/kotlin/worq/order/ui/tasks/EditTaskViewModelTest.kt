package worq.order.ui.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.domain.ManualIntervalValidationError
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeClientRepository
import worq.order.testing.FakeClientRepository.Companion.client
import worq.order.testing.FakeEmployeeRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule
import worq.order.timer.CurrentDateProvider
import worq.order.model.Employee
import worq.order.model.WorkType

@OptIn(ExperimentalCoroutinesApi::class)
class EditTaskViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savesClientDescriptionAndPurchasesForDailyTask() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val effects = mutableListOf<EditTaskEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.viewModel.effects.collect(effects::add)
            }

            fixture.viewModel.onEvent(EditTaskEvent.SelectClient("client-2"))
            fixture.viewModel.onEvent(EditTaskEvent.EditDescription(" Updated "))
            fixture.viewModel.onEvent(
                EditTaskEvent.EditHardwareSoftwarePurchases(" Software license "),
            )
            fixture.viewModel.onEvent(EditTaskEvent.SelectConsultant("employee-2"))
            fixture.viewModel.onEvent(EditTaskEvent.SelectWorkType(WorkType.IN_OFFICE))
            fixture.viewModel.onEvent(EditTaskEvent.EditMileage("012.500"))
            fixture.viewModel.onEvent(EditTaskEvent.SaveMetadata)
            runCurrent()

            val changed =
                requireNotNull(fixture.tasks.readTaskWithClient(fixture.taskId)).task
            assertEquals("client-2", changed.clientId)
            assertEquals("Updated", changed.description)
            assertEquals("Software license", changed.hardwareSoftwarePurchases)
            assertEquals("employee-2", changed.employeeId)
            assertEquals(WorkType.IN_OFFICE, changed.workType)
            assertEquals("12.5", changed.mileage)
            assertFalse(fixture.viewModel.uiState.value.hasUnsavedMetadataChanges)
            assertEquals(listOf(EditTaskEffect.NavigateBack), effects)
        }

    @Test
    fun addEditValidationDeleteIntervalUpdatesTotalAndOrder() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()

            fixture.viewModel.onEvent(EditTaskEvent.OpenAddInterval)
            fixture.viewModel.onEvent(EditTaskEvent.SaveInterval)
            runCurrent()
            val interval = fixture.viewModel.uiState.value.intervals.single()
            assertEquals("01:00:00", fixture.viewModel.uiState.value.totalDuration)
            assertEquals(60L, fixture.viewModel.uiState.value.billingMinutes)
            assertEquals("09:00 AM", interval.startText)
            assertEquals("10:00 AM", interval.stopText)

            fixture.viewModel.onEvent(EditTaskEvent.OpenEditInterval(interval.id))
            fixture.viewModel.onEvent(
                EditTaskEvent.SetEditorTime(IntervalEndpoint.START, 11, 0),
            )
            fixture.viewModel.onEvent(
                EditTaskEvent.SetEditorTime(IntervalEndpoint.STOP, 10, 0),
            )
            fixture.viewModel.onEvent(EditTaskEvent.SaveInterval)
            runCurrent()
            assertTrue(
                ManualIntervalValidationError.START_MUST_PRECEDE_STOP in
                    requireNotNull(
                        fixture.viewModel.uiState.value.intervalEditor,
                    ).validationErrors,
            )

            fixture.viewModel.onEvent(EditTaskEvent.DismissIntervalEditor)
            fixture.viewModel.onEvent(EditTaskEvent.RequestDeleteInterval(interval.id))
            fixture.viewModel.onEvent(EditTaskEvent.ConfirmDeleteInterval)
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.intervals.isEmpty())
            assertEquals("00:00:00", fixture.viewModel.uiState.value.totalDuration)
            assertEquals(0L, fixture.viewModel.uiState.value.billingMinutes)
        }

    @Test
    fun runningTaskLocksMetadataIntervalsAndDeletion() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            fixture.active.createActiveInterval(
                taskId = fixture.taskId,
                boundaryZoneId = ZONE,
                start = NOW,
            )
            runCurrent()

            assertTrue(fixture.viewModel.uiState.value.isRunning)
            fixture.viewModel.onEvent(EditTaskEvent.EditDescription("Blocked"))
            fixture.viewModel.onEvent(EditTaskEvent.SaveMetadata)
            fixture.viewModel.onEvent(EditTaskEvent.OpenAddInterval)
            fixture.viewModel.onEvent(EditTaskEvent.RequestDeleteTask)
            fixture.viewModel.onEvent(EditTaskEvent.ConfirmDeleteTask)
            runCurrent()

            assertEquals(
                EditTaskMessage.RUNNING_TASK,
                fixture.viewModel.uiState.value.message,
            )
            assertTrue(fixture.tasks.readTaskWithClient(fixture.taskId) != null)
            assertNull(fixture.viewModel.uiState.value.intervalEditor)
        }

    @Test
    fun deletingSelectedTaskClearsSelectionAndNavigatesBack() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture(selectTask = true)
            val effects = mutableListOf<EditTaskEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.viewModel.effects.collect(effects::add)
            }

            fixture.viewModel.onEvent(EditTaskEvent.RequestDeleteTask)
            fixture.viewModel.onEvent(EditTaskEvent.ConfirmDeleteTask)
            runCurrent()

            assertNull(fixture.tasks.readTaskWithClient(fixture.taskId))
            assertNull(fixture.selection.readSelection())
            assertEquals(listOf(EditTaskEffect.NavigateBack), effects)
        }

    private suspend fun TestScope.fixture(
        selectTask: Boolean = false,
    ): Fixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val selection = FakeSelectedTaskRepository()
        val clock = FakeUtcClock(NOW)
        val zone = FakeZoneIdProvider(ZONE)
        val currentDate = CurrentDateProvider(clock, zone)
        val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selection,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = currentDate,
                zoneIdProvider = zone,
            )
        val mutationCoordinator =
            TaskMutationCoordinator(
                taskRepository = tasks,
                selectionCoordinator = selectionCoordinator,
                currentDateProvider = currentDate,
                zoneIdProvider = zone,
            )
        val task =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Task",
                    hardwareSoftwarePurchases = "Laptop",
                    employeeId = "employee-1",
                    employeeNameSnapshot = "Alex Rivera",
                    workType = WorkType.ON_SITE,
                    mileage = "5",
                    workDate = TODAY,
                    zoneId = ZONE,
                ),
            )
        if (selectTask) {
            selectionCoordinator.selectTask(task.id)
        }
        val clients =
            FakeClientRepository(
                listOf(
                    client("client-1", "First"),
                    client("client-2", "Second"),
                ),
            )
        val employees =
            FakeEmployeeRepository(
                listOf(
                    employee("employee-1", "Alex Rivera"),
                    employee("employee-2", "Morgan Lee"),
                ),
            )
        val viewModel =
            EditTaskViewModel(
                taskId = task.id,
                taskRepository = tasks,
                clientRepository = clients,
                employeeRepository = employees,
                activeTimerRepository = active,
                taskMutationCoordinator = mutationCoordinator,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return Fixture(
            taskId = task.id,
            tasks = tasks,
            active = active,
            selection = selection,
            viewModel = viewModel,
        )
    }

    private data class Fixture(
        val taskId: String,
        val tasks: FakeTaskRepository,
        val active: FakeActiveTimerRepository,
        val selection: FakeSelectedTaskRepository,
        val viewModel: EditTaskViewModel,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 25)
        val NOW: Instant = Instant.parse("2026-07-25T16:00:00Z")

        fun employee(
            id: String,
            name: String,
        ) =
            Employee(
                id = id,
                name = name,
                canonicalName = name.lowercase(),
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                archivedAt = null,
            )
    }
}
