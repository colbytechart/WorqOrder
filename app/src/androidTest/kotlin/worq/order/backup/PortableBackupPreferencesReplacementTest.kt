package worq.order.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableBackupPreferencesReplacementTest {
    @Test
    fun targetApplyIsExactAndRollbackRestoresEveryLocalPreference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "portable-preferences-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("default_export_destination")] = "XLSX"
                preferences[stringPreferencesKey("google_account_id")] = "must-be-cleared"
                preferences[stringPreferencesKey("google_spreadsheet_id")] = "must-be-cleared"
                preferences[booleanPreferencesKey("automatic_google_export_enabled")] = true
            }
            val replacement = PortableBackupPreferencesReplacement(dataStore)
            val original = replacement.captureLocalSnapshot()
            val data = emptyPortableData()

            replacement.applyTarget(data, "XLSX", "a".repeat(32))

            assertTrue(replacement.matchesTarget(data, "XLSX", "a".repeat(32)))
            val replaced = replacement.captureLocalSnapshot()
            assertFalse(replaced.entries.any { it.name.startsWith("google_") })
            assertEquals(
                "false",
                replaced.entries.single { it.name == "automatic_google_export_enabled" }.value,
            )
            assertEquals(
                "XLSX",
                replaced.entries.single { it.name == "default_export_destination" }.value,
            )
            assertEquals(
                "a".repeat(32),
                replaced.entries.single { it.name == "export_origin_id" }.value,
            )

            replacement.restore(original)
            assertTrue(replacement.matches(original))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun emptyPortableData(): PortableBackupDataV1 =
        PortableBackupDataV1(
            dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
            clients = emptyList(),
            consultants = emptyList(),
            tags = emptyList(),
            tasks = emptyList(),
            settings =
                PortableBackupSettingsV1(
                    themeMode = "DARK",
                    timeZoneMode = "DEVICE",
                    manualZoneId = null,
                    defaultExportDestination = "GOOGLE_SHEETS",
                    lastExportAttempt = null,
                    selectedConsultantId = null,
                    landscapeHandedness = "LEFT_HANDED",
                ),
            selection = null,
        )
}
