package worq.order.domain

import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ClientCsvDocument
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.TagImportApplyResult
import worq.order.data.TagImportCandidate
import worq.order.data.TagImportRepository
import worq.order.model.TagCategory

class TagCsvImportCoordinatorTest {
    @Test
    fun importsEveryNonblankCellAndSupportsQuotedUnicodeCommaQuoteAndNewline() =
        runTest {
            val repository = RecordingTagImportRepository()
            repository.result = TagImportApplyResult(addedCount = 4, skippedExistingCount = 1)
            val csv =
                " Alpha ,\"Bravo, LLC\",alpha\r\n" +
                    "\"Quote \"\"tag\"\"\",\"Line\nTag\",\"\u4f7f\u7528\""

            val result = coordinator(csv, repository).import(
                category = TagCategory.DESCRIPTION,
                documentUri = "content://tags",
            )

            assertEquals(
                listOf("Alpha", "Bravo, LLC", "Quote \"tag\"", "Line Tag", "\u4f7f\u7528"),
                repository.candidates.map(TagImportCandidate::displayText),
            )
            assertEquals(TagCategory.DESCRIPTION, repository.category)
            assertEquals(
                TagCsvImportResult.Success(
                    TagCsvImportSummary(addedCount = 4, skippedCount = 2),
                ),
                result,
            )
        }

    @Test
    fun blankCellsAreIgnoredAndValidImportAppendsRatherThanReplacing() =
        runTest {
            val repository = RecordingTagImportRepository()
            repository.result = TagImportApplyResult(addedCount = 2, skippedExistingCount = 0)
            val result = coordinator(",Existing\n\nNew", repository).import(
                category = TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                documentUri = "content://tags",
            )

            assertEquals(listOf("Existing", "New"), repository.candidates.map(TagImportCandidate::displayText))
            assertEquals(
                TagCsvImportResult.Success(
                    TagCsvImportSummary(addedCount = 2, skippedCount = 0),
                ),
                result,
            )
        }

    @Test
    fun malformedInvalidUtf8UnsupportedAndTooLongInputsDoNotCallRepository() =
        runTest {
            val repository = RecordingTagImportRepository()
            val malformed = coordinator("\"unterminated", repository).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.MALFORMED_CSV,
                (malformed as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)

            val invalidUtf8 =
                TagCsvImportCoordinator(
                    documentSource = FixedSource(ClientCsvDocumentReadResult.Success(ClientCsvDocument(byteArrayOf(0xC3.toByte(), 0x28)))),
                    repository = repository,
                ).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.INVALID_UTF8,
                (invalidUtf8 as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)

            val unsupported =
                TagCsvImportCoordinator(
                    documentSource = FixedSource(ClientCsvDocumentReadResult.UnsupportedFile),
                    repository = repository,
                ).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.UNSUPPORTED_FILE,
                (unsupported as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)

            val readFailed =
                TagCsvImportCoordinator(
                    documentSource = FixedSource(ClientCsvDocumentReadResult.ReadFailed),
                    repository = repository,
                ).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.READ_FAILED,
                (readFailed as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)

            val tooLong = coordinator("x".repeat(401), repository).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.TAG_TOO_LONG,
                (tooLong as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)
        }

    @Test
    fun unexpectedDocumentSourceFailureIsReportedAsReadFailure() =
        runTest {
            val repository = RecordingTagImportRepository()
            val result =
                TagCsvImportCoordinator(
                    documentSource =
                        object : ClientCsvDocumentSource {
                            override suspend fun read(documentUri: String): ClientCsvDocumentReadResult =
                                error("provider failure")
                        },
                    repository = repository,
                ).import(TagCategory.DESCRIPTION, "uri")

            assertEquals(TagCsvImportResult.Failed(TagCsvImportFailure.READ_FAILED), result)
            assertFalse(repository.wasCalled)
        }

    @Test
    fun fileAndNonblankCellLimitsFailBeforeMutation() =
        runTest {
            val repository = RecordingTagImportRepository()
            val tooManyCells =
                (1..(TagCsvImportLimits.MAX_NONBLANK_CELLS + 1))
                    .joinToString(",") { "tag$it" }
            val tooManyResult = coordinator(tooManyCells, repository).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.TOO_MANY_TAGS,
                (tooManyResult as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)

            val tooLarge =
                TagCsvImportCoordinator(
                    documentSource = FixedSource(ClientCsvDocumentReadResult.TooLarge),
                    repository = repository,
                ).import(TagCategory.DESCRIPTION, "uri")
            assertEquals(
                TagCsvImportFailure.FILE_TOO_LARGE,
                (tooLarge as TagCsvImportResult.Failed).failure,
            )
            assertFalse(repository.wasCalled)
        }

    @Test
    fun storageFailureIsReportedWithoutPretendingImportSucceeded() =
        runTest {
            val repository = RecordingTagImportRepository().apply { throwOnApply = true }
            val result = coordinator("A", repository).import(TagCategory.DESCRIPTION, "uri")

            assertEquals(
                TagCsvImportResult.Failed(TagCsvImportFailure.STORAGE_FAILED),
                result,
            )
            assertTrue(repository.wasCalled)
        }

    @Test
    fun cancellationIsPropagatedInsteadOfMappedToStorageFailure() =
        runTest {
            val repository = object : TagImportRepository {
                override suspend fun applyImport(
                    category: TagCategory,
                    candidates: List<TagImportCandidate>,
                ): TagImportApplyResult = throw CancellationException("test cancellation")
            }
            var observed = false
            try {
                coordinator("A", repository).import(TagCategory.DESCRIPTION, "uri")
            } catch (_: CancellationException) {
                observed = true
            }
            assertTrue(observed)
        }

    private fun coordinator(
        csv: String,
        repository: TagImportRepository,
    ) =
        TagCsvImportCoordinator(
            documentSource =
                FixedSource(
                    ClientCsvDocumentReadResult.Success(
                        ClientCsvDocument(csv.toByteArray(StandardCharsets.UTF_8)),
                    ),
                ),
            repository = repository,
        )

    private class FixedSource(
        private val result: ClientCsvDocumentReadResult,
    ) : ClientCsvDocumentSource {
        override suspend fun read(documentUri: String): ClientCsvDocumentReadResult = result
    }

    private class RecordingTagImportRepository : TagImportRepository {
        var result = TagImportApplyResult(addedCount = 0, skippedExistingCount = 0)
        var throwOnApply = false
        var wasCalled = false
        var category: TagCategory? = null
        var candidates: List<TagImportCandidate> = emptyList()

        override suspend fun applyImport(
            category: TagCategory,
            candidates: List<TagImportCandidate>,
        ): TagImportApplyResult {
            wasCalled = true
            this.category = category
            this.candidates = candidates
            if (throwOnApply) error("storage")
            return result
        }
    }
}
