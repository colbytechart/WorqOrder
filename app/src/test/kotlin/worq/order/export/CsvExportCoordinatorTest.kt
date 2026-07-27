package worq.order.export

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.export.csv.CsvSerializer
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.LiveTimerSession
import worq.order.timer.TimerOperationLock

class CsvExportCoordinatorTest {
    @Test
    fun preparationDoesNotMutateCompletedLocalData() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask()
            fixture.tasks.insertCompletedInterval(
                taskId = task.id,
                start = Instant.parse("2026-07-24T13:00:00Z"),
                stop = Instant.parse("2026-07-24T14:00:00Z"),
                wasManuallyEdited = false,
            )
            val before = fixture.tasks.readTasksWithIntervalsForDate(WORK_DATE)

            val result = fixture.coordinator.prepare(WORK_DATE)
            val after = fixture.tasks.readTasksWithIntervalsForDate(WORK_DATE)

            assertTrue(result is PrepareCsvExportResult.Ready)
            assertEquals(before, after)
            assertEquals(
                1,
                (result as PrepareCsvExportResult.Ready).export.dataRowCount,
            )
        }

    @Test
    fun runningTimerIsSnapshottedWithoutBeingStopped() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask()
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = ZONE,
                start = Instant.parse("2026-07-24T12:00:00Z"),
            )

            val result = fixture.coordinator.prepare(WORK_DATE)
            val activeAfter = fixture.active.readActiveTimerSnapshot()

            assertTrue(result is PrepareCsvExportResult.Ready)
            val csv = (result as PrepareCsvExportResult.Ready).export.contents
            assertTrue(csv.contains(",1,08:00,,01:00:00,01:00:00"))
            assertNotNull(activeAfter)
            assertNull(activeAfter?.interval?.stop)
        }

    @Test
    fun csvSerializesTheDestinationNeutralSnapshotWithoutChangingIt() =
        runTest {
            val fixture = Fixture()
            fixture.addTask()

            val snapshotResult = fixture.snapshotCoordinator.prepare(WORK_DATE)
            val csvResult = fixture.coordinator.prepare(WORK_DATE)

            assertTrue(snapshotResult is PrepareExportSnapshotResult.Ready)
            assertTrue(csvResult is PrepareCsvExportResult.Ready)
            val snapshot =
                (snapshotResult as PrepareExportSnapshotResult.Ready).snapshot
            assertEquals(2, snapshot.schemaVersion)
            assertEquals(
                CsvSerializer().serialize(snapshot),
                (csvResult as PrepareCsvExportResult.Ready).export.contents,
            )
        }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        private val selected = FakeSelectedTaskRepository()
        private val clock = FakeUtcClock(EXPORTED_AT)
        private val operationLock = TimerOperationLock()
        private val normalizer =
            ActiveTimerNormalizer(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selected,
                clock = clock,
                liveTimerSession = LiveTimerSession(FakeMonotonicTimeSource()),
                operationLock = operationLock,
            )
        val snapshotCoordinator =
            ExportSnapshotCoordinator(
                taskRepository = tasks,
                activeTimerNormalizer = normalizer,
                clock = clock,
                timerOperationLock = operationLock,
            )
        val coordinator = CsvExportCoordinator(snapshotCoordinator)

        suspend fun addTask() =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Task",
                    workDate = WORK_DATE,
                    zoneId = ZONE,
                ),
            )
    }

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 24)
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val EXPORTED_AT: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
