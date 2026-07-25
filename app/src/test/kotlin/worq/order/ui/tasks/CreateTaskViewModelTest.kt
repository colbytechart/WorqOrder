package worq.order.ui.tasks

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.testing.FakeClientRepository
import worq.order.testing.FakeClientRepository.Companion.client
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTaskViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectorObservesOnlyActiveClients() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(
                        client("active", "Active Client"),
                        client("archived", "Archived Client", isActive = false),
                    ),
                )
            val viewModel = viewModelAndCollect(repository)

            assertEquals(
                listOf("Active Client"),
                viewModel.uiState.value.activeClients.map { it.name },
            )
            assertNull(viewModel.uiState.value.selectedClientId)
        }

    @Test
    fun emptySelectorInlineAddCreatesAndSelectsClient() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeClientRepository()
            val viewModel = viewModelAndCollect(repository)
            assertTrue(viewModel.uiState.value.activeClients.isEmpty())

            viewModel.onEvent(CreateTaskEvent.OpenAddClient)
            viewModel.onEvent(CreateTaskEvent.EditNewClientName(" New   Client "))
            viewModel.onEvent(CreateTaskEvent.ConfirmAddClient)
            runCurrent()

            assertEquals("New Client", viewModel.uiState.value.selectedClient?.name)
            assertFalse(viewModel.uiState.value.activeClients.isEmpty())
            assertNull(viewModel.uiState.value.addClientEditor)
        }

    @Test
    fun inlineAddOffersRestoreForMatchingArchivedClient() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeClientRepository(
                    listOf(client("archived", "Acme", isActive = false)),
                )
            val viewModel = viewModelAndCollect(repository)

            viewModel.onEvent(CreateTaskEvent.OpenAddClient)
            viewModel.onEvent(CreateTaskEvent.EditNewClientName(" ACME "))
            viewModel.onEvent(CreateTaskEvent.ConfirmAddClient)
            runCurrent()
            assertEquals("archived", viewModel.uiState.value.restoreOffer?.clientId)

            viewModel.onEvent(CreateTaskEvent.ConfirmRestoreOffer)
            runCurrent()
            assertEquals("Acme", viewModel.uiState.value.selectedClient?.name)
        }

    private fun TestScope.viewModelAndCollect(
        repository: FakeClientRepository,
    ): CreateTaskViewModel {
        val viewModel = CreateTaskViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return viewModel
    }
}
