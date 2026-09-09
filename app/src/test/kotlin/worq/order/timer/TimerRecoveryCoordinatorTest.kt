package worq.order.timer

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.SelectedTaskState
import worq.order.domain.SelectionCoordinator
import worq.order.domain.SelectionReconciliationResult
import worq.order.model.DailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider

class TimerRecoveryCoordinatorTest {
    @Test
    fun processRecoveryUsesRoomTimerAndRebuildsMonotonicDisplay() =
        runTest {
            val fixture = Fixture()
            val runningTask = fixture.addTask(TODAY, seriesId = "running-series")
            val staleTask = fixture.addTask(TODAY, seriesId = "stale-series")
            fixture.tasks.insertCompletedInterval(
                taskId = runningTask.id,
                start = NOW.minus(Duration.ofHours(1)),
                stop = NOW.minus(Duration.ofMinutes(45)),
                wasManuallyEdited = false,
            )
            fixture.select(staleTask)
            val persisted =
                fixture.active.createActiveInterval(
                    taskId = runningTask.id,
                    boundaryZoneId = NEW_YORK,
                    start = NOW.minus(Duration.ofMinutes(30)),
                )
            persisted as worq.order.data.CreateActiveIntervalResult.Created
            val intervalId = persisted.snapshot.interval.id
            val repeatedTaskId = persisted.startedTask.id
            assertTrue(repeatedTaskId != runningTask.id)

            val result = fixture.recovery.recover()

            assertTrue(result is TimerRecoveryResult.Recovered)
            assertEquals(
                SelectionReconciliationResult.ActiveTimerOwnsSelection(repeatedTaskId),
                (result as TimerRecoveryResult.Recovered).selectionResult,
            )
            assertEquals(repeatedTaskId, fixture.selection.readSelection()?.taskId)
            assertEquals(
                Duration.ofMinutes(30),
                fixture.live.read(intervalId)?.total,
            )

            fixture.monotonic.nanos += Duration.ofMinutes(5).toNanos()

            assertEquals(
                Duration.ofMinutes(35),
                fixture.live.read(intervalId)?.total,
            )
            val sourceStored =
                requireNotNull(
                    fixture.tasks.readTaskWithIntervals(runningTask.id),
                ).intervals
            val repeatedStored =
                requireNotNull(
                    fixture.tasks.readTaskWithIntervals(repeatedTaskId),
                ).intervals
            assertEquals(1, sourceStored.size)
            assertEquals(1, repeatedStored.size)
            assertNull(repeatedStored.single { it.id == intervalId }.stop)
        }

    @Test
    fun processRecoveryClearsStaleSelectionWithoutCreatingTask() =
        runTest {
            val fixture = Fixture()
            val historical = fixture.addTask(TODAY.minusDays(1))
            fixture.select(historical, selectedOnDate = historical.workDate)

            val result = fixture.recovery.recover()

            assertEquals(
                SelectionReconciliationResult.IneligibleSelectionCleared,
                (result as TimerRecoveryResult.Recovered).selectionResult,
            )
            assertNull(fixture.selection.readSelection())
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
            assertEquals(historical, fixture.tasks.readTaskWithClient(historical.id)?.task)
        }

    @Test
    fun concurrentResumeRecoveryClosesAtFirstMissedMidnightOnlyOnce() =
        runTest {
            val fixture =
                Fixture(
                    now = Instant.parse("2026-07-27T05:00:00Z"),
                )
            val task =
                fixture.addTask(
                    LocalDate.of(2026, 7, 24),
                    seriesId = "multi-day-series",
                )
            fixture.select(
                task,
                selectedOnDate = LocalDate.of(2026, 7, 24),
            )
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )

            val results =
                coroutineScope {
                    List(8) {
                        async { fixture.recovery.recover() }
                    }.awaitAll()
                }

            assertEquals(
                1,
                results.count {
                    it is TimerRecoveryResult.ClosedAtBoundary
                },
            )
            assertEquals(
                1,
                requireNotNull(fixture.tasks.readTaskWithIntervals(task.id)).intervals.size,
            )
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z"),
                requireNotNull(fixture.tasks.readTaskWithIntervals(task.id))
                    .intervals.single().stop,
            )
            assertNull(fixture.active.readActiveTimerSnapshot())
            assertNull(fixture.selection.readSelection())
        }

    @Test
    fun negativeWallRecoveryIsSurfacedThenCanReanchorAfterClockCorrection() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            val created =
                fixture.active.createActiveInterval(
                    taskId = task.id,
                    boundaryZoneId = NEW_YORK,
                    start = NOW,
                ) as worq.order.data.CreateActiveIntervalResult.Created
            fixture.clock.instant = NOW.minusSeconds(60)

            val anomalous = fixture.recovery.recover()

            assertTrue(anomalous is TimerRecoveryResult.ClockChanged)
            assertTrue(
                requireNotNull(fixture.live.read(created.snapshot.interval.id))
                    .clockAnomalyDetected,
            )
            assertNull(created.snapshot.interval.stop)
            assertTrue(fixture.active.readActiveTimerSnapshot() != null)

            fixture.clock.instant = NOW.plus(Duration.ofMinutes(10))
            val corrected = fixture.recovery.recover()

            assertTrue(corrected is TimerRecoveryResult.Recovered)
            val correctedDisplay =
                requireNotNull(fixture.live.read(created.snapshot.interval.id))
            assertEquals(Duration.ofMinutes(10), correctedDisplay.total)
            assertFalse(correctedDisplay.clockAnomalyDetected)
        }

    @Test
    fun liveWallJumpUsesMonotonicProjectionAndClosesAtRealMidnight() =
        runTest {
            val start = Instant.parse("2026-07-25T03:30:00Z")
            val fixture = Fixture(now = start)
            val task =
                fixture.addTask(
                    date = LocalDate.of(2026, 7, 24),
                    seriesId = "clock-jump-series",
                )
            fixture.select(task)
            assertTrue(fixture.timer.start() is StartTimerResult.Started)
            fixture.monotonic.nanos += Duration.ofMinutes(30).toNanos()
            fixture.clock.instant = start.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(30))

            val jumped = fixture.recovery.recover()

            assertTrue(jumped is TimerRecoveryResult.ClosedAtBoundary)
            assertNull(
                fixture.tasks.findLegacyContinuationTask(
                    seriesId = task.seriesId,
                    workDate = LocalDate.of(2026, 7, 26),
                    zoneId = NEW_YORK,
                ),
            )
            assertNull(fixture.active.readActiveTimerSnapshot())
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z"),
                requireNotNull(fixture.tasks.readTaskWithIntervals(task.id))
                    .intervals.single().stop,
            )

            fixture.clock.instant = start.plus(Duration.ofMinutes(30))
            val corrected = fixture.recovery.recover()

            assertEquals(
                0,
                (corrected as TimerRecoveryResult.Recovered).normalizedSplitCount,
            )
            assertNull(fixture.active.readActiveTimerSnapshot())
        }

    @Test
    fun deviceZoneChangeDoesNotChangeActiveSessionsPinnedCloseBoundary() =
        runTest {
            val fixture =
                Fixture(
                    now = Instant.parse("2026-07-25T05:00:00Z"),
                )
            val task =
                fixture.addTask(
                    date = LocalDate.of(2026, 7, 24),
                    seriesId = "pinned-zone-series",
                )
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )
            fixture.zone.current = LOS_ANGELES

            val result = fixture.recovery.recover()

            assertTrue(result is TimerRecoveryResult.ClosedAtBoundary)
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z"),
                requireNotNull(fixture.tasks.readTaskWithIntervals(task.id))
                    .intervals.single().stop,
            )
            assertNull(fixture.active.readActiveTimerSnapshot())
        }

    @Test
    fun concurrentResumeAndStopLeaveOneClosedIntervalAndNoActiveTimer() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            assertTrue(fixture.timer.start() is StartTimerResult.Started)
            fixture.advance(Duration.ofHours(1))

            val stopResult =
                coroutineScope {
                    val recovery = async { fixture.recovery.recover() }
                    val stop = async { fixture.timer.stop() }
                    recovery.await()
                    stop.await()
                }

            assertTrue(stopResult is StopTimerResult.Stopped)
            assertNull(fixture.active.readActiveTimerSnapshot())
            val intervals =
                requireNotNull(
                    fixture.tasks.readTaskWithIntervals(task.id),
                ).intervals
            assertEquals(1, intervals.size)
            assertEquals(
                Duration.ofHours(1),
                Duration.between(
                    intervals[0].start,
                    requireNotNull(intervals[0].stop),
                ),
            )
        }

    private class Fixture(
        now: Instant = NOW,
    ) {
        val tasks = FakeTaskRepository()
        val selection = FakeSelectedTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val clock = FakeUtcClock(now)
        val zone = FakeZoneIdProvider(NEW_YORK)
        val monotonic = FakeMonotonicTimeSource()
        val live = LiveTimerSession(monotonic)
        private val operationLock = TimerOperationLock()
        private val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selection,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = CurrentDateProvider(clock, zone),
                zoneIdProvider = zone,
            )
        private val normalizer =
            ActiveTimerNormalizer(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                liveTimerSession = live,
                operationLock = operationLock,
            )
        val recovery =
            TimerRecoveryCoordinator(
                activeTimerNormalizer = normalizer,
                selectionCoordinator = selectionCoordinator,
                zoneIdProvider = zone,
                clock = clock,
            )
        val timer =
            TimerCoordinator(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                zoneIdProvider = zone,
                liveTimerSession = live,
                operationLock = operationLock,
            )

        suspend fun addTask(
            date: LocalDate,
            seriesId: String = "series-1",
        ): DailyTask =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Task $seriesId",
                    workDate = date,
                    zoneId = NEW_YORK,
                    seriesId = seriesId,
                ),
            )

        suspend fun select(
            task: DailyTask,
            selectedOnDate: LocalDate = clock.now().atZone(zone.current).toLocalDate(),
        ) {
            selection.select(
                SelectedTaskState(
                    taskId = task.id,
                    seriesId = task.seriesId,
                    selectedOnDate = selectedOnDate,
                    selectedInZone = NEW_YORK,
                ),
            )
        }

        fun advance(duration: Duration) {
            clock.instant = clock.instant.plus(duration)
            monotonic.nanos += duration.toNanos()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-24T13:00:00Z")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val LOS_ANGELES: ZoneId = ZoneId.of("America/Los_Angeles")
    }
}
