package worq.order.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Recovery-only representation of every currently supported Preferences value. */
data class PortableBackupLocalPreferencesSnapshot(
    val entries: List<PortableBackupLocalPreferenceEntry>,
)

data class PortableBackupLocalPreferenceEntry(
    val name: String,
    val type: PortableBackupLocalPreferenceType,
    val value: String,
)

enum class PortableBackupLocalPreferenceType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
}

/**
 * Applies portable values in exactly one DataStore edit and captures/restores the local-only
 * values needed for rollback. This class is the only portable replacement code allowed to clear
 * Preferences. OAuth tokens are not stored by WorqOrder and are never represented here.
 */
class PortableBackupPreferencesReplacement(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun captureLocalSnapshot(): PortableBackupLocalPreferencesSnapshot =
        dataStore.data.first().toLocalSnapshot()

    suspend fun applyTarget(
        data: PortableBackupDataV1,
        preservedDefaultDestination: String,
        newExportOriginId: String,
    ) {
        require(PortableBackupValidator.validateData(data) is PortableBackupDataValidationResult.Valid)
        require(ORIGIN_ID.matches(newExportOriginId))
        require(preservedDefaultDestination in EXPORT_DESTINATIONS)
        val target = data.toTargetSnapshot(preservedDefaultDestination, newExportOriginId)

        dataStore.edit { preferences ->
            preferences.clear()
            target.entries.forEach { entry -> preferences.put(entry) }
        }
    }

    suspend fun matchesTarget(
        data: PortableBackupDataV1,
        preservedDefaultDestination: String,
        newExportOriginId: String,
    ): Boolean =
        captureLocalSnapshot() ==
            data.toTargetSnapshot(preservedDefaultDestination, newExportOriginId)

    suspend fun restore(snapshot: PortableBackupLocalPreferencesSnapshot) {
        require(snapshot.entries == snapshot.entries.distinctBy(PortableBackupLocalPreferenceEntry::name))
        dataStore.edit { preferences ->
            preferences.clear()
            snapshot.entries.forEach { entry -> preferences.put(entry) }
        }
    }

    suspend fun matches(snapshot: PortableBackupLocalPreferencesSnapshot): Boolean =
        captureLocalSnapshot() == snapshot

    private fun Preferences.toLocalSnapshot(): PortableBackupLocalPreferencesSnapshot =
        PortableBackupLocalPreferencesSnapshot(
            entries =
                asMap()
                    .mapNotNull { (key, value) ->
                        when (value) {
                            is String ->
                                PortableBackupLocalPreferenceEntry(
                                    name = key.name,
                                    type = PortableBackupLocalPreferenceType.STRING,
                                    value = value,
                                )
                            is Boolean ->
                                PortableBackupLocalPreferenceEntry(
                                    name = key.name,
                                    type = PortableBackupLocalPreferenceType.BOOLEAN,
                                    value = value.toString(),
                                )
                            is Int ->
                                PortableBackupLocalPreferenceEntry(
                                    name = key.name,
                                    type = PortableBackupLocalPreferenceType.INT,
                                    value = value.toString(),
                                )
                            is Long ->
                                PortableBackupLocalPreferenceEntry(
                                    name = key.name,
                                    type = PortableBackupLocalPreferenceType.LONG,
                                    value = value.toString(),
                                )
                            else -> null
                        }
                    }.sortedBy(PortableBackupLocalPreferenceEntry::name),
        )

    private fun androidx.datastore.preferences.core.MutablePreferences.put(
        entry: PortableBackupLocalPreferenceEntry,
    ) {
        when (entry.type) {
            PortableBackupLocalPreferenceType.STRING -> this[stringPreferencesKey(entry.name)] = entry.value
            PortableBackupLocalPreferenceType.BOOLEAN ->
                this[booleanPreferencesKey(entry.name)] = entry.value.toBooleanStrict()
            PortableBackupLocalPreferenceType.INT -> this[intPreferencesKey(entry.name)] = entry.value.toInt()
            PortableBackupLocalPreferenceType.LONG -> this[longPreferencesKey(entry.name)] = entry.value.toLong()
        }
    }

    private fun PortableBackupDataV1.toTargetSnapshot(
        preservedDefaultDestination: String,
        newExportOriginId: String,
    ): PortableBackupLocalPreferencesSnapshot =
        PortableBackupLocalPreferencesSnapshot(
            buildList {
                add(stringEntry(THEME_MODE.name, settings.themeMode))
                add(stringEntry(TIME_ZONE_MODE.name, settings.timeZoneMode))
                settings.manualZoneId?.let { add(stringEntry(MANUAL_ZONE_ID.name, it)) }
                add(stringEntry(DEFAULT_EXPORT_DESTINATION.name, preservedDefaultDestination))
                add(stringEntry(LANDSCAPE_HANDEDNESS.name, settings.landscapeHandedness))
                settings.selectedConsultantId?.let {
                    add(stringEntry(SELECTED_EMPLOYEE_ID.name, it))
                }
                settings.lastExportAttempt?.let { attempt ->
                    add(stringEntry(LAST_EXPORT_DESTINATION.name, attempt.destination))
                    add(longEntry(LAST_EXPORT_WORK_DATE.name, attempt.workDateEpochDay))
                    add(longEntry(LAST_EXPORT_ATTEMPTED_AT.name, attempt.attemptedAtEpochMs))
                    add(stringEntry(LAST_EXPORT_OUTCOME.name, attempt.outcome))
                    attempt.errorCategory?.let {
                        add(stringEntry(LAST_EXPORT_ERROR_CATEGORY.name, it))
                    }
                }
                selection?.let { selected ->
                    add(stringEntry(SELECTED_TASK_ID.name, selected.taskId))
                    add(stringEntry(SELECTED_SERIES_ID.name, selected.seriesId))
                    add(longEntry(SELECTED_ON_EPOCH_DAY.name, selected.selectedOnEpochDay))
                    add(stringEntry(SELECTED_IN_ZONE_ID.name, selected.selectedInZoneId))
                }

                // Portable replacement never retains Google connection/scheduling state.
                add(booleanEntry(AUTOMATIC_GOOGLE_EXPORT_ENABLED.name, false))
                add(stringEntry(EXPORT_ORIGIN_ID.name, newExportOriginId))
                add(booleanEntry(LEGACY_V1_ADOPTION_ALLOWED.name, false))
            }.sortedBy(PortableBackupLocalPreferenceEntry::name),
        )

    private fun stringEntry(name: String, value: String) =
        PortableBackupLocalPreferenceEntry(name, PortableBackupLocalPreferenceType.STRING, value)

    private fun booleanEntry(name: String, value: Boolean) =
        PortableBackupLocalPreferenceEntry(
            name,
            PortableBackupLocalPreferenceType.BOOLEAN,
            value.toString(),
        )

    private fun longEntry(name: String, value: Long) =
        PortableBackupLocalPreferenceEntry(
            name,
            PortableBackupLocalPreferenceType.LONG,
            value.toString(),
        )

    private companion object {
        val ORIGIN_ID = Regex("^[a-f0-9]{32}$")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TIME_ZONE_MODE = stringPreferencesKey("time_zone_mode")
        val MANUAL_ZONE_ID = stringPreferencesKey("manual_zone_id")
        val DEFAULT_EXPORT_DESTINATION = stringPreferencesKey("default_export_destination")
        val LAST_EXPORT_DESTINATION = stringPreferencesKey("last_export_destination")
        val LAST_EXPORT_WORK_DATE = longPreferencesKey("last_export_work_date_epoch_day")
        val LAST_EXPORT_ATTEMPTED_AT = longPreferencesKey("last_export_attempted_at_epoch_ms")
        val LAST_EXPORT_OUTCOME = stringPreferencesKey("last_export_outcome")
        val LAST_EXPORT_ERROR_CATEGORY = stringPreferencesKey("last_export_error_category")
        val SELECTED_EMPLOYEE_ID = stringPreferencesKey("selected_employee_id")
        val LANDSCAPE_HANDEDNESS = stringPreferencesKey("landscape_handedness")
        val AUTOMATIC_GOOGLE_EXPORT_ENABLED = booleanPreferencesKey("automatic_google_export_enabled")
        val SELECTED_TASK_ID = stringPreferencesKey("selected_task_id")
        val SELECTED_SERIES_ID = stringPreferencesKey("selected_series_id")
        val SELECTED_ON_EPOCH_DAY = longPreferencesKey("selected_on_epoch_day")
        val SELECTED_IN_ZONE_ID = stringPreferencesKey("selected_in_zone_id")
        val EXPORT_ORIGIN_ID = stringPreferencesKey("export_origin_id")
        val LEGACY_V1_ADOPTION_ALLOWED = booleanPreferencesKey("export_origin_legacy_v1_allowed")
        val EXPORT_DESTINATIONS = setOf("CSV", "XLSX", "GOOGLE_SHEETS")
    }
}
