package worq.order.backup

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens a user-selected document only long enough to stage and fully validate it privately. */
fun interface PortableBackupImportDocumentStager {
    suspend fun stage(documentUri: String): PortableBackupImportStageResult
}

class AndroidPortableBackupImportDocumentStager(
    private val contentResolver: ContentResolver,
    private val replacementCoordinator: PortableBackupReplacementCoordinator,
) : PortableBackupImportDocumentStager {
    override suspend fun stage(documentUri: String): PortableBackupImportStageResult =
        withContext(Dispatchers.IO) {
            val uri = documentUri.toUri()
            if (!isPortableBackupDocumentUri(uri)) {
                return@withContext PortableBackupImportStageResult.InvalidArchive
            }
            try {
                contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                    replacementCoordinator.stageImport(input)
                } ?: PortableBackupImportStageResult.StorageFailure
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                PortableBackupImportStageResult.StorageFailure
            } catch (_: SecurityException) {
                PortableBackupImportStageResult.StorageFailure
            }
        }
}
