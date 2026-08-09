package worq.order.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import worq.order.data.RunningTimerNotificationPreferences

class PreferencesRunningTimerNotificationPreferences(
    private val dataStore: DataStore<Preferences>,
) : RunningTimerNotificationPreferences {
    override suspend fun readDismissedIntervalId(): String? =
        dataStore.data.first()[DISMISSED_INTERVAL_ID]?.takeIf(String::isNotBlank)

    override suspend fun setDismissedIntervalId(intervalId: String) {
        require(intervalId.isNotBlank()) { "intervalId must not be blank" }
        dataStore.edit { preferences ->
            preferences[DISMISSED_INTERVAL_ID] = intervalId
        }
    }

    override suspend fun clearDismissedIntervalId() {
        dataStore.edit { preferences ->
            preferences.remove(DISMISSED_INTERVAL_ID)
        }
    }

    private companion object {
        val DISMISSED_INTERVAL_ID =
            stringPreferencesKey("dismissed_running_timer_notification_interval_id")
    }
}
