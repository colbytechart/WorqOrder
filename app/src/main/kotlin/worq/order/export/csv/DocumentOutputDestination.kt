package worq.order.export.csv

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DocumentWriteResult {
    data object Success : DocumentWriteResult

    data class Failed(
        val partialDocumentMayRemain: Boolean,
    ) : DocumentWriteResult
}

fun interface DocumentOutputDestination {
    suspend fun write(
        documentUri: String,
        contents: String,
    ): DocumentWriteResult
}

class AndroidDocumentOutputDestination(
    private val contentResolver: ContentResolver,
) : DocumentOutputDestination {
    override suspend fun write(
        documentUri: String,
        contents: String,
    ): DocumentWriteResult =
        withContext(Dispatchers.IO) {
            val uri = documentUri.toUri()
            try {
                val stream =
                    contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Document provider returned no output stream")
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(contents)
                    writer.flush()
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
