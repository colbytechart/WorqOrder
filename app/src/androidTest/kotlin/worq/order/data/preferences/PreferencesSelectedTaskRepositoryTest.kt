package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
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
import worq.order.data.SelectedTaskState

@RunWith(AndroidJUnit4::class)
class PreferencesSelectedTaskRepositoryTest {
    @Test
    fun selectionSurvivesDataStoreAndRepositoryRecreationAndCanBeCleared() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.filesDir, "selection-persistence-test.preferences_pb")
            file.delete()
            val expected =
                SelectedTaskState(
                    taskId = "task-1",
                    seriesId = "series-1",
                    selectedOnDate = LocalDate.of(2026, 7, 24),
                    selectedInZone = ZoneId.of("America/New_York"),
                )

            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            PreferencesSelectedTaskRepository(dataStore).select(expected)
            scope.cancel()
            scope.coroutineContext.job.join()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            val recreated = PreferencesSelectedTaskRepository(dataStore)
            assertEquals(expected, recreated.readSelection())

            recreated.clear()
            assertNull(recreated.readSelection())
            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }
}
