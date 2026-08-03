package worq.order.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.ClientCsvDocument
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.ClientImportApplyResult
import worq.order.data.ClientImportCandidate
import worq.order.data.ClientImportRepository
import worq.order.data.MAX_CLIENT_NAME_CODE_POINTS

class ClientCsvImportCoordinatorTest {
    @Test
    fun normalizesCellsCollapsesFileDuplicatesAndReportsAppliedCounts() =
        runTest {
            val repository = RecordingImportRepository()
            repository.result = ClientImportApplyResult(addedCount = 1, restoredCount = 1, skippedActiveCount = 1)
            val coordinator = coordinator(" Alpha ,\"alpha\"\n\nBeta\tGroup,Gamma", repository)

            val result = coordinator.import("content://clients")

            assertEquals(
                listOf("Alpha", "Beta Group", "Gamma"),
                repository.candidates.map(ClientImportCandidate::displayName),
            )
            assertEquals(
                ClientCsvImportResult.Success(
                    ClientCsvImportSummary(
                        addedCount = 1,
                        restoredCount = 1,
                        skippedCount = 2,
                    ),
                ),
                result,
            )
        }

    @Test
    fun invalidCellRejectsWholeFileBeforeRepositoryMutation() =
        runTest {
            val repository = RecordingImportRepository()
            val coordinator =
                coordinator(
                    "Valid,${"x".repeat(MAX_CLIENT_NAME_CODE_POINTS + 1)}",
                    repository,
                )

            val result = coordinator.import("content://clients")

            assertEquals(
                ClientCsvImportResult.Failed(
                    failure = ClientCsvImportFailure.CLIENT_NAME_TOO_LONG,
                    recordNumber = 1,
                    columnNumber = 2,
                ),
                result,
            )
            assertFalse(repository.wasCalled)
        }

    @Test
    fun mapsDocumentParserAndStorageFailuresWithoutPartialMutation() =
        runTest {
            val repository = RecordingImportRepository()
            val unsupported =
                ClientCsvImportCoordinator(
                    documentSource = FixedSource(ClientCsvDocumentReadResult.UnsupportedFile),
                    parser = ClientCsvParser(),
                    repository = repository,
                )
            assertEquals(
                ClientCsvImportResult.Failed(ClientCsvImportFailure.UNSUPPORTED_FILE),
                unsupported.import("content://clients"),
            )
            assertFalse(repository.wasCalled)

            val malformed = coordinator("\"broken", repository)
            assertTrue(
                (malformed.import("content://clients") as ClientCsvImportResult.Failed)
                    .failure == ClientCsvImportFailure.MALFORMED_CSV,
            )
            assertFalse(repository.wasCalled)

            repository.throwOnApply = true
            val storage = coordinator("Alpha", repository).import("content://clients")
            assertEquals(
                ClientCsvImportResult.Failed(ClientCsvImportFailure.STORAGE_FAILED),
                storage,
            )
        }

    @Test
    fun cancellationIsNeverMappedToAStorageFailure() =
        runTest {
            val repository = FixedImportRepositoryThatCancels()
            val coordinator =
                ClientCsvImportCoordinator(
                    documentSource =
                        FixedSource(
                            ClientCsvDocumentReadResult.Success(
                                ClientCsvDocument("Alpha".toByteArray()),
                            ),
                        ),
                    parser = ClientCsvParser(),
                    repository = repository,
                )

            var cancellationObserved = false
            try {
                coordinator.import("content://clients")
            } catch (_: CancellationException) {
                cancellationObserved = true
            }
            assertTrue(cancellationObserved)
        }

    private fun coordinator(
        csv: String,
        repository: RecordingImportRepository,
    ) = ClientCsvImportCoordinator(
        documentSource =
            FixedSource(
                ClientCsvDocumentReadResult.Success(
                    ClientCsvDocument(csv.toByteArray()),
                ),
            ),
        parser = ClientCsvParser(),
        repository = repository,
    )

    private class FixedSource(
        private val result: ClientCsvDocumentReadResult,
    ) : ClientCsvDocumentSource {
        override suspend fun read(documentUri: String): ClientCsvDocumentReadResult = result
    }

    private class RecordingImportRepository : ClientImportRepository {
        var result = ClientImportApplyResult(0, 0, 0)
        var throwOnApply = false
        var wasCalled = false
        var candidates: List<ClientImportCandidate> = emptyList()

        override suspend fun applyImport(
            candidates: List<ClientImportCandidate>,
        ): ClientImportApplyResult {
            wasCalled = true
            this.candidates = candidates
            if (throwOnApply) error("storage")
            return result
        }
    }

    private class FixedImportRepositoryThatCancels : ClientImportRepository {
        override suspend fun applyImport(
            candidates: List<ClientImportCandidate>,
        ): ClientImportApplyResult = throw CancellationException("test")
    }
}
