package worq.order.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import worq.order.data.ExportOriginIdGenerator
import worq.order.data.ExportOriginRepository
import worq.order.data.ExportOriginState

/**
 * Stores the non-portable identity used by later Google export compatibility work. A malformed
 * stored value fails closed rather than silently changing row ownership.
 */
class PreferencesExportOriginRepository(
    private val dataStore: DataStore<Preferences>,
    private val idGenerator: ExportOriginIdGenerator = UuidExportOriginIdGenerator,
) : ExportOriginRepository {
    override suspend fun readOrCreate(): ExportOriginState {
        var state: ExportOriginState? = null
        dataStore.edit { preferences ->
            val stored = preferences[EXPORT_ORIGIN_ID]
            val originId =
                when {
                    stored == null -> generateOriginId()
                    isValidOriginId(stored) -> stored
                    else -> throw ExportOriginStorageException.InvalidStoredOrigin
                }
            val adoptionAllowed = preferences[LEGACY_V1_ADOPTION_ALLOWED] ?: true
            preferences[EXPORT_ORIGIN_ID] = originId
            preferences[LEGACY_V1_ADOPTION_ALLOWED] = adoptionAllowed
            state = ExportOriginState(originId, adoptionAllowed)
        }
        return requireNotNull(state)
    }

    override suspend fun rotateAfterPortableImport(): ExportOriginState {
        var state: ExportOriginState? = null
        dataStore.edit { preferences ->
            val previousOrigin = preferences[EXPORT_ORIGIN_ID]
            if (previousOrigin != null && !isValidOriginId(previousOrigin)) {
                throw ExportOriginStorageException.InvalidStoredOrigin
            }
            val originId = generateOriginId()
            if (originId == previousOrigin) {
                throw ExportOriginStorageException.RotationDidNotChangeOrigin
            }
            preferences[EXPORT_ORIGIN_ID] = originId
            preferences[LEGACY_V1_ADOPTION_ALLOWED] = false
            state = ExportOriginState(originId, legacyV1AdoptionAllowed = false)
        }
        return requireNotNull(state)
    }

    override suspend fun markLegacyV1AdoptionComplete() {
        dataStore.edit { preferences ->
            val stored = preferences[EXPORT_ORIGIN_ID]
            when {
                stored == null -> preferences[EXPORT_ORIGIN_ID] = generateOriginId()
                isValidOriginId(stored) -> Unit
                else -> throw ExportOriginStorageException.InvalidStoredOrigin
            }
            preferences[LEGACY_V1_ADOPTION_ALLOWED] = false
        }
    }

    private fun generateOriginId(): String =
        idGenerator.newOriginId().also { candidate ->
            require(isValidOriginId(candidate)) {
                "Export origin generator produced an invalid identifier"
            }
        }

    private fun isValidOriginId(value: String): Boolean =
        ORIGIN_ID_PATTERN.matches(value)

    private companion object {
        val EXPORT_ORIGIN_ID = stringPreferencesKey("export_origin_id")
        val LEGACY_V1_ADOPTION_ALLOWED = booleanPreferencesKey("export_origin_legacy_v1_allowed")
        val ORIGIN_ID_PATTERN = Regex("^[a-z0-9]{32}$")
    }
}

object UuidExportOriginIdGenerator : ExportOriginIdGenerator {
    override fun newOriginId(): String = UUID.randomUUID().toString().replace("-", "")
}

sealed class ExportOriginStorageException(
    message: String,
) : IllegalStateException(message) {
    data object InvalidStoredOrigin :
        ExportOriginStorageException("Stored export origin is malformed")

    data object RotationDidNotChangeOrigin :
        ExportOriginStorageException("Export origin rotation did not create a new identity")
}
