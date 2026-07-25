package worq.order.ui.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import worq.order.testing.FakeClientRepository
import worq.order.testing.FakeClientRepository.Companion.client
import worq.order.testing.MainDispatcherRule
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.timer.CurrentDateProvider
import worq.order.data.TaskMetadataValidationError

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTaskViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectorObservesOnlyActiveClients() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client("active", "Active Client"),
                        client("archived", "Archived Client", isActive = false),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            assertEquals(
                listOf("Active Client"),
                viewModel.uiState.value.activeClients.map { it.name },
            )
            assertNull(viewModel.uiState.value.selectedClientId)
        }

    @Test
    fun emptySelectorInlineAddCreatesAndSelectsClient() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeClientRepository()
            val viewModel = viewModelAndCollect(repository)
            assertTrue(viewModel.uiState.value.activeClients.isEmpty())

            viewModel.onEvent(CreateTaskEvent.OpenAddClient)
            viewModel.onEvent(CreateTaskEvent.EditNewClientName(" New   Client "))
            viewModel.onEvent(CreateTaskEvent.ConfirmAddClient)
            runCurrent()

            assertEquals("New Client", viewModel.uiState.value.selectedClient?.name)
            assertFalse(viewModel.uiState.value.activeClients.isEmpty())
            assertNull(viewModel.uiState.value.addClientEditor)
        }

    @Test
    fun inlineAddOffersRestoreForMatchingArchivedClient() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(client("archived", "Acme", isActive = false)),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(CreateTaskEvent.OpenAddClient)
            viewModel.onEvent(CreateTaskEvent.EditNewClientName(" ACME "))
            viewModel.onEvent(CreateTaskEvent.ConfirmAddClient)
            runCurrent()
            assertEquals("archived", viewModel.uiState.value.restoreOffer?.clientId)

            viewModel.onEvent(CreateTaskEvent.ConfirmRestoreOffer)
            runCurrent()
            assertEquals("Acme", viewModel.uiState.value.selectedClient?.name)
        }

    @Test
    fun validSubmitCreatesOnceSelectsTodayAndNavigatesBack() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(client("client-1", "Client")),
                )
            val fixture = taskFixture(repository)
            val effects = mutableListOf<CreateTaskEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.viewModel.effects.collect(effects::add)
            }

            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditDescription(" New task "))
            fixture.viewModel.onEvent(
                CreateTaskEvent.EditHardwareSoftwarePurchases(" Laptop "),
            )
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)
            runCurrent()

            val tasks = fixture.tasks.observeTasksForDate(WORK_DATE).first()
            assertEquals(1, tasks.size)
            assertEquals("New task", tasks.single().task.description)
            assertEquals(
                "Laptop",
                tasks.single().task.hardwareSoftwarePurchases,
            )
            assertEquals(
                tasks.single().task.id,
                fixture.selection.readSelection()?.taskId,
            )
            assertEquals(listOf(CreateTaskEffect.NavigateBack), effects)
        }

    @Test
    fun validationAndDiscardDoNotCreateTask() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(client("client-1", "Client")),
                )
            val fixture = taskFixture(repository)
            val effects = mutableListOf<CreateTaskEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.viewModel.effects.collect(effects::add)
            }

            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditDescription("  "))
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)

            assertTrue(
                TaskMetadataValidationError.DESCRIPTION_REQUIRED in
                    fixture.viewModel.uiState.value.metadataErrors,
            )
            fixture.viewModel.onEvent(CreateTaskEvent.RequestClose)
            assertTrue(fixture.viewModel.uiState.value.showDiscardConfirmation)
            fixture.viewModel.onEvent(CreateTaskEvent.ConfirmDiscard)
            runCurrent()

            assertTrue(fixture.tasks.observeTasksForDate(WORK_DATE).first().isEmpty())
            assertEquals(listOf(CreateTaskEffect.NavigateBack), effects)
        }

    private fun TestScope.viewModelAndCollect(
        repository: FakeClientRepository,
    ): CreateTaskViewModel = taskFixture(repository).viewModel

    private fun TestScope.taskFixture(
        repository: FakeClientRepository,
    ): TaskFixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val selected = FakeSelectedTaskRepository()
        val clock = FakeUtcClock(Instant.parse("2026-07-25T12:00:00Z"))
        val zone = FakeZoneIdProvider(ZoneId.of("America/New_York"))
        val dateProvider = CurrentDateProvider(clock, zone)
        val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selected,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = dateProvider,
                zoneIdProvider = zone,
            )
        val coordinator =
            TaskMutationCoordinator(
                taskRepository = tasks,
                selectionCoordinator = selectionCoordinator,
                currentDateProvider = dateProvider,
                zoneIdProvider = zone,
            )
        val viewModel =
            CreateTaskViewModel(
                clientRepository = repository,
                taskMutationCoordinator = coordinator,
                workDate = WORK_DATE,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return TaskFixture(
            viewModel = viewModel,
            tasks = tasks,
            selection = selected,
        )
    }

    private data class TaskFixture(
        val viewModel: CreateTaskViewModel,
        val tasks: FakeTaskRepository,
        val selection: FakeSelectedTaskRepository,
    )

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 25)
    }
}
