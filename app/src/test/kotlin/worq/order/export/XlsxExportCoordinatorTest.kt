package worq.order.export

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.LiveTimerSession
import worq.order.timer.TimerOperationLock

class XlsxExportCoordinatorTest {
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

            assertTrue(result is PrepareXlsxExportResult.Ready)
            assertEquals(before, after)
            val export = (result as PrepareXlsxExportResult.Ready).export
            assertEquals(1, export.dataRowCount)
            assertTrue(export.contents.size > MINIMAL_PACKAGE_BYTES)
        }

    @Test
    fun runningTimerPreventsAnUnstableXlsxSnapshot() =
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

            assertEquals(PrepareXlsxExportResult.ActiveTimerChanged, result)
            assertTrue(activeAfter != null)
            assertEquals(null, activeAfter?.interval?.stop)
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
        private val snapshotCoordinator =
            ExportSnapshotCoordinator(
                taskRepository = tasks,
                activeTimerRepository = active,
                activeTimerNormalizer = normalizer,
                clock = clock,
                timerOperationLock = operationLock,
            )
        val coordinator = XlsxExportCoordinator(snapshotCoordinator)

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
        const val MINIMAL_PACKAGE_BYTES = 500
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 24)
        val ZONE: ZoneId = ZoneId.of("America/New_York")
        val EXPORTED_AT: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
