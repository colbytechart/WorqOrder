package worq.order.data

/**
 * Presentation-only persistence for a user-dismissed running-timer notification.
 *
 * Room remains authoritative for whether a timer is active. This value only prevents an already
 * dismissed notification from being reposted for the same open interval.
 */
interface RunningTimerNotificationPreferences {
    suspend fun readDismissedIntervalId(): String?

    suspend fun setDismissedIntervalId(intervalId: String)

    suspend fun clearDismissedIntervalId()
}
