package worq.order.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.SelectedTaskState
import worq.order.model.DailyTask
import worq.order.model.WorkType
import worq.order.model.BillingStatus
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.timer.CurrentDateProvider

class SelectionCoordinatorTest {
    @Test
    fun selectionCanBeObservedClearedAndSurvivesCoordinatorRecreation() =
        runTest {
            val fixture = fixture()
            val task = fixture.addTask(TODAY, NEW_YORK)
            val firstCoordinator = fixture.coordinator()

            assertTrue(firstCoordinator.selectTask(task.id) is SelectTaskResult.Selected)
            assertEquals(task.id, firstCoordinator.observeSelectedTask().first()?.id)

            val recreatedCoordinator = fixture.coordinator()
            assertEquals(task.id, recreatedCoordinator.observeSelectedTask().first()?.id)

            recreatedCoordinator.clearSelection()
            assertNull(recreatedCoordinator.observeSelectedTask().first())
        }

    @Test
    fun dateChangeClearsSelectionWithoutCreatingDailyCopy() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            fixture.selection.select(
                source.selection(selectedOnDate = TODAY.minusDays(1)),
            )

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(SelectionReconciliationResult.IneligibleSelectionCleared, result)
            assertNull(fixture.selection.readSelection())
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
            assertEquals(source, fixture.tasks.readTaskWithClient(source.id)?.task)
        }

    @Test
    fun repeatedStaleSelectionReconciliationDoesNotCreateOrDuplicateTasks() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            fixture.selection.select(
                source.selection(selectedOnDate = TODAY.minusDays(1)),
            )

            assertEquals(
                SelectionReconciliationResult.IneligibleSelectionCleared,
                fixture.coordinator().reconcileForToday(),
            )
            assertEquals(
                SelectionReconciliationResult.NoSelection,
                fixture.coordinator().reconcileForToday(),
            )
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
            assertEquals(source, fixture.tasks.readTaskWithClient(source.id)?.task)
        }

    @Test
    fun sameSeriesTaskForTodayDoesNotOverrideSelectedRoomTask() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            val existing =
                fixture.tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = source.clientId,
                        description = "Existing current copy",
                        workDate = TODAY,
                        zoneId = NEW_YORK,
                        seriesId = source.seriesId,
                    ),
                )
            fixture.selection.select(
                source.selection(selectedOnDate = TODAY.minusDays(1)),
            )

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(SelectionReconciliationResult.IneligibleSelectionCleared, result)
            assertNull(fixture.selection.readSelection())
            assertEquals(
                listOf(existing.id),
                fixture.tasks.observeTasksForDate(TODAY).first().map { it.task.id },
            )
        }

    @Test
    fun dataStoreContextCannotMakeHistoricalRoomTaskEligible() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            fixture.selection.select(
                SelectedTaskState(
                    taskId = source.id,
                    seriesId = source.seriesId,
                    selectedOnDate = TODAY,
                    selectedInZone = NEW_YORK,
                ),
            )

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(SelectionReconciliationResult.IneligibleSelectionCleared, result)
            assertNull(fixture.selection.readSelection())
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
        }

    @Test
    fun futureTaskSelectionIsClearedDuringReconciliation() =
        runTest {
            val fixture = fixture()
            val future = fixture.addTask(TODAY.plusDays(3), NEW_YORK)
            fixture.selection.select(future.selection(selectedOnDate = TODAY))

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(
                SelectionReconciliationResult.IneligibleSelectionCleared,
                result,
            )
            assertNull(fixture.selection.readSelection())
        }

    @Test
    fun effectiveZoneChangeClearsSelectionWithoutCreatingCopy() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY, NEW_YORK)
            fixture.selection.select(source.selection(selectedOnDate = TODAY))
            fixture.zone.current = CHICAGO

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(SelectionReconciliationResult.IneligibleSelectionCleared, result)
            assertNull(fixture.selection.readSelection())
            assertEquals(
                listOf(source.id),
                fixture.tasks.observeTasksForDate(TODAY).first().map { it.task.id },
            )
        }

    @Test
    fun missingSelectedTaskClearsDanglingPersistence() =
        runTest {
            val fixture = fixture()
            fixture.selection.select(
                SelectedTaskState(
                    taskId = "missing",
                    seriesId = "series-missing",
                    selectedOnDate = TODAY.minusDays(1),
                    selectedInZone = NEW_YORK,
                ),
            )

            assertEquals(
                SelectionReconciliationResult.MissingSelectionCleared,
                fixture.coordinator().reconcileForToday(),
            )
            assertNull(fixture.selection.readSelection())
        }

    @Test
    fun dataStoreSeriesHintCannotOverrideSelectedRoomTask() =
        runTest {
            val fixture = fixture()
            val task = fixture.addTask(TODAY, NEW_YORK)
            fixture.selection.select(
                SelectedTaskState(
                    taskId = task.id,
                    seriesId = "stale-series-hint",
                    selectedOnDate = TODAY,
                    selectedInZone = NEW_YORK,
                ),
            )

            assertEquals(
                SelectionReconciliationResult.MissingSelectionCleared,
                fixture.coordinator().reconcileForToday(),
            )
            assertNull(fixture.selection.readSelection())
            assertEquals(task, fixture.tasks.readTaskWithClient(task.id)?.task)
        }

    @Test
    fun activeTimerPreventsSwitchingToAnotherTask() =
        runTest {
            val fixture = fixture()
            val running = fixture.addTask(TODAY, NEW_YORK)
            val other = fixture.addTask(TODAY, NEW_YORK, seriesId = "series-2")
            fixture.active.createActiveInterval(
                taskId = running.id,
                boundaryZoneId = NEW_YORK,
                start = NOW,
            )

            val result = fixture.coordinator().selectTask(other.id)

            assertEquals(
                SelectTaskResult.LockedByActiveTimer(running.id),
                result,
            )
        }

    @Test
    fun activeTimerRestoresAndLocksItsSelectionAfterProcessStateLoss() =
        runTest {
            val fixture = fixture()
            val running = fixture.addTask(TODAY, NEW_YORK)
            fixture.active.createActiveInterval(
                taskId = running.id,
                boundaryZoneId = NEW_YORK,
                start = NOW,
            )

            val reconciliation = fixture.coordinator().reconcileForToday()
            val clear = fixture.coordinator().clearSelection()

            assertEquals(
                SelectionReconciliationResult.ActiveTimerOwnsSelection(running.id),
                reconciliation,
            )
            assertEquals(running.id, fixture.selection.readSelection()?.taskId)
            assertEquals(
                ClearSelectionResult.LockedByActiveTimer(running.id),
                clear,
            )
            assertEquals(running.id, fixture.selection.readSelection()?.taskId)
        }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val selection = FakeSelectedTaskRepository()
        val clock = FakeUtcClock(NOW)
        val zone = FakeZoneIdProvider(NEW_YORK)
        val active = FakeActiveTimerRepository(tasks)

        fun coordinator() =
            SelectionCoordinator(
                selectedTaskRepository = selection,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = CurrentDateProvider(clock, zone),
                zoneIdProvider = zone,
            )

        suspend fun addTask(
            date: LocalDate,
            zoneId: ZoneId,
            seriesId: String = "series-1",
        ): DailyTask =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Current description",
                    hardwareSoftwarePurchases = "Current purchases",
                    employeeId = "employee-1",
                    employeeNameSnapshot = "Alex Rivera",
                    workType = WorkType.ON_SITE,
                    billingStatus = BillingStatus.DO_NOT_BILL,
                    mileage = "18.5",
                    workDate = date,
                    zoneId = zoneId,
                    seriesId = seriesId,
                ),
            )
    }

    private fun fixture() = Fixture()

    private fun DailyTask.selection(selectedOnDate: LocalDate) =
        SelectedTaskState(
            taskId = id,
            seriesId = seriesId,
            selectedOnDate = selectedOnDate,
            selectedInZone = zoneId,
        )

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val CHICAGO: ZoneId = ZoneId.of("America/Chicago")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
