package worq.order.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import worq.order.data.BackupRestoreStatus
import worq.order.data.BackupRestoreStatusRepository

class PreferencesBackupRestoreStatusRepository(
    private val dataStore: DataStore<Preferences>,
) : BackupRestoreStatusRepository {
    override fun observeStatus(): Flow<BackupRestoreStatus?> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw error
                }
            }.map { preferences ->
                preferences[STATUS]
                    ?.let { value -> BackupRestoreStatus.entries.firstOrNull { it.name == value } }
            }

    override suspend fun setStatus(status: BackupRestoreStatus) {
        dataStore.edit { preferences ->
            preferences[STATUS] = status.name
        }
    }

    override suspend fun clearStatus() {
        dataStore.edit { preferences ->
            preferences.remove(STATUS)
        }
    }

    private companion object {
        val STATUS = stringPreferencesKey("backup_restore_status")
    }
}
