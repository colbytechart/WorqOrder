package worq.order.timer.notification

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.RunningTimerNotificationPreferences
import worq.order.data.TaskRepository
import worq.order.timer.LiveTimerSession
import worq.order.timer.UtcClock

enum class RunningTimerNotificationAvailability {
    AVAILABLE,
    RUNTIME_PERMISSION_REQUIRED,
    DISABLED_IN_SETTINGS,
}

data class RunningTimerNotificationContent(
    val intervalId: String,
    val clientName: String,
    val taskDescription: String,
    val chronometerBaseEpochMillis: Long,
)

interface RunningTimerNotificationGateway {
    fun availability(): RunningTimerNotificationAvailability

    fun post(content: RunningTimerNotificationContent): Boolean

    fun cancel()
}

sealed interface RunningTimerNotificationResult {
    data object Posted : RunningTimerNotificationResult

    data object NoActiveTimer : RunningTimerNotificationResult

    data object DismissedForActiveInterval : RunningTimerNotificationResult

    data object RuntimePermissionRequired : RunningTimerNotificationResult

    data object DisabledInSettings : RunningTimerNotificationResult

    data object Failed : RunningTimerNotificationResult
}

interface RunningTimerNotificationController {
    suspend fun reconcile(): RunningTimerNotificationResult

    suspend fun onTimerStopped()

    suspend fun recordDismissal(intervalId: String)
}

/**
 * Rebuilds notification presentation from Room without making that presentation authoritative.
 *
 * The only persisted presentation state is the interval ID dismissed by the user. No elapsed
 * value is written to Room or DataStore.
 */
class RunningTimerNotificationCoordinator(
    private val activeTimerRepository: ActiveTimerRepository,
    private val taskRepository: TaskRepository,
    private val preferences: RunningTimerNotificationPreferences,
    private val gateway: RunningTimerNotificationGateway,
    private val clock: UtcClock,
    private val liveTimerSession: LiveTimerSession,
    private val mutex: Mutex = Mutex(),
) : RunningTimerNotificationController {
    override suspend fun reconcile(): RunningTimerNotificationResult =
        mutex.withLock {
            val snapshot = activeTimerRepository.readActiveTimerSnapshot()
            if (snapshot == null) {
                preferences.clearDismissedIntervalId()
                gateway.cancel()
                return@withLock RunningTimerNotificationResult.NoActiveTimer
            }

            val intervalId = snapshot.interval.id
            val dismissedIntervalId = preferences.readDismissedIntervalId()
            if (dismissedIntervalId == intervalId) {
                gateway.cancel()
                return@withLock RunningTimerNotificationResult.DismissedForActiveInterval
            }
            if (dismissedIntervalId != null) {
                preferences.clearDismissedIntervalId()
            }

            when (gateway.availability()) {
                RunningTimerNotificationAvailability.RUNTIME_PERMISSION_REQUIRED -> {
                    gateway.cancel()
                    return@withLock RunningTimerNotificationResult.RuntimePermissionRequired
                }
                RunningTimerNotificationAvailability.DISABLED_IN_SETTINGS -> {
                    gateway.cancel()
                    return@withLock RunningTimerNotificationResult.DisabledInSettings
                }
                RunningTimerNotificationAvailability.AVAILABLE -> Unit
            }

            val task =
                taskRepository.readTaskWithClient(snapshot.activeTimer.taskId)
                    ?: run {
                        gateway.cancel()
                        return@withLock RunningTimerNotificationResult.Failed
                    }
            val now = clock.now()
            val completedDuration =
                Duration.ofMillis(
                    taskRepository.readCompletedDurationMillis(task.task.id).coerceAtLeast(0L),
                )
            val total =
                liveTimerSession.read(intervalId)?.total?.takeUnless { it.isNegative }
                    ?: completedDuration.plus(nonNegativeElapsed(snapshot.interval.start, now))
            val totalMillis = runCatching { total.toMillis() }.getOrDefault(Long.MAX_VALUE)
            val baseEpochMillis = subtractWithoutOverflow(now.toEpochMilli(), totalMillis)
            val posted =
                gateway.post(
                    RunningTimerNotificationContent(
                        intervalId = intervalId,
                        clientName = task.client.name,
                        taskDescription = task.task.description,
                        chronometerBaseEpochMillis = baseEpochMillis,
                    ),
                )
            if (posted) {
                RunningTimerNotificationResult.Posted
            } else {
                RunningTimerNotificationResult.Failed
            }
        }

    override suspend fun onTimerStopped() {
        mutex.withLock {
            gateway.cancel()
            preferences.clearDismissedIntervalId()
        }
    }

    override suspend fun recordDismissal(intervalId: String) {
        if (intervalId.isBlank()) return
        mutex.withLock {
            val activeIntervalId =
                activeTimerRepository.readActiveTimerSnapshot()?.interval?.id
            if (activeIntervalId == intervalId) {
                preferences.setDismissedIntervalId(intervalId)
            }
        }
    }

    private fun nonNegativeElapsed(start: Instant, end: Instant): Duration =
        Duration.between(start, end).let { elapsed ->
            if (elapsed.isNegative) Duration.ZERO else elapsed
        }

    private fun subtractWithoutOverflow(value: Long, amount: Long): Long =
        runCatching { Math.subtractExact(value, amount.coerceAtLeast(0L)) }
            .getOrDefault(Long.MIN_VALUE)
}
