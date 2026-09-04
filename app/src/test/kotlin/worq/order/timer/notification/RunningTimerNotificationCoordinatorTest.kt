package worq.order.timer.notification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.RunningTimerNotificationPreferences
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.timer.LiveTimerSession

class RunningTimerNotificationCoordinatorTest {
    @Test
    fun repeatedTaskNotificationUsesOnlyTheNewTaskActiveTime() =
        runTest {
            val fixture = fixture()
            fixture.tasks.insertCompletedInterval(
                taskId = fixture.taskId,
                start = Instant.parse("2026-08-08T13:00:00Z"),
                stop = Instant.parse("2026-08-08T14:00:00Z"),
                wasManuallyEdited = false,
            )
            fixture.start(Instant.parse("2026-08-08T14:30:00Z"))
            fixture.clock.instant = Instant.parse("2026-08-08T15:00:00Z")

            assertEquals(RunningTimerNotificationResult.Posted, fixture.coordinator.reconcile())

            val content = requireNotNull(fixture.gateway.lastContent)
            assertEquals("Client", content.clientName)
            assertEquals("Inspect roof", content.taskDescription)
            assertEquals(
                Instant.parse("2026-08-08T14:30:00Z").toEpochMilli(),
                content.chronometerBaseEpochMillis,
            )
        }

    @Test
    fun permissionAndChannelFailuresNeverChangeTheActiveTimer() =
        runTest {
            val fixture = fixture()
            val active = fixture.start(Instant.parse("2026-08-08T14:30:00Z"))
            fixture.gateway.availability =
                RunningTimerNotificationAvailability.RUNTIME_PERMISSION_REQUIRED
            assertEquals(
                RunningTimerNotificationResult.RuntimePermissionRequired,
                fixture.coordinator.reconcile(),
            )
            assertEquals(active, fixture.activeTimers.readActiveTimerSnapshot())

            fixture.gateway.availability =
                RunningTimerNotificationAvailability.DISABLED_IN_SETTINGS
            assertEquals(
                RunningTimerNotificationResult.DisabledInSettings,
                fixture.coordinator.reconcile(),
            )
            assertEquals(active, fixture.activeTimers.readActiveTimerSnapshot())
            assertEquals(2, fixture.gateway.cancelCount)
        }

    @Test
    fun dismissalSurvivesCoordinatorRecreationForOnlyThatInterval() =
        runTest {
            val fixture = fixture()
            val first = fixture.start(Instant.parse("2026-08-08T14:30:00Z"))
            fixture.coordinator.recordDismissal(first.interval.id)

            val recreated = fixture.newCoordinator()
            assertEquals(
                RunningTimerNotificationResult.DismissedForActiveInterval,
                recreated.reconcile(),
            )
            assertNull(fixture.gateway.lastContent)

            fixture.activeTimers.closeActiveInterval(Instant.parse("2026-08-08T15:00:00Z"))
            val second = fixture.start(Instant.parse("2026-08-08T16:00:00Z"))
            assertEquals(RunningTimerNotificationResult.Posted, recreated.reconcile())
            assertEquals(second.interval.id, fixture.gateway.lastContent?.intervalId)
            assertNull(fixture.preferences.dismissedIntervalId)
        }

    @Test
    fun stopCleanupCancelsAndClearsDismissalWithoutChangingRoomHistory() =
        runTest {
            val fixture = fixture()
            val active = fixture.start(Instant.parse("2026-08-08T14:30:00Z"))
            fixture.coordinator.recordDismissal(active.interval.id)
            fixture.activeTimers.closeActiveInterval(Instant.parse("2026-08-08T15:00:00Z"))

            fixture.coordinator.onTimerStopped()

            assertNull(fixture.preferences.dismissedIntervalId)
            assertEquals(1, fixture.gateway.cancelCount)
            assertEquals(1_800_000L, fixture.tasks.readCompletedDurationMillis(fixture.taskId))
        }

    @Test
    fun noActiveTimerCancelsAndClearsStalePresentationState() =
        runTest {
            val fixture = fixture()
            fixture.preferences.dismissedIntervalId = "stale"

            assertEquals(
                RunningTimerNotificationResult.NoActiveTimer,
                fixture.coordinator.reconcile(),
            )

            assertNull(fixture.preferences.dismissedIntervalId)
            assertEquals(1, fixture.gateway.cancelCount)
        }

    @Test
    fun backwardWallClockIsClampedInsteadOfCreatingNegativeElapsedTime() =
        runTest {
            val fixture = fixture()
            fixture.start(Instant.parse("2026-08-08T14:30:00Z"))
            fixture.clock.instant = Instant.parse("2026-08-08T14:00:00Z")

            assertEquals(RunningTimerNotificationResult.Posted, fixture.coordinator.reconcile())

            assertEquals(
                fixture.clock.instant.toEpochMilli(),
                fixture.gateway.lastContent?.chronometerBaseEpochMillis,
            )
        }

    private suspend fun fixture(): Fixture {
        val tasks = FakeTaskRepository()
        val task =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = "Inspect roof",
                    workDate = LocalDate.of(2026, 8, 8),
                    zoneId = ZoneId.of("America/New_York"),
                ),
            )
        return Fixture(tasks = tasks, taskId = task.id)
    }

    private class Fixture(
        val tasks: FakeTaskRepository,
        val taskId: String,
    ) {
        val activeTimers = FakeActiveTimerRepository(tasks)
        val preferences = FakePreferences()
        val gateway = FakeGateway()
        val clock = FakeUtcClock(Instant.parse("2026-08-08T14:30:00Z"))
        private val liveTimerSession = LiveTimerSession(FakeMonotonicTimeSource())
        val coordinator = newCoordinator()

        suspend fun start(start: Instant) =
            requireNotNull(
                (activeTimers.createActiveInterval(
                    taskId = taskId,
                    boundaryZoneId = ZoneId.of("America/New_York"),
                    start = start,
                ) as? worq.order.data.CreateActiveIntervalResult.Created)?.snapshot,
            )

        fun newCoordinator() =
            RunningTimerNotificationCoordinator(
                activeTimerRepository = activeTimers,
                taskRepository = tasks,
                preferences = preferences,
                gateway = gateway,
                clock = clock,
                liveTimerSession = liveTimerSession,
            )
    }

    private class FakePreferences : RunningTimerNotificationPreferences {
        var dismissedIntervalId: String? = null

        override suspend fun readDismissedIntervalId(): String? = dismissedIntervalId

        override suspend fun setDismissedIntervalId(intervalId: String) {
            dismissedIntervalId = intervalId
        }

        override suspend fun clearDismissedIntervalId() {
            dismissedIntervalId = null
        }
    }

    private class FakeGateway : RunningTimerNotificationGateway {
        var availability = RunningTimerNotificationAvailability.AVAILABLE
        var lastContent: RunningTimerNotificationContent? = null
        var cancelCount = 0
        var postSucceeds = true

        override fun availability(): RunningTimerNotificationAvailability = availability

        override fun post(content: RunningTimerNotificationContent): Boolean {
            lastContent = content
            return postSucceeds
        }

        override fun cancel() {
            cancelCount += 1
            lastContent = null
        }
    }
}
