package worq.order.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.BackupRestoreStatus

@RunWith(AndroidJUnit4::class)
class PreferencesBackupRestoreStatusRepositoryTest {
    @Test
    fun statusPersistsAcrossRepositoryRecreationAndCanBeCleared() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file =
                File(context.filesDir, "backup-restore-status.preferences_pb").also(File::delete)
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var repository =
                PreferencesBackupRestoreStatusRepository(
                    PreferenceDataStoreFactory.create(
                        scope = scope,
                        produceFile = { file },
                    ),
                )

            repository.setStatus(BackupRestoreStatus.IMPORT_SUCCEEDED)
            assertEquals(BackupRestoreStatus.IMPORT_SUCCEEDED, repository.observeStatus().first())

            scope.cancel()
            scope.coroutineContext.job.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository =
                PreferencesBackupRestoreStatusRepository(
                    PreferenceDataStoreFactory.create(
                        scope = scope,
                        produceFile = { file },
                    ),
                )
            assertEquals(BackupRestoreStatus.IMPORT_SUCCEEDED, repository.observeStatus().first())

            repository.clearStatus()
            assertEquals(null, repository.observeStatus().first())

            scope.cancel()
            scope.coroutineContext.job.join()
            file.delete()
        }
    }
}
