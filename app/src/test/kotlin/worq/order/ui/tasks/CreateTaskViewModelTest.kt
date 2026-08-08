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
import worq.order.testing.FakeEmployeeRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.timer.CurrentDateProvider
import worq.order.data.TaskMetadataValidationError
import worq.order.data.AppSettings
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.model.Employee
import worq.order.model.BillingStatus
import worq.order.model.WorkType

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
            fixture.viewModel.onEvent(CreateTaskEvent.SelectWorkType(WorkType.IN_OFFICE))
            fixture.viewModel.onEvent(
                CreateTaskEvent.SelectBillingStatus(BillingStatus.DO_NOT_BILL),
            )
            fixture.viewModel.onEvent(CreateTaskEvent.EditMileage("012.500"))
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
            assertEquals("employee-1", tasks.single().task.employeeId)
            assertEquals(WorkType.IN_OFFICE, tasks.single().task.workType)
            assertEquals(BillingStatus.DO_NOT_BILL, tasks.single().task.billingStatus)
            assertEquals("12.5", tasks.single().task.mileage)
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

    @Test
    fun malformedMileageBlocksCreationAndInvalidCharactersAreIgnored() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture =
                taskFixture(
                    FakeClientRepository(listOf(client("client-1", "Client"))),
                )
            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditDescription("Task"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditMileage("12a"))
            assertEquals("", fixture.viewModel.uiState.value.mileage)

            fixture.viewModel.onEvent(CreateTaskEvent.EditMileage("1.2345"))
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)

            assertTrue(
                TaskMetadataValidationError.MILEAGE_TOO_PRECISE in
                    fixture.viewModel.uiState.value.metadataErrors,
            )
            assertTrue(fixture.tasks.observeTasksForDate(WORK_DATE).first().isEmpty())
        }

    @Test
    fun missingConsultantBlocksCreationAndOffersSettingsRoute() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeClientRepository(listOf(client("client-1", "Client")))
            val fixture = taskFixture(repository, selectConsultant = false)
            val effects = mutableListOf<CreateTaskEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.viewModel.effects.collect(effects::add)
            }

            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditDescription("Task"))
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)
            fixture.viewModel.onEvent(CreateTaskEvent.OpenConsultantSettings)
            runCurrent()

            assertEquals(
                CreateTaskMessage.CONSULTANT_REQUIRED,
                fixture.viewModel.uiState.value.message,
            )
            assertTrue(fixture.tasks.observeTasksForDate(WORK_DATE).first().isEmpty())
            assertEquals(listOf(CreateTaskEffect.NavigateToSettings), effects)
        }

    private fun TestScope.viewModelAndCollect(
        repository: FakeClientRepository,
    ): CreateTaskViewModel = taskFixture(repository).viewModel

    private fun TestScope.taskFixture(
        repository: FakeClientRepository,
        selectConsultant: Boolean = true,
    ): TaskFixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val selected = FakeSelectedTaskRepository()
        val clock = FakeUtcClock(Instant.parse("2026-07-25T12:00:00Z"))
        val zone = FakeZoneIdProvider(ZoneId.of("America/New_York"))
        val dateProvider = CurrentDateProvider(clock, zone)
        val employee = employee("employee-1", "Alex Rivera")
        val employees = FakeEmployeeRepository(listOf(employee))
        val settings =
            FakeSettingsRepository(
                AppSettings(
                    selectedEmployeeId = employee.id.takeIf { selectConsultant },
                ),
            )
        val consultantSelectionCoordinator =
            ConsultantSelectionCoordinator(employees, settings)
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
                employeeRepository = employees,
                settingsRepository = settings,
                consultantSelectionCoordinator = consultantSelectionCoordinator,
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

        fun employee(
            id: String,
            name: String,
            active: Boolean = true,
        ) =
            Employee(
                id = id,
                name = name,
                canonicalName = name.lowercase(),
                isActive = active,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                archivedAt = null,
            )
    }
}
