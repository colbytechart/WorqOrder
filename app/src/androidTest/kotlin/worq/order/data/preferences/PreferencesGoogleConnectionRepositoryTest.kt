package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.GoogleAccountHint
import worq.order.data.GoogleSpreadsheetConnection

@RunWith(AndroidJUnit4::class)
class PreferencesGoogleConnectionRepositoryTest {
    @Test
    fun safeMetadataPersistsAndSignOutAlsoDisconnectsSpreadsheet() {
        runBlocking {
            val context =
                InstrumentationRegistry.getInstrumentation().targetContext
            val file =
                File(
                    context.filesDir,
                    "google-connection.preferences_pb",
                ).also(File::delete)
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            var repository =
                PreferencesGoogleConnectionRepository(
                    dataStore,
                )

            assertEquals(
                GoogleSpreadsheetConnection(),
                repository.readConnection(),
            )
            repository.saveSignedInAccount(
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                ),
            )
            val validatedAt = Instant.parse("2026-07-26T14:00:00Z")
            repository.saveConnectedSpreadsheet(
                spreadsheetId = SPREADSHEET_ID,
                spreadsheetTitle = "Work Log",
                validatedAt = validatedAt,
            )

            scope.cancel()
            scope.coroutineContext.job.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            repository =
                PreferencesGoogleConnectionRepository(
                    dataStore,
                )
            val restored = repository.readConnection()
            assertTrue(restored.isConnected)
            assertEquals(SPREADSHEET_ID, restored.spreadsheetId)
            assertEquals("Work Log", restored.spreadsheetTitle)
            assertEquals(validatedAt, restored.validatedAt)

            dataStore.edit { preferences ->
                preferences[
                    intPreferencesKey("google_connection_state_version")
                ] = 99
            }
            assertFalse(repository.readConnection().isConnected)

            repository.completeLocalSignOut()
            val signedOut = repository.readConnection()
            assertNull(signedOut.accountId)
            assertFalse(signedOut.hasSpreadsheetMetadata)
            assertFalse(signedOut.isConnected)
            assertNull(signedOut.spreadsheetId)
            assertNull(signedOut.spreadsheetTitle)
            assertNull(signedOut.validatedAt)

            repository.disconnectSpreadsheet()
            val disconnected = repository.readConnection()
            assertNull(disconnected.spreadsheetId)
            assertNull(disconnected.spreadsheetTitle)

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
