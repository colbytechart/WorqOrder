package worq.order.ui.clients

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.MAX_CLIENT_NAME_CODE_POINTS
import worq.order.testing.FakeClientRepository
import worq.order.testing.FakeClientRepository.Companion.client
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ClientManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addValidClientNormalizesAndSortsActiveClients() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client("z", "Zulu"),
                        client("a", "Alpha"),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName(" \tBeta   Group\n"))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()

            assertEquals(
                listOf("Alpha", "Beta Group", "Zulu"),
                viewModel.uiState.value.activeClients.map(ClientItemUi::name),
            )
            assertEquals(
                "beta group",
                repository.currentClients
                    .single { it.name == "Beta Group" }
                    .canonicalName,
            )
            assertNull(viewModel.uiState.value.editor)
        }

    @Test
    fun blankAndOverlengthNamesShowFieldErrorsWithoutSaving() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeClientRepository()
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName(" \t "))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            assertEquals(
                ClientNameFieldError.BLANK,
                viewModel.uiState.value.editor?.fieldError,
            )

            viewModel.onEvent(
                ClientManagementEvent.EditName(
                    "a".repeat(MAX_CLIENT_NAME_CODE_POINTS + 1),
                ),
            )
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            assertEquals(
                ClientNameFieldError.TOO_LONG,
                viewModel.uiState.value.editor?.fieldError,
            )
            assertTrue(repository.currentClients.isEmpty())
        }

    @Test
    fun caseAndWhitespaceEquivalentAddIsRejectedAsDuplicate() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(listOf(client("acme", "Acme Corp")))
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName("  ACME\t corp "))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()

            assertEquals(
                ClientNameFieldError.DUPLICATE_ACTIVE,
                viewModel.uiState.value.editor?.fieldError,
            )
            assertEquals(1, repository.currentClients.size)
        }

    @Test
    fun renameUsesSharedValidationAndDetectsConflict() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client("alpha", "Alpha"),
                        client("beta", "Beta"),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenRenameClient("beta"))
            viewModel.onEvent(ClientManagementEvent.EditName(" Beta   Works "))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()
            assertEquals(
                "Beta Works",
                repository.currentClients.single { it.id == "beta" }.name,
            )

            viewModel.onEvent(ClientManagementEvent.OpenRenameClient("beta"))
            viewModel.onEvent(ClientManagementEvent.EditName(" ALPHA "))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()
            assertEquals(
                ClientNameFieldError.DUPLICATE_ACTIVE,
                viewModel.uiState.value.editor?.fieldError,
            )
            assertEquals(
                "Beta Works",
                repository.currentClients.single { it.id == "beta" }.name,
            )
        }

    @Test
    fun archiveMovesClientToArchivedListAndRestoreReturnsIt() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(listOf(client("alpha", "Alpha")))
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.RequestArchive("alpha"))
            assertNotNull(viewModel.uiState.value.archiveConfirmation)
            viewModel.onEvent(ClientManagementEvent.ConfirmArchive)
            runCurrent()

            assertTrue(viewModel.uiState.value.activeClients.isEmpty())
            assertEquals("Alpha", viewModel.uiState.value.archivedClients.single().name)

            viewModel.onEvent(ClientManagementEvent.RestoreClient("alpha"))
            runCurrent()
            assertEquals("Alpha", viewModel.uiState.value.activeClients.single().name)
            assertTrue(viewModel.uiState.value.archivedClients.isEmpty())
        }

    @Test
    fun addMatchingArchivedClientOffersRestoreAndRestoresOnConfirmation() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client(
                            id = "archived",
                            name = "Acme Corp",
                            isActive = false,
                        ),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName(" acme   CORP "))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()

            assertEquals("archived", viewModel.uiState.value.restoreOffer?.clientId)
            assertFalse(repository.currentClients.single().isActive)

            viewModel.onEvent(ClientManagementEvent.ConfirmRestoreOffer)
            runCurrent()
            assertTrue(repository.currentClients.single().isActive)
            assertNull(viewModel.uiState.value.restoreOffer)
        }

    @Test
    fun cancelingEditorAndRestoreOfferSavesNothing() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(client("archived", "Acme", isActive = false)),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName("New Client"))
            viewModel.onEvent(ClientManagementEvent.DismissEditor)
            assertEquals(1, repository.currentClients.size)

            viewModel.onEvent(ClientManagementEvent.OpenAddClient)
            viewModel.onEvent(ClientManagementEvent.EditName("Acme"))
            viewModel.onEvent(ClientManagementEvent.ConfirmEditor)
            runCurrent()
            viewModel.onEvent(ClientManagementEvent.DismissRestoreOffer)

            assertFalse(repository.currentClients.single().isActive)
            assertNull(viewModel.uiState.value.restoreOffer)
        }

    @Test
    fun restoringArchivedClientWithActiveNameConflictIsSafe() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client("active", "Acme", isActive = true),
                        client("archived", "ACME", isActive = false),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(ClientManagementEvent.RestoreClient("archived"))
            runCurrent()

            assertEquals(
                ClientManagementMessage.RESTORE_NAME_CONFLICT,
                viewModel.uiState.value.message,
            )
            assertFalse(
                repository.currentClients.single { it.id == "archived" }.isActive,
            )
        }

    private fun TestScope.viewModelAndCollect(
        repository: FakeClientRepository,
    ): ClientManagementViewModel {
        val viewModel = ClientManagementViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return viewModel
    }
}
