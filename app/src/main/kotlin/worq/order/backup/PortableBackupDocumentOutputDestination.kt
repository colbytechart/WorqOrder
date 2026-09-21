package worq.order.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PortableBackupDocumentWriteResult {
    data class Success(
        val archive: PortableBackupArchiveWriteResult.Success,
    ) : PortableBackupDocumentWriteResult

    data class Failed(
        val reason: PortableBackupArchiveWriteFailure,
        val partialDocumentMayRemain: Boolean,
    ) : PortableBackupDocumentWriteResult
}

fun interface PortableBackupDocumentOutputDestination {
    suspend fun write(
        documentUri: String,
        backup: PreparedPortableBackup,
    ): PortableBackupDocumentWriteResult
}

/** Android scoped-storage output boundary for an owner-selected backup destination. */
class AndroidPortableBackupDocumentOutputDestination(
    private val contentResolver: ContentResolver,
    private val archiveWriter: PortableBackupArchiveWriter = PortableBackupArchiveWriter(),
) : PortableBackupDocumentOutputDestination {
    override suspend fun write(
        documentUri: String,
        backup: PreparedPortableBackup,
    ): PortableBackupDocumentWriteResult =
        withContext(Dispatchers.IO) {
            val uri = documentUri.toUri()
            if (!isPortableBackupDocumentUri(uri)) {
                return@withContext PortableBackupDocumentWriteResult.Failed(
                    reason = PortableBackupArchiveWriteFailure.OUTPUT,
                    partialDocumentMayRemain = false,
                )
            }
            try {
                val stream =
                    contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Document provider returned no output stream")
                stream.buffered().use { stream ->
                    when (val result = archiveWriter.write(backup, stream)) {
                        is PortableBackupArchiveWriteResult.Success ->
                            PortableBackupDocumentWriteResult.Success(result)
                        is PortableBackupArchiveWriteResult.Failed ->
                            PortableBackupDocumentWriteResult.Failed(
                                reason = result.reason,
                                partialDocumentMayRemain = !deletePartialDocument(uri),
                            )
                    }
                }
            } catch (error: CancellationException) {
                deletePartialDocument(uri)
                throw error
            } catch (_: Exception) {
                PortableBackupDocumentWriteResult.Failed(
                    reason = PortableBackupArchiveWriteFailure.OUTPUT,
                    partialDocumentMayRemain = !deletePartialDocument(uri),
                )
            }
        }

    private fun deletePartialDocument(uri: Uri): Boolean =
        runCatching { contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
}

internal fun isPortableBackupDocumentUri(uri: Uri): Boolean =
    uri.scheme == ContentResolver.SCHEME_CONTENT && !uri.authority.isNullOrBlank()
