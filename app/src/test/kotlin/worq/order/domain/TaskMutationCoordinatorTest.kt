package worq.order.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.timer.CurrentDateProvider

class TaskMutationCoordinatorTest {
    @Test
    fun createTodayTrimsMetadataAndSelectsNewTask() =
        runTest {
            val fixture = Fixture()

            val result =
                fixture.coordinator.createTask(
                    clientId = "client-1",
                    description = "  Install workstation  ",
                    hardwareSoftwarePurchases = "  Laptop  ",
                    workDate = TODAY,
                    employeeId = "employee-1",
                )

            val created = result as CreateTaskOperationResult.Created
            assertEquals("Install workstation", created.task.description)
            assertEquals("Laptop", created.task.hardwareSoftwarePurchases)
            assertTrue(created.selectedForTiming)
            assertEquals(created.task.id, fixture.selection.readSelection()?.taskId)
        }

    @Test
    fun createHistoricalTaskDoesNotReplaceTimingSelection() =
        runTest {
            val fixture = Fixture()

            val result =
                fixture.coordinator.createTask(
                    clientId = "client-1",
                    description = "Historical",
                    hardwareSoftwarePurchases = "",
                    workDate = TODAY.minusDays(1),
                    employeeId = "employee-1",
                )

            assertTrue(result is CreateTaskOperationResult.Created)
            assertTrue(!(result as CreateTaskOperationResult.Created).selectedForTiming)
            assertNull(fixture.selection.readSelection())
        }

    @Test
    fun createWithoutConsultantIsRejectedBeforePersistence() =
        runTest {
            val fixture = Fixture()

            assertEquals(
                CreateTaskOperationResult.ConsultantUnavailable,
                fixture.coordinator.createTask(
                    clientId = "client-1",
                    description = "Task",
                    hardwareSoftwarePurchases = "",
                    workDate = TODAY,
                    employeeId = null,
                ),
            )
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
        }

    @Test
    fun manualAddEditAndDeleteUseSharedValidation() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)

            val reversed =
                fixture.coordinator.saveManualInterval(
                    taskId = task.id,
                    startLocal = LocalDateTime.of(2026, 7, 25, 11, 0),
                    stopLocal = LocalDateTime.of(2026, 7, 25, 10, 0),
                )
            assertTrue(
                (reversed as ManualIntervalOperationResult.Invalid)
                    .errors
                    .contains(ManualIntervalValidationError.START_MUST_PRECEDE_STOP),
            )

            val first =
                fixture.coordinator.saveManualInterval(
                    taskId = task.id,
                    startLocal = LocalDateTime.of(2026, 7, 25, 9, 0),
                    stopLocal = LocalDateTime.of(2026, 7, 25, 10, 0),
                ) as ManualIntervalOperationResult.Saved
            val overlap =
                fixture.coordinator.saveManualInterval(
                    taskId = task.id,
                    startLocal = LocalDateTime.of(2026, 7, 25, 9, 30),
                    stopLocal = LocalDateTime.of(2026, 7, 25, 10, 30),
                )
            assertTrue(overlap is ManualIntervalOperationResult.Invalid)

            val edited =
                fixture.coordinator.saveManualInterval(
                    taskId = task.id,
                    editingIntervalId = first.interval.id,
                    startLocal = LocalDateTime.of(2026, 7, 25, 13, 0),
                    stopLocal = LocalDateTime.of(2026, 7, 25, 14, 30),
                )
            assertTrue(edited is ManualIntervalOperationResult.Saved)
            assertEquals(
                ManualIntervalOperationResult.Deleted,
                fixture.coordinator.deleteInterval(task.id, first.interval.id),
            )
            assertTrue(fixture.tasks.readIntervalsForOverlapValidation(task.id).isEmpty())
        }

    @Test
    fun deleteSelectedDailyTaskClearsSelectionAndPreservesSeriesSibling() =
        runTest {
            val fixture = Fixture()
            val selected = fixture.addTask(TODAY)
            fixture.selectionCoordinator.selectTask(selected.id)
            val sibling =
                fixture.tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = selected.clientId,
                        description = selected.description,
                        hardwareSoftwarePurchases =
                            selected.hardwareSoftwarePurchases,
                        workDate = TODAY.plusDays(1),
                        zoneId = ZONE,
                        seriesId = selected.seriesId,
                    ),
                )

            assertEquals(
                DeleteTaskOperationResult.Deleted,
                fixture.coordinator.deleteTask(selected.id),
            )
            assertNull(fixture.selection.readSelection())
            assertEquals(
                sibling,
                fixture.tasks.readTaskWithClient(sibling.id)?.task,
            )
        }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val selection = FakeSelectedTaskRepository()
        private val clock = FakeUtcClock(NOW)
        private val zone = FakeZoneIdProvider(ZONE)
        private val dateProvider = CurrentDateProvider(clock, zone)
        val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selection,
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

        suspend fun addTask(date: LocalDate) =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Task",
                    hardwareSoftwarePurchases = "Equipment",
                    workDate = date,
                    zoneId = ZONE,
                ),
            )
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 25)
        val NOW: Instant = Instant.parse("2026-07-25T16:00:00Z")
    }
}
