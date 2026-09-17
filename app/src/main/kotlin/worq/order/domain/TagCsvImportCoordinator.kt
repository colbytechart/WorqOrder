package worq.order.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.ClientCsvImportLimits
import worq.order.data.TagImportCandidate
import worq.order.data.TagImportRepository
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationError
import worq.order.data.TagTextValidationResult
import worq.order.model.TagCategory

object TagCsvImportLimits {
    const val MAX_NONBLANK_CELLS = 10_000
}

data class TagCsvImportSummary(
    val addedCount: Int,
    val skippedCount: Int,
)

enum class TagCsvImportFailure {
    UNSUPPORTED_FILE,
    FILE_TOO_LARGE,
    READ_FAILED,
    INVALID_UTF8,
    MALFORMED_CSV,
    TOO_MANY_TAGS,
    TAG_TOO_LONG,
    STORAGE_FAILED,
}

sealed interface TagCsvImportResult {
    data class Success(
        val summary: TagCsvImportSummary,
    ) : TagCsvImportResult

    data class Failed(
        val failure: TagCsvImportFailure,
        val recordNumber: Int? = null,
        val columnNumber: Int? = null,
    ) : TagCsvImportResult
}

/**
 * Validates every input cell before opening the single Room transaction. A failed read, parse, or
 * validation therefore cannot partially mutate a category catalog.
 */
class TagCsvImportCoordinator(
    private val documentSource: ClientCsvDocumentSource,
    private val repository: TagImportRepository,
) {
    suspend fun import(
        category: TagCategory,
        documentUri: String,
    ): TagCsvImportResult {
        val readResult =
            try {
                documentSource.read(documentUri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return TagCsvImportResult.Failed(TagCsvImportFailure.READ_FAILED)
            }
        val document =
            when (val result = readResult) {
                is ClientCsvDocumentReadResult.Success -> result.document
                ClientCsvDocumentReadResult.UnsupportedFile ->
                    return TagCsvImportResult.Failed(TagCsvImportFailure.UNSUPPORTED_FILE)
                ClientCsvDocumentReadResult.TooLarge ->
                    return TagCsvImportResult.Failed(TagCsvImportFailure.FILE_TOO_LARGE)
                ClientCsvDocumentReadResult.ReadFailed ->
                    return TagCsvImportResult.Failed(TagCsvImportFailure.READ_FAILED)
            }

        val candidates =
            withContext(Dispatchers.Default) {
                buildCandidates(document.bytes)
            }
        when (candidates) {
            is TagCandidateBuildResult.Failed ->
                return TagCsvImportResult.Failed(
                    failure = candidates.failure,
                    recordNumber = candidates.recordNumber,
                    columnNumber = candidates.columnNumber,
                )
            is TagCandidateBuildResult.Success -> {
                val applied =
                    try {
                        repository.applyImport(category, candidates.candidates)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        return TagCsvImportResult.Failed(TagCsvImportFailure.STORAGE_FAILED)
                    }
                return TagCsvImportResult.Success(
                    TagCsvImportSummary(
                        addedCount = applied.addedCount,
                        skippedCount = applied.skippedExistingCount + candidates.duplicateInFileCount,
                    ),
                )
            }
        }
    }

    private fun buildCandidates(bytes: ByteArray): TagCandidateBuildResult {
        val cells =
            when (
                val result =
                    ClientCsvParser(
                        maxRecords = null,
                        maxCells = TagCsvImportLimits.MAX_NONBLANK_CELLS,
                        maxRawCellUtf16Units = ClientCsvImportLimits.MAX_BYTES,
                        retainBlankCells = false,
                    ).parse(bytes)
            ) {
                is ClientCsvParseResult.Success -> result.cells
                ClientCsvParseResult.TooLarge ->
                    return TagCandidateBuildResult.Failed(TagCsvImportFailure.FILE_TOO_LARGE)
                ClientCsvParseResult.InvalidUtf8 ->
                    return TagCandidateBuildResult.Failed(TagCsvImportFailure.INVALID_UTF8)
                is ClientCsvParseResult.MalformedCsv ->
                    return TagCandidateBuildResult.Failed(
                        failure = TagCsvImportFailure.MALFORMED_CSV,
                        recordNumber = result.recordNumber,
                        columnNumber = result.columnNumber,
                    )
                ClientCsvParseResult.TooManyRecords,
                ClientCsvParseResult.TooManyCells,
                -> return TagCandidateBuildResult.Failed(TagCsvImportFailure.TOO_MANY_TAGS)
                is ClientCsvParseResult.CellTooLarge ->
                    return TagCandidateBuildResult.Failed(
                        failure = TagCsvImportFailure.TAG_TOO_LONG,
                        recordNumber = result.recordNumber,
                        columnNumber = result.columnNumber,
                    )
            }

        val uniqueCandidates = linkedMapOf<String, TagImportCandidate>()
        var duplicateInFileCount = 0
        cells.forEach { cell ->
            when (val validation = TagTextNormalizer.validate(cell.value)) {
                is TagTextValidationResult.Valid -> {
                    val candidate =
                        TagImportCandidate(
                            displayText = validation.text.displayText,
                            normalizedText = validation.text.normalizedText,
                        )
                    if (uniqueCandidates.putIfAbsent(candidate.normalizedText, candidate) != null) {
                        duplicateInFileCount += 1
                    }
                }
                is TagTextValidationResult.Invalid ->
                    when (validation.error) {
                        TagTextValidationError.BLANK -> Unit
                        TagTextValidationError.TOO_LONG ->
                            return TagCandidateBuildResult.Failed(
                                failure = TagCsvImportFailure.TAG_TOO_LONG,
                                recordNumber = cell.recordNumber,
                                columnNumber = cell.columnNumber,
                            )
                    }
            }
        }
        return TagCandidateBuildResult.Success(
            candidates = uniqueCandidates.values.toList(),
            duplicateInFileCount = duplicateInFileCount,
        )
    }
}

private sealed interface TagCandidateBuildResult {
    data class Success(
        val candidates: List<TagImportCandidate>,
        val duplicateInFileCount: Int,
    ) : TagCandidateBuildResult

    data class Failed(
        val failure: TagCsvImportFailure,
        val recordNumber: Int? = null,
        val columnNumber: Int? = null,
    ) : TagCandidateBuildResult
}
