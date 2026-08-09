package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRunningTimerNotificationPreferencesTest {
    @Test
    fun dismissalPersistsAcrossRepositoryRecreationAndCanBeCleared() =
        runBlocking {
            val file = testFile()
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            var repository = PreferencesRunningTimerNotificationPreferences(dataStore)

            assertNull(repository.readDismissedIntervalId())
            repository.setDismissedIntervalId("interval-1")
            scope.cancel()
            scope.coroutineContext.job.join()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            repository = PreferencesRunningTimerNotificationPreferences(dataStore)
            assertEquals("interval-1", repository.readDismissedIntervalId())

            repository.clearDismissedIntervalId()
            assertNull(repository.readDismissedIntervalId())
            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
            Unit
        }

    private fun testFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "running-timer-notification-${System.nanoTime()}.preferences_pb")
            .also { it.delete() }
    }
}
