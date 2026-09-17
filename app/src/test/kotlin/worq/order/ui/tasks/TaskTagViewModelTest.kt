package worq.order.ui.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.AppSettings
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.model.Employee
import worq.order.model.Tag
import worq.order.model.TagCategory
import worq.order.model.WorkType
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeClientRepository
import worq.order.testing.FakeClientRepository.Companion.client
import worq.order.testing.FakeEmployeeRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeTagRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule
import worq.order.timer.CurrentDateProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TaskTagViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun tagOnlyDescriptionSavesOrderedSnapshots() =
        runTest(mainDispatcherRule.dispatcher) {
            val tags =
                FakeTagRepository(
                    listOf(
                        testTag("tag-1", "Install monitor"),
                        testTag("tag-2", "Verify cabling"),
                    ),
                )
            val fixture = createFixture(tags)

            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION))
            fixture.viewModel.onEvent(CreateTaskEvent.ToggleTagPickerItem("tag-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.ToggleTagPickerItem("tag-2"))
            fixture.viewModel.onEvent(CreateTaskEvent.ApplyTagPicker)
            fixture.viewModel.onEvent(CreateTaskEvent.CreateTask)
            runCurrent()

            val saved = fixture.tasks.readTasksWithIntervalsForDate(WORK_DATE).single()
            assertEquals("", saved.taskWithClient.task.description)
            assertEquals(
                listOf("Install monitor", "Verify cabling"),
                saved.tagSnapshots
                    .filter { it.category == TagCategory.DESCRIPTION }
                    .map { it.text },
            )
        }

    @Test
    fun inlineTagCreationPersistsWhenTaskCreationIsCancelled() =
        runTest(mainDispatcherRule.dispatcher) {
            val tags = FakeTagRepository()
            val fixture = createFixture(tags)

            fixture.viewModel.onEvent(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION))
            fixture.viewModel.onEvent(CreateTaskEvent.OpenInlineTagCreate)
            fixture.viewModel.onEvent(CreateTaskEvent.EditInlineTagText("Install monitor"))
            fixture.viewModel.onEvent(CreateTaskEvent.ConfirmInlineTagCreate)
            runCurrent()

            assertEquals(1, tags.tags.value.size)
            assertEquals(
                listOf("Install monitor"),
                fixture.viewModel.uiState.value.tagPicker?.draftSelections?.map { it.text },
            )
            fixture.viewModel.onEvent(CreateTaskEvent.DismissTagPicker)
            fixture.viewModel.onEvent(CreateTaskEvent.RequestClose)
            assertFalse(fixture.viewModel.uiState.value.showDiscardConfirmation)
            assertTrue(fixture.tasks.readTasksWithIntervalsForDate(WORK_DATE).isEmpty())
        }

    @Test
    fun applyingTagsReportsProjectedLimitBeforeTaskSave() =
        runTest(mainDispatcherRule.dispatcher) {
            val tags = FakeTagRepository(listOf(testTag("tag-1", "y".repeat(400))))
            val fixture = createFixture(tags)

            fixture.viewModel.onEvent(CreateTaskEvent.SelectClient("client-1"))
            fixture.viewModel.onEvent(CreateTaskEvent.EditDescription("x".repeat(598)))
            fixture.viewModel.onEvent(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION))
            fixture.viewModel.onEvent(CreateTaskEvent.ToggleTagPickerItem("tag-1"))
            val unsavedBeforeApply = fixture.viewModel.uiState.value.hasUnsavedTaskChanges
            assertTrue(unsavedBeforeApply)
            fixture.viewModel.onEvent(CreateTaskEvent.ApplyTagPicker)

            assertEquals(
                TaskTagPickerError.COMPOSED_TEXT_TOO_LONG,
                fixture.viewModel.uiState.value.tagPicker?.error,
            )
            assertTrue(fixture.viewModel.uiState.value.descriptionTagSelections.isEmpty())
            assertEquals(
                unsavedBeforeApply,
                fixture.viewModel.uiState.value.hasUnsavedTaskChanges,
            )

            fixture.viewModel.onEvent(CreateTaskEvent.DismissTagPicker)
            assertTrue(fixture.viewModel.uiState.value.descriptionTagSelections.isEmpty())
            assertEquals(
                unsavedBeforeApply,
                fixture.viewModel.uiState.value.hasUnsavedTaskChanges,
            )
            assertTrue(fixture.tasks.readTasksWithIntervalsForDate(WORK_DATE).isEmpty())
        }

    @Test
    fun applyingAnUnchangedPickerDoesNotCreateUnsavedTaskChanges() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(FakeTagRepository())

            fixture.viewModel.onEvent(CreateTaskEvent.OpenTagPicker(TaskTagField.DESCRIPTION))
            fixture.viewModel.onEvent(CreateTaskEvent.ApplyTagPicker)

            assertEquals(null, fixture.viewModel.uiState.value.tagPicker)
            assertFalse(fixture.viewModel.uiState.value.hasUnsavedTaskChanges)
        }

    private fun TestScope.createFixture(tags: FakeTagRepository): Fixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val selected = FakeSelectedTaskRepository()
        val clock = FakeUtcClock(Instant.parse("2026-07-25T12:00:00Z"))
        val zone = FakeZoneIdProvider(ZoneId.of("America/New_York"))
        val dateProvider = CurrentDateProvider(clock, zone)
        val employee = employee("employee-1", "Alex Rivera")
        val employees = FakeEmployeeRepository(listOf(employee))
        val settings = FakeSettingsRepository(AppSettings(selectedEmployeeId = employee.id))
        val consultantSelection = ConsultantSelectionCoordinator(employees, settings)
        val selection =
            SelectionCoordinator(
                selectedTaskRepository = selected,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = dateProvider,
                zoneIdProvider = zone,
            )
        val mutation =
            TaskMutationCoordinator(
                taskRepository = tasks,
                selectionCoordinator = selection,
                currentDateProvider = dateProvider,
                zoneIdProvider = zone,
            )
        val viewModel =
            CreateTaskViewModel(
                clientRepository = FakeClientRepository(listOf(client("client-1", "Client"))),
                employeeRepository = employees,
                settingsRepository = settings,
                consultantSelectionCoordinator = consultantSelection,
                taskMutationCoordinator = mutation,
                tagRepository = tags,
                workDate = WORK_DATE,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return Fixture(viewModel, tasks)
    }

    private data class Fixture(
        val viewModel: CreateTaskViewModel,
        val tasks: FakeTaskRepository,
    )

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 25)

        fun employee(id: String, name: String) =
            Employee(
                id = id,
                name = name,
                canonicalName = name.lowercase(),
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                archivedAt = null,
            )

        fun testTag(id: String, text: String) =
            Tag(
                id = id,
                category = TagCategory.DESCRIPTION,
                text = text,
                normalizedText = text.lowercase(),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
    }
}
