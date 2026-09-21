package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ExportOriginIdGenerator

@RunWith(AndroidJUnit4::class)
class PreferencesExportOriginRepositoryTest {
    @Test
    fun originPersistsThenRotatesAndPermanentlyBlocksLegacyAdoptionAfterImport() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.filesDir, "export-origin.preferences_pb").also(File::delete)
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var store =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            var repository =
                PreferencesExportOriginRepository(
                    dataStore = store,
                    idGenerator = QueueOriginGenerator(FIRST_ORIGIN, SECOND_ORIGIN),
                )

            assertEquals(
                FIRST_ORIGIN,
                repository.readOrCreate().originId,
            )
            assertEquals(
                FIRST_ORIGIN,
                repository.readOrCreate().originId,
            )
            assertEquals(
                true,
                repository.readOrCreate().legacyV1AdoptionAllowed,
            )
            repository.markLegacyV1AdoptionComplete()
            assertFalse(repository.readOrCreate().legacyV1AdoptionAllowed)
            assertEquals(
                SECOND_ORIGIN,
                repository.rotateAfterPortableImport().originId,
            )
            assertFalse(repository.readOrCreate().legacyV1AdoptionAllowed)

            scope.cancel()
            scope.coroutineContext.job.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            repository = PreferencesExportOriginRepository(store)
            assertEquals(SECOND_ORIGIN, repository.readOrCreate().originId)
            assertFalse(repository.readOrCreate().legacyV1AdoptionAllowed)

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }

    @Test
    fun malformedStoredOriginFailsClosedRatherThanSilentlyChangingGoogleRowOwnership() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.filesDir, "malformed-export-origin.preferences_pb").also(File::delete)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            store.edit { preferences ->
                preferences[stringPreferencesKey("export_origin_id")] = "not-a-valid-origin"
            }

            try {
                PreferencesExportOriginRepository(store).readOrCreate()
                fail("Malformed export origin must fail closed")
            } catch (_: ExportOriginStorageException.InvalidStoredOrigin) {
                // Expected: an importer must not silently assume another device's identity.
            } finally {
                scope.cancel()
                scope.coroutineContext.job.join()
                file.delete()
            }
        }
    }

    @Test
    fun rotationFailsClosedIfGeneratorRepeatsTheCurrentOrigin() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.filesDir, "repeated-export-origin.preferences_pb").also(File::delete)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            val repository =
                PreferencesExportOriginRepository(
                    dataStore = store,
                    idGenerator = QueueOriginGenerator(FIRST_ORIGIN, FIRST_ORIGIN),
                )
            repository.readOrCreate()

            try {
                repository.rotateAfterPortableImport()
                fail("Origin rotation must produce a different identity")
            } catch (_: ExportOriginStorageException.RotationDidNotChangeOrigin) {
                assertEquals(FIRST_ORIGIN, repository.readOrCreate().originId)
                assertEquals(true, repository.readOrCreate().legacyV1AdoptionAllowed)
            } finally {
                scope.cancel()
                scope.coroutineContext.job.join()
                file.delete()
            }
        }
    }

    private class QueueOriginGenerator(
        vararg ids: String,
    ) : ExportOriginIdGenerator {
        private val values = ArrayDeque(ids.toList())

        override fun newOriginId(): String = values.removeFirst()
    }

    private companion object {
        const val FIRST_ORIGIN = "0123456789abcdef0123456789abcdef"
        const val SECOND_ORIGIN = "fedcba9876543210fedcba9876543210"
    }
}
