package worq.order.export.xlsx

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import worq.order.export.csv.DocumentWriteResult

fun interface BinaryDocumentOutputDestination {
    suspend fun write(
        documentUri: String,
        contents: ByteArray,
    ): DocumentWriteResult
}

class AndroidBinaryDocumentOutputDestination(
    private val contentResolver: ContentResolver,
) : BinaryDocumentOutputDestination {
    override suspend fun write(
        documentUri: String,
        contents: ByteArray,
    ): DocumentWriteResult =
        withContext(Dispatchers.IO) {
            val uri = documentUri.toUri()
            try {
                val stream =
                    contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Document provider returned no output stream")
                stream.buffered().use { output ->
                    output.write(contents)
                    output.flush()
                }
                DocumentWriteResult.Success
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val deleted =
                    runCatching {
                        contentResolver.delete(uri, null, null) > 0
                    }.getOrDefault(false)
                DocumentWriteResult.Failed(
                    partialDocumentMayRemain = !deleted,
                )
            }
        }
}
