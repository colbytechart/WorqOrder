package worq.order.data.document

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import worq.order.data.ClientCsvDocument
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.ClientCsvFilePolicy
import worq.order.data.ClientCsvImportLimits

class AndroidClientCsvDocumentSource(
    private val contentResolver: ContentResolver,
) : ClientCsvDocumentSource {
    override suspend fun read(documentUri: String): ClientCsvDocumentReadResult =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(documentUri)
                if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                    return@withContext ClientCsvDocumentReadResult.UnsupportedFile
                }
                val metadata = queryMetadata(uri)
                if (!ClientCsvFilePolicy.isSupported(metadata.displayName, metadata.mimeType)) {
                    return@withContext ClientCsvDocumentReadResult.UnsupportedFile
                }
                if (metadata.size != null && metadata.size > ClientCsvImportLimits.MAX_BYTES) {
                    return@withContext ClientCsvDocumentReadResult.TooLarge
                }
                val input =
                    contentResolver.openInputStream(uri)
                        ?: return@withContext ClientCsvDocumentReadResult.ReadFailed
                input.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > ClientCsvImportLimits.MAX_BYTES) {
                            return@withContext ClientCsvDocumentReadResult.TooLarge
                        }
                        output.write(buffer, 0, count)
                    }
                    ClientCsvDocumentReadResult.Success(
                        ClientCsvDocument(output.toByteArray()),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ClientCsvDocumentReadResult.ReadFailed
            }
        }

    private fun queryMetadata(uri: Uri): DocumentMetadata {
        var displayName: String? = null
        var size: Long? = null
        contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        return DocumentMetadata(
            displayName = displayName,
            mimeType = contentResolver.getType(uri),
            size = size,
        )
    }

    private data class DocumentMetadata(
        val displayName: String?,
        val mimeType: String?,
        val size: Long?,
    )
}
