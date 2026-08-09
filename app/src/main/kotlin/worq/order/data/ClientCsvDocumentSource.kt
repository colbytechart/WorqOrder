package worq.order.data

import java.util.Locale

object ClientCsvImportLimits {
    const val MAX_BYTES = 1_048_576
    const val MAX_RECORDS = 10_000
    const val MAX_CELLS = 20_000
    const val MAX_RAW_CELL_UTF16_UNITS = 1_024
}

object ClientCsvFilePolicy {
    val acceptedMimeTypes: List<String> =
        listOf(
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "application/vnd.ms-excel",
        )

    fun isSupported(
        displayName: String?,
        mimeType: String?,
    ): Boolean =
        displayName
            ?.lowercase(Locale.ROOT)
            ?.endsWith(".csv") == true &&
            mimeType
                ?.lowercase(Locale.ROOT)
                ?.substringBefore(';')
                ?.trim() in acceptedMimeTypes
}

data class ClientCsvDocument(
    val bytes: ByteArray,
)

sealed interface ClientCsvDocumentReadResult {
    data class Success(
        val document: ClientCsvDocument,
    ) : ClientCsvDocumentReadResult

    data object UnsupportedFile : ClientCsvDocumentReadResult

    data object TooLarge : ClientCsvDocumentReadResult

    data object ReadFailed : ClientCsvDocumentReadResult
}

interface ClientCsvDocumentSource {
    suspend fun read(documentUri: String): ClientCsvDocumentReadResult
}
