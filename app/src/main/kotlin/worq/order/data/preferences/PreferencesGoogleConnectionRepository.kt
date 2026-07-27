package worq.order.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import worq.order.data.GoogleAccountHint
import worq.order.data.GoogleConnectionRepository
import worq.order.data.GoogleSpreadsheetConnection

class PreferencesGoogleConnectionRepository(
    private val dataStore: DataStore<Preferences>,
) : GoogleConnectionRepository {
    override fun observeConnection(): Flow<GoogleSpreadsheetConnection> =
        dataStore.data
            .catch { error ->
                if (error is IOException || error is CorruptionException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map(::connectionFromPreferences)

    override suspend fun readConnection(): GoogleSpreadsheetConnection =
        observeConnection().first()

    override suspend fun saveSignedInAccount(account: GoogleAccountHint) {
        val accountId = account.id.trim()
        require(accountId.isNotEmpty()) { "Google account hint ID must not be blank" }
        dataStore.edit { preferences ->
            val sameAccount =
                preferences[ACCOUNT_ID] == accountId &&
                    preferences[STATE_VERSION] ==
                    GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
            preferences[ACCOUNT_ID] = accountId
            account.displayName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { preferences[ACCOUNT_DISPLAY_NAME] = it }
                ?: preferences.remove(ACCOUNT_DISPLAY_NAME)
            if (!sameAccount) {
                preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] = false
            }
            preferences[STATE_VERSION] =
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        }
    }

    override suspend fun saveConnectedSpreadsheet(
        spreadsheetId: String,
        spreadsheetTitle: String,
        validatedAt: Instant,
    ) {
        val cleanId = spreadsheetId.trim()
        val cleanTitle = spreadsheetTitle.trim()
        require(cleanId.isNotEmpty()) { "Spreadsheet ID must not be blank" }
        require(cleanTitle.isNotEmpty()) { "Spreadsheet title must not be blank" }
        dataStore.edit { preferences ->
            require(!preferences[ACCOUNT_ID].isNullOrBlank()) {
                "A signed-in account hint is required before connecting a spreadsheet"
            }
            preferences[SPREADSHEET_ID] = cleanId
            preferences[SPREADSHEET_TITLE] = cleanTitle
            preferences[VALIDATED_AT_EPOCH_MILLIS] = validatedAt.toEpochMilli()
            preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] = true
            preferences[STATE_VERSION] =
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        }
    }

    override suspend fun markAuthorizationRequired() {
        dataStore.edit { preferences ->
            preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] = false
            preferences[STATE_VERSION] =
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        }
    }

    override suspend fun disconnectSpreadsheet() {
        dataStore.edit { preferences ->
            preferences.remove(SPREADSHEET_ID)
            preferences.remove(SPREADSHEET_TITLE)
            preferences.remove(VALIDATED_AT_EPOCH_MILLIS)
            preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] = false
            preferences[STATE_VERSION] =
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        }
    }

    override suspend fun completeLocalSignOut() {
        dataStore.edit { preferences ->
            preferences.remove(ACCOUNT_ID)
            preferences.remove(ACCOUNT_DISPLAY_NAME)
            preferences.remove(SPREADSHEET_ID)
            preferences.remove(SPREADSHEET_TITLE)
            preferences.remove(VALIDATED_AT_EPOCH_MILLIS)
            preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] = false
            preferences[STATE_VERSION] =
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        }
    }

    private fun connectionFromPreferences(
        preferences: Preferences,
    ): GoogleSpreadsheetConnection {
        val accountId = preferences[ACCOUNT_ID].cleanOrNull()
        val accountDisplayName = preferences[ACCOUNT_DISPLAY_NAME].cleanOrNull()
        val spreadsheetId = preferences[SPREADSHEET_ID].cleanOrNull()
        val spreadsheetTitle = preferences[SPREADSHEET_TITLE].cleanOrNull()
        val validatedAt =
            preferences[VALIDATED_AT_EPOCH_MILLIS]
                ?.let { epochMillis ->
                    runCatching { Instant.ofEpochMilli(epochMillis) }.getOrNull()
                }
        val storedStateVersion =
            preferences[STATE_VERSION]
                ?: GoogleSpreadsheetConnection.CURRENT_STATE_VERSION
        val isValidated =
            storedStateVersion ==
                GoogleSpreadsheetConnection.CURRENT_STATE_VERSION &&
                preferences[IS_VALIDATED_FOR_CURRENT_ACCOUNT] == true &&
                accountId != null &&
                spreadsheetId != null &&
                spreadsheetTitle != null &&
                validatedAt != null
        return GoogleSpreadsheetConnection(
            accountId = accountId,
            accountDisplayName = accountDisplayName,
            spreadsheetId = spreadsheetId,
            spreadsheetTitle = spreadsheetTitle,
            validatedAt = validatedAt,
            isValidatedForCurrentAccount = isValidated,
            stateVersion = storedStateVersion,
        )
    }

    private fun String?.cleanOrNull(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        val ACCOUNT_ID = stringPreferencesKey("google_account_id_hint")
        val ACCOUNT_DISPLAY_NAME =
            stringPreferencesKey("google_account_display_name_hint")
        val SPREADSHEET_ID = stringPreferencesKey("google_spreadsheet_id")
        val SPREADSHEET_TITLE = stringPreferencesKey("google_spreadsheet_title")
        val VALIDATED_AT_EPOCH_MILLIS =
            longPreferencesKey("google_spreadsheet_validated_at_epoch_ms")
        val IS_VALIDATED_FOR_CURRENT_ACCOUNT =
            booleanPreferencesKey("google_spreadsheet_valid_for_current_account")
        val STATE_VERSION = intPreferencesKey("google_connection_state_version")
    }
}
