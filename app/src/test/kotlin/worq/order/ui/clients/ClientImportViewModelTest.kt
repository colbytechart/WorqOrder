package worq.order.ui.clients

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.ClientCsvDocument
import worq.order.data.ClientCsvDocumentReadResult
import worq.order.data.ClientCsvDocumentSource
import worq.order.data.ClientImportApplyResult
import worq.order.data.ClientImportCandidate
import worq.order.data.ClientImportRepository
import worq.order.domain.ClientCsvImportCoordinator
import worq.order.domain.ClientCsvParser
import worq.order.testing.FakeClientRepository
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ClientImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pickerCancellationIsSilentAndSuccessPublishesCounts() =
        runTest(mainDispatcherRule.dispatcher) {
            val importRepository = FixedImportRepository()
            val viewModel = viewModel(importRepository)

            viewModel.onEvent(ClientManagementEvent.ImportCsvDocumentSelected(null))
            assertFalse(importRepository.wasCalled)
            assertNull(viewModel.uiState.value.importSummary)
            assertNull(viewModel.uiState.value.importFailure)

            viewModel.onEvent(
                ClientManagementEvent.ImportCsvDocumentSelected("content://clients"),
            )
            runCurrent()

            assertEquals(
                ClientImportSummaryUi(addedCount = 2, restoredCount = 1, skippedCount = 3),
                viewModel.uiState.value.importSummary,
            )
            assertFalse(viewModel.uiState.value.isImporting)
        }

    @Test
    fun importInProgressPreventsASecondDocumentRead() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val source = GatedSource(gate)
            val viewModel = viewModel(FixedImportRepository(), source)

            viewModel.onEvent(
                ClientManagementEvent.ImportCsvDocumentSelected("content://first"),
            )
            runCurrent()
            assertTrue(viewModel.uiState.value.isImporting)

            viewModel.onEvent(
                ClientManagementEvent.ImportCsvDocumentSelected("content://second"),
            )
            runCurrent()
            assertEquals(1, source.readCount)

            gate.complete(Unit)
            runCurrent()
            assertFalse(viewModel.uiState.value.isImporting)
        }

    private fun viewModel(
        importRepository: FixedImportRepository,
        source: ClientCsvDocumentSource =
            FixedSource(
                ClientCsvDocumentReadResult.Success(
                    ClientCsvDocument("Alpha".toByteArray()),
                ),
            ),
    ) = ClientManagementViewModel(
        clientRepository = FakeClientRepository(),
        clientCsvImportCoordinator =
            ClientCsvImportCoordinator(
                documentSource = source,
                parser = ClientCsvParser(),
                repository = importRepository,
            ),
    )

    private class FixedSource(
        private val result: ClientCsvDocumentReadResult,
    ) : ClientCsvDocumentSource {
        override suspend fun read(documentUri: String): ClientCsvDocumentReadResult = result
    }

    private class GatedSource(
        private val gate: CompletableDeferred<Unit>,
    ) : ClientCsvDocumentSource {
        var readCount = 0

        override suspend fun read(documentUri: String): ClientCsvDocumentReadResult {
            readCount += 1
            gate.await()
            return ClientCsvDocumentReadResult.Success(
                ClientCsvDocument("Alpha".toByteArray()),
            )
        }
    }

    private class FixedImportRepository : ClientImportRepository {
        var wasCalled = false

        override suspend fun applyImport(
            candidates: List<ClientImportCandidate>,
        ): ClientImportApplyResult {
            wasCalled = true
            return ClientImportApplyResult(
                addedCount = 2,
                restoredCount = 1,
                skippedActiveCount = 3,
            )
        }
    }
}
