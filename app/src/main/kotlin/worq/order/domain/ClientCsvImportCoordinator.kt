package worq.order.domain

import kotlinx.coroutines.CancellationException
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.ClientImportCandidate
import worq.order.data.ClientImportRepository
import worq.order.data.ClientNameValidationError
import worq.order.data.ClientNameValidationResult
import worq.order.data.ClientNameNormalizer

data class ClientCsvImportSummary(
    val addedCount: Int,
    val restoredCount: Int,
    val skippedCount: Int,
)

enum class ClientCsvImportFailure {
    UNSUPPORTED_FILE,
    FILE_TOO_LARGE,
    READ_FAILED,
    INVALID_UTF8,
    MALFORMED_CSV,
    TOO_MANY_RECORDS,
    TOO_MANY_CELLS,
    CELL_TOO_LARGE,
    CLIENT_NAME_TOO_LONG,
    STORAGE_FAILED,
}

sealed interface ClientCsvImportResult {
    data class Success(
        val summary: ClientCsvImportSummary,
    ) : ClientCsvImportResult

    data class Failed(
        val failure: ClientCsvImportFailure,
        val recordNumber: Int? = null,
        val columnNumber: Int? = null,
    ) : ClientCsvImportResult
}

class ClientCsvImportCoordinator(
    private val documentSource: ClientCsvDocumentSource,
    private val parser: ClientCsvParser,
    private val repository: ClientImportRepository,
) {
    suspend fun import(documentUri: String): ClientCsvImportResult {
        val document =
            when (val result = documentSource.read(documentUri)) {
                is ClientCsvDocumentReadResult.Success -> result.document
                ClientCsvDocumentReadResult.UnsupportedFile ->
                    return ClientCsvImportResult.Failed(
                        ClientCsvImportFailure.UNSUPPORTED_FILE,
                    )
                ClientCsvDocumentReadResult.TooLarge ->
                    return ClientCsvImportResult.Failed(
                        ClientCsvImportFailure.FILE_TOO_LARGE,
                    )
                ClientCsvDocumentReadResult.ReadFailed ->
                    return ClientCsvImportResult.Failed(
                        ClientCsvImportFailure.READ_FAILED,
                    )
            }
        val cells =
            when (val result = parser.parse(document.bytes)) {
                is ClientCsvParseResult.Success -> result.cells
                ClientCsvParseResult.TooLarge ->
                    return ClientCsvImportResult.Failed(ClientCsvImportFailure.FILE_TOO_LARGE)
                ClientCsvParseResult.InvalidUtf8 ->
                    return ClientCsvImportResult.Failed(ClientCsvImportFailure.INVALID_UTF8)
                is ClientCsvParseResult.MalformedCsv ->
                    return ClientCsvImportResult.Failed(
                        failure = ClientCsvImportFailure.MALFORMED_CSV,
                        recordNumber = result.recordNumber,
                        columnNumber = result.columnNumber,
                    )
                ClientCsvParseResult.TooManyRecords ->
                    return ClientCsvImportResult.Failed(ClientCsvImportFailure.TOO_MANY_RECORDS)
                ClientCsvParseResult.TooManyCells ->
                    return ClientCsvImportResult.Failed(ClientCsvImportFailure.TOO_MANY_CELLS)
                is ClientCsvParseResult.CellTooLarge ->
                    return ClientCsvImportResult.Failed(
                        failure = ClientCsvImportFailure.CELL_TOO_LARGE,
                        recordNumber = result.recordNumber,
                        columnNumber = result.columnNumber,
                    )
            }

        val uniqueCandidates = linkedMapOf<String, ClientImportCandidate>()
        var duplicateInFileCount = 0
        cells.forEach { cell ->
            when (val validation = ClientNameNormalizer.validate(cell.value)) {
                is ClientNameValidationResult.Valid -> {
                    val candidate =
                        ClientImportCandidate(
                            displayName = validation.name.displayName,
                            canonicalName = validation.name.canonicalName,
                        )
                    if (uniqueCandidates.putIfAbsent(candidate.canonicalName, candidate) != null) {
                        duplicateInFileCount += 1
                    }
                }
                is ClientNameValidationResult.Invalid ->
                    when (validation.error) {
                        ClientNameValidationError.BLANK -> Unit
                        ClientNameValidationError.TOO_LONG ->
                            return ClientCsvImportResult.Failed(
                                failure = ClientCsvImportFailure.CLIENT_NAME_TOO_LONG,
                                recordNumber = cell.recordNumber,
                                columnNumber = cell.columnNumber,
                            )
                    }
            }
        }

        val applied =
            try {
                repository.applyImport(uniqueCandidates.values.toList())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return ClientCsvImportResult.Failed(
                    ClientCsvImportFailure.STORAGE_FAILED,
                )
            }
        return ClientCsvImportResult.Success(
            ClientCsvImportSummary(
                addedCount = applied.addedCount,
                restoredCount = applied.restoredCount,
                skippedCount = applied.skippedActiveCount + duplicateInFileCount,
            ),
        )
    }
}
