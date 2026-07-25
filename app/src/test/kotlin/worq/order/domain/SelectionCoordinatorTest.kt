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
    fun actualDateRolloverCreatesExactSeriesDateZoneCopyWithCurrentMetadata() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            fixture.selection.select(
                source.selection(selectedOnDate = TODAY.minusDays(1)),
            )

            val result = fixture.coordinator().reconcileForToday()

            assertTrue(result is SelectionReconciliationResult.RolledOver)
            val copy = (result as SelectionReconciliationResult.RolledOver).task
            assertEquals(source.seriesId, copy.seriesId)
            assertEquals(source.clientId, copy.clientId)
            assertEquals(source.description, copy.description)
            assertEquals(
                source.hardwareSoftwarePurchases,
                copy.hardwareSoftwarePurchases,
            )
            assertEquals(TODAY, copy.workDate)
            assertEquals(NEW_YORK, copy.zoneId)
            assertEquals(copy.id, fixture.selection.readSelection()?.taskId)
        }

    @Test
    fun rolloverReusesExistingExactThreePartCopy() =
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

            assertTrue(result is SelectionReconciliationResult.RolledOver)
            assertEquals(
                existing.id,
                (result as SelectionReconciliationResult.RolledOver).task.id,
            )
            assertEquals(existing.id, fixture.selection.readSelection()?.taskId)
        }

    @Test
    fun sameSeriesDateUnderDifferentZoneIsNotRepurposed() =
        runTest {
            val fixture = fixture()
            val source = fixture.addTask(TODAY.minusDays(1), NEW_YORK)
            val otherZone =
                fixture.tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = source.clientId,
                        description = "Chicago copy",
                        workDate = TODAY,
                        zoneId = CHICAGO,
                        seriesId = source.seriesId,
                    ),
                )
            fixture.selection.select(
                source.selection(selectedOnDate = TODAY.minusDays(1)),
            )

            val result = fixture.coordinator().reconcileForToday()

            val rolled = (result as SelectionReconciliationResult.RolledOver).task
            assertEquals(NEW_YORK, rolled.zoneId)
            assertTrue(rolled.id != otherZone.id)
        }

    @Test
    fun historicalTaskSelectedTodayIsPreservedForViewingInsteadOfRolled() =
        runTest {
            val fixture = fixture()
            val historical = fixture.addTask(TODAY.minusDays(3), NEW_YORK)
            fixture.selection.select(historical.selection(selectedOnDate = TODAY))

            val result = fixture.coordinator().reconcileForToday()

            assertEquals(
                SelectionReconciliationResult.HistoricalSelectionPreserved,
                result,
            )
            assertEquals(historical.id, fixture.selection.readSelection()?.taskId)
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
