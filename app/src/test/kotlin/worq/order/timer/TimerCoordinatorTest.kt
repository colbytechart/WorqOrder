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
import worq.order.data.ManualIntervalPersistenceResult
import worq.order.data.NewDailyTask
import worq.order.data.SelectedTaskState
import worq.order.model.DailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider

class TimerCoordinatorTest {
    @Test
    fun startRequiresSelectionAndCurrentExactDateZoneTask() =
        runTest {
            val fixture = Fixture()

            assertEquals(StartTimerResult.NoSelectedTask, fixture.coordinator().start())

            val historical = fixture.addTask(TODAY.minusDays(1))
            fixture.select(historical)
            val historicalResult = fixture.coordinator().start()
            assertTrue(historicalResult is StartTimerResult.TaskNotEligibleToday)

            val future = fixture.addTask(TODAY.plusDays(1), seriesId = "series-future")
            fixture.select(future)
            val futureResult = fixture.coordinator().start()
            assertTrue(futureResult is StartTimerResult.TaskNotEligibleToday)

            val otherZone = fixture.addTask(TODAY, CHICAGO, seriesId = "series-zone")
            fixture.select(otherZone)
            val otherZoneResult = fixture.coordinator().start()
            assertTrue(otherZoneResult is StartTimerResult.TaskNotEligibleToday)
            assertNull(fixture.active.readActiveTimerSnapshot())
            assertTrue(
                listOf(historical, future, otherZone).all { task ->
                    fixture.tasks.readTaskWithIntervals(task.id)?.intervals?.isEmpty() == true
                },
            )
        }

    @Test
    fun successfulStartCreatesOpenIntervalWithoutPersistedStopwatchValue() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)

            val result = fixture.coordinator().start()

            assertTrue(result is StartTimerResult.Started)
            result as StartTimerResult.Started
            assertEquals(Duration.ZERO, result.initialDisplayTotal)
            assertNull(result.snapshot.interval.stop)
            assertEquals(task.id, result.snapshot.interval.taskId)
            assertEquals(NEW_YORK, result.snapshot.activeTimer.boundaryZoneId)
        }

    @Test
    fun startRejectsAnotherGloballyActiveTimer() =
        runTest {
            val fixture = Fixture()
            val first = fixture.addTask(TODAY)
            val second = fixture.addTask(TODAY, seriesId = "series-2")
            fixture.active.createActiveInterval(first.id, NEW_YORK, NOW)
            fixture.select(second)

            assertEquals(StartTimerResult.AlreadyActive, fixture.coordinator().start())
        }

    @Test
    fun stopWithoutActiveTimerIsSafe() =
        runTest {
            val fixture = Fixture()

            assertEquals(StopTimerResult.NoActiveTimer, fixture.coordinator().stop())
        }

    @Test
    fun repeatedStartCreatesSelectedSameDayCopyAndPreservesSourceTask() =
        runTest {
            val fixture = Fixture()
            val task1 = fixture.addTask(TODAY, seriesId = "series-1")
            val task2 = fixture.addTask(TODAY, seriesId = "series-2")

            fixture.setTime("2026-07-24T13:00:00Z")
            fixture.select(task1)
            assertTrue(fixture.coordinator().start() is StartTimerResult.Started)
            fixture.advance(Duration.ofHours(1))
            assertTrue(fixture.coordinator().stop() is StopTimerResult.Stopped)

            fixture.advance(Duration.ofMinutes(30))
            fixture.select(task2)
            assertTrue(fixture.coordinator().start() is StartTimerResult.Started)
            fixture.advance(Duration.ofMinutes(30))
            assertTrue(fixture.coordinator().stop() is StopTimerResult.Stopped)

            fixture.advance(Duration.ofHours(2))
            fixture.select(task1)
            val restarted = fixture.coordinator().start()
            assertTrue(restarted is StartTimerResult.Started)
            assertEquals(
                Duration.ZERO,
                (restarted as StartTimerResult.Started).initialDisplayTotal,
            )
            val repeatedTaskId = requireNotNull(fixture.selection.readSelection()).taskId
            assertTrue(repeatedTaskId != task1.id)
            val repeatedTask =
                requireNotNull(fixture.tasks.readTaskWithIntervals(repeatedTaskId))
                    .taskWithClient
                    .task
            assertEquals(task1.seriesId, repeatedTask.seriesId)
            assertEquals(task1.clientId, repeatedTask.clientId)
            assertEquals(task1.description, repeatedTask.description)
            assertEquals(task1.workDate, repeatedTask.workDate)
            assertEquals(task1.zoneId, repeatedTask.zoneId)
            fixture.advance(Duration.ofHours(1))
            assertTrue(fixture.coordinator().stop() is StopTimerResult.Stopped)

            val task1Intervals =
                requireNotNull(fixture.tasks.readTaskWithIntervals(task1.id)).intervals
            val repeatedTaskIntervals =
                requireNotNull(fixture.tasks.readTaskWithIntervals(repeatedTaskId)).intervals
            val task2Intervals =
                requireNotNull(fixture.tasks.readTaskWithIntervals(task2.id)).intervals
            assertEquals(
                listOf(
                    Instant.parse("2026-07-24T13:00:00Z") to
                        Instant.parse("2026-07-24T14:00:00Z"),
                ),
                task1Intervals.map { it.start to it.stop },
            )
            assertEquals(
                listOf(
                    Instant.parse("2026-07-24T17:00:00Z") to
                        Instant.parse("2026-07-24T18:00:00Z"),
                ),
                repeatedTaskIntervals.map { it.start to it.stop },
            )
            assertEquals(
                listOf(
                    Instant.parse("2026-07-24T14:30:00Z") to
                        Instant.parse("2026-07-24T15:00:00Z"),
                ),
                task2Intervals.map { it.start to it.stop },
            )
            assertEquals(
                Duration.ofHours(1).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task1.id),
            )
            assertEquals(
                Duration.ofHours(1).toMillis(),
                fixture.tasks.readCompletedDurationMillis(repeatedTaskId),
            )
            assertEquals(
                Duration.ofMinutes(30).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task2.id),
            )
        }

    @Test
    fun deletingSoleCompletedIntervalMakesOriginalTaskEligibleForFirstStart() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val saved =
                fixture.tasks.addManualInterval(
                    taskId = task.id,
                    start = NOW.minusSeconds(3_600),
                    stop = NOW.minusSeconds(1_800),
                )
            assertTrue(saved is ManualIntervalPersistenceResult.Saved)
            val intervalId = (saved as ManualIntervalPersistenceResult.Saved).interval.id
            assertEquals(
                ManualIntervalPersistenceResult.Deleted,
                fixture.tasks.deleteManualInterval(task.id, intervalId),
            )
            fixture.select(task)

            val started = fixture.coordinator().start()

            assertTrue(started is StartTimerResult.Started)
            started as StartTimerResult.Started
            assertEquals(task.id, started.snapshot.interval.taskId)
            assertEquals(task.id, fixture.selection.readSelection()?.taskId)
            assertEquals(1, fixture.tasks.observeTasksForDate(TODAY).first().size)
        }

    @Test
    fun normalizationSplitsAtMidnightAndIsIdempotent() =
        runTest {
            val fixture = Fixture()
            fixture.setTime("2026-07-25T03:30:00Z")
            val task = fixture.addTask(LocalDate.of(2026, 7, 24))
            fixture.select(task)
            assertTrue(fixture.coordinator().start() is StartTimerResult.Started)
            val evaluation = Instant.parse("2026-07-25T05:00:00Z")
            fixture.advance(Duration.between(fixture.clock.instant, evaluation))

            val first = fixture.normalizer().normalize(evaluation)
            val second = fixture.normalizer().normalize(evaluation)

            assertTrue(first is NormalizeTimerResult.Normalized)
            assertEquals(1, (first as NormalizeTimerResult.Normalized).splitCount)
            assertEquals(
                LocalDate.of(2026, 7, 25),
                first.snapshot.interval.start.atZone(NEW_YORK).toLocalDate(),
            )
            assertEquals(NormalizeTimerResult.NoChange, second)
            assertEquals(2, fixture.allSeriesIntervals("series-1").size)
        }

    @Test
    fun normalizationSplitsEveryMissedMidnight() =
        runTest {
            val fixture = Fixture()
            fixture.setTime("2026-07-25T03:30:00Z")
            val task = fixture.addTask(LocalDate.of(2026, 7, 24))
            fixture.select(task)
            fixture.coordinator().start()
            val evaluation = Instant.parse("2026-07-27T05:00:00Z")
            fixture.advance(Duration.between(fixture.clock.instant, evaluation))

            val result = fixture.normalizer().normalize(evaluation)

            assertTrue(result is NormalizeTimerResult.Normalized)
            assertEquals(3, (result as NormalizeTimerResult.Normalized).splitCount)
            assertEquals(4, fixture.allSeriesIntervals("series-1").size)
            assertEquals(
                LocalDate.of(2026, 7, 27),
                result.snapshot.interval.start.atZone(NEW_YORK).toLocalDate(),
            )
        }

    @Test
    fun clockAnomalyNeverWritesNegativeStopAndLeavesTimerRecoverable() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            fixture.coordinator().start()
            fixture.clock.instant = fixture.clock.instant.minusSeconds(1)

            val result = fixture.coordinator().stop()

            assertTrue(result is StopTimerResult.ClockChanged)
            assertTrue(fixture.active.readActiveTimerSnapshot() != null)
            assertNull(
                fixture.active.readActiveTimerSnapshot()?.interval?.stop,
            )
        }

    @Test
    fun backwardWallClockChangeStopsAtMonotonicProjectionWithoutDisplayJump() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            fixture.coordinator().start()
            fixture.monotonic.nanos += Duration.ofSeconds(45).toNanos()
            fixture.clock.instant = NOW.minusSeconds(10)

            val result = fixture.coordinator().stop()

            assertTrue(result is StopTimerResult.Stopped)
            assertEquals(
                NOW.plusSeconds(45),
                (result as StopTimerResult.Stopped).interval.stop,
            )
            assertEquals(
                Duration.ofSeconds(45).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task.id),
            )
        }

    @Test
    fun forwardWallClockChangeStopsAtMonotonicProjectionWithoutDisplayJump() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            fixture.coordinator().start()
            fixture.monotonic.nanos += Duration.ofSeconds(45).toNanos()
            fixture.clock.instant = NOW.plusSeconds(65)

            val result = fixture.coordinator().stop()

            assertTrue(result is StopTimerResult.Stopped)
            assertEquals(
                NOW.plusSeconds(45),
                (result as StopTimerResult.Stopped).interval.stop,
            )
            assertEquals(
                Duration.ofSeconds(45).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task.id),
            )
        }

    @Test
    fun forwardWallJumpCannotCreateFalseMidnightSplitWhileAnchorIsAlive() =
        runTest {
            val fixture = Fixture()
            val start = Instant.parse("2026-07-24T13:00:00Z")
            fixture.clock.instant = start
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            fixture.coordinator().start()
            fixture.monotonic.nanos += Duration.ofMinutes(30).toNanos()
            fixture.clock.instant = start.plus(Duration.ofDays(1))

            val result = fixture.normalizer().normalize()

            assertEquals(NormalizeTimerResult.NoChange, result)
            assertEquals(1, fixture.allSeriesIntervals(task.seriesId).size)
        }

    @Test
    fun concurrentStartAttemptsProduceExactlyOneGlobalWinner() =
        runTest {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.select(task)
            val coordinators =
                listOf(
                    fixture.coordinator(operationLock = TimerOperationLock()),
                    fixture.coordinator(operationLock = TimerOperationLock()),
                )

            val results =
                coroutineScope {
                    coordinators.map { coordinator ->
                        async { coordinator.start() }
                    }.awaitAll()
                }

            assertEquals(1, results.count { it is StartTimerResult.Started })
            assertEquals(1, results.count { it is StartTimerResult.AlreadyActive })
            assertEquals(1, fixture.tasks.readTaskWithIntervals(task.id)?.intervals?.size)
        }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val selection = FakeSelectedTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val clock = FakeUtcClock(NOW)
        val zone = FakeZoneIdProvider(NEW_YORK)
        val monotonic = FakeMonotonicTimeSource()
        private val sharedLock = TimerOperationLock()
        private val live = LiveTimerSession(monotonic)

        fun coordinator(
            operationLock: TimerOperationLock = sharedLock,
        ) = TimerCoordinator(
            activeTimerRepository = active,
            taskRepository = tasks,
            selectedTaskRepository = selection,
            clock = clock,
            zoneIdProvider = zone,
            liveTimerSession = live,
            operationLock = operationLock,
        )

        fun normalizer() =
            ActiveTimerNormalizer(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                liveTimerSession = live,
                operationLock = sharedLock,
            )

        suspend fun addTask(
            date: LocalDate,
            zoneId: ZoneId = NEW_YORK,
            seriesId: String = "series-1",
        ): DailyTask =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Task $seriesId",
                    workDate = date,
                    zoneId = zoneId,
                    seriesId = seriesId,
                ),
            )

        suspend fun select(task: DailyTask) {
            selection.select(
                SelectedTaskState(
                    taskId = task.id,
                    seriesId = task.seriesId,
                    selectedOnDate = clock.now().atZone(zone.current).toLocalDate(),
                    selectedInZone = zone.current,
                ),
            )
        }

        fun setTime(value: String) {
            clock.instant = Instant.parse(value)
        }

        fun advance(duration: Duration) {
            clock.instant = clock.instant.plus(duration)
            monotonic.nanos += duration.toNanos()
        }

        suspend fun allSeriesIntervals(seriesId: String) =
            listOf(
                LocalDate.of(2026, 7, 24),
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27),
            ).flatMap { date ->
                tasks.observeTasksForDate(date).first()
            }.filter { it.task.seriesId == seriesId }
                .flatMap { item ->
                    requireNotNull(tasks.readTaskWithIntervals(item.task.id)).intervals
                }
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val CHICAGO: ZoneId = ZoneId.of("America/Chicago")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
