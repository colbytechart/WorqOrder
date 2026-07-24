package worq.order.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState

private const val PREFERENCES_FILE_NAME = "worqorder_preferences"

val Context.worqOrderPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_FILE_NAME,
)

class PreferencesSelectedTaskRepository(
    private val dataStore: DataStore<Preferences>,
) : SelectedTaskRepository {
    override fun observeSelection(): Flow<SelectedTaskState?> =
        dataStore.data.map(::selectionFromPreferences)

    override suspend fun readSelection(): SelectedTaskState? =
        selectionFromPreferences(dataStore.data.first())

    override suspend fun select(selection: SelectedTaskState) {
        require(selection.taskId.isNotBlank()) { "taskId must not be blank" }
        require(selection.seriesId.isNotBlank()) { "seriesId must not be blank" }
        dataStore.edit { preferences ->
            preferences[SELECTED_TASK_ID] = selection.taskId
            preferences[SELECTED_SERIES_ID] = selection.seriesId
            preferences[SELECTED_ON_EPOCH_DAY] = selection.selectedOnDate.toEpochDay()
            preferences[SELECTED_IN_ZONE_ID] = selection.selectedInZone.id
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(SELECTED_TASK_ID)
            preferences.remove(SELECTED_SERIES_ID)
            preferences.remove(SELECTED_ON_EPOCH_DAY)
            preferences.remove(SELECTED_IN_ZONE_ID)
        }
    }

    private fun selectionFromPreferences(
        preferences: Preferences,
    ): SelectedTaskState? {
        val taskId = preferences[SELECTED_TASK_ID] ?: return null
        val seriesId = preferences[SELECTED_SERIES_ID] ?: return null
        val selectedOnEpochDay = preferences[SELECTED_ON_EPOCH_DAY] ?: return null
        val selectedInZoneId = preferences[SELECTED_IN_ZONE_ID] ?: return null
        return runCatching {
            SelectedTaskState(
                taskId = taskId,
                seriesId = seriesId,
                selectedOnDate = LocalDate.ofEpochDay(selectedOnEpochDay),
                selectedInZone = ZoneId.of(selectedInZoneId),
            )
        }.getOrNull()
    }

    private companion object {
        val SELECTED_TASK_ID = stringPreferencesKey("selected_task_id")
        val SELECTED_SERIES_ID = stringPreferencesKey("selected_series_id")
        val SELECTED_ON_EPOCH_DAY = longPreferencesKey("selected_on_epoch_day")
        val SELECTED_IN_ZONE_ID = stringPreferencesKey("selected_in_zone_id")
    }
}
