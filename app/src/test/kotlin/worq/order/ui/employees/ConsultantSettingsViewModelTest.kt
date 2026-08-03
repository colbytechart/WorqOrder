package worq.order.ui.employees

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.AppSettings
import worq.order.domain.ConsultantSelectionCoordinator
import worq.order.model.Employee
import worq.order.testing.FakeEmployeeRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ConsultantSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun directoryIsSortedAndSelectionPersists() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture(employee("z", "Zulu"), employee("a", "Alpha"))
            val viewModel = fixture.viewModel()
            collect(viewModel)
            runCurrent()

            assertEquals(
                listOf("Alpha", "Zulu"),
                viewModel.uiState.value.activeConsultants.map { it.name },
            )
            viewModel.onEvent(ConsultantSettingsEvent.SelectConsultant("z"))
            runCurrent()

            assertEquals("z", viewModel.uiState.value.selectedConsultantId)
            assertEquals("z", fixture.settings.readSettings().selectedEmployeeId)
        }

    @Test
    fun addRenameDuplicateArchiveAndRestoreUseOneDirectory() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture(employee("a", "Alpha"))
            val viewModel = fixture.viewModel()
            collect(viewModel)
            runCurrent()

            viewModel.onEvent(ConsultantSettingsEvent.OpenAddConsultant)
            viewModel.onEvent(ConsultantSettingsEvent.EditName("  Beta   User "))
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmEditor)
            runCurrent()
            val beta = viewModel.uiState.value.activeConsultants.single { it.name == "Beta User" }

            viewModel.onEvent(ConsultantSettingsEvent.OpenRenameConsultant(beta.id))
            viewModel.onEvent(ConsultantSettingsEvent.EditName(" alpha "))
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmEditor)
            runCurrent()
            assertEquals(
                ConsultantNameFieldError.DUPLICATE_ACTIVE,
                viewModel.uiState.value.editor?.fieldError,
            )

            viewModel.onEvent(ConsultantSettingsEvent.DismissEditor)
            viewModel.onEvent(ConsultantSettingsEvent.RequestArchive(beta.id))
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmArchive)
            runCurrent()
            assertTrue(viewModel.uiState.value.archivedConsultants.any { it.id == beta.id })

            viewModel.onEvent(ConsultantSettingsEvent.RestoreConsultant(beta.id))
            runCurrent()
            assertTrue(viewModel.uiState.value.activeConsultants.any { it.id == beta.id })
        }

    @Test
    fun archivingCurrentSelectionClearsPreferenceAndPreservesDirectoryRow() =
        runTest(mainDispatcherRule.dispatcher) {
            val selected = employee("selected", "Selected Person")
            val fixture = Fixture(selected, selectedId = selected.id)
            val viewModel = fixture.viewModel()
            collect(viewModel)
            runCurrent()

            viewModel.onEvent(ConsultantSettingsEvent.RequestArchive(selected.id))
            assertTrue(viewModel.uiState.value.archiveConfirmation?.wasSelected == true)
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmArchive)
            runCurrent()

            assertNull(fixture.settings.readSettings().selectedEmployeeId)
            assertNull(viewModel.uiState.value.selectedConsultantId)
            assertTrue(viewModel.uiState.value.archivedConsultants.any { it.id == selected.id })
        }

    @Test
    fun addMatchingArchivedConsultantOffersRestore() =
        runTest(mainDispatcherRule.dispatcher) {
            val archived = employee("archived", "Morgan Lee", active = false)
            val fixture = Fixture(archived)
            val viewModel = fixture.viewModel()
            collect(viewModel)
            runCurrent()

            viewModel.onEvent(ConsultantSettingsEvent.OpenAddConsultant)
            viewModel.onEvent(ConsultantSettingsEvent.EditName(" morgan   lee "))
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmEditor)
            runCurrent()

            assertEquals("archived", viewModel.uiState.value.restoreOffer?.consultantId)
            viewModel.onEvent(ConsultantSettingsEvent.ConfirmRestoreOffer)
            runCurrent()
            assertTrue(viewModel.uiState.value.activeConsultants.any { it.id == "archived" })
        }

    private fun kotlinx.coroutines.test.TestScope.collect(
        viewModel: ConsultantSettingsViewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class Fixture(
        vararg employees: Employee,
        selectedId: String? = null,
    ) {
        val repository = FakeEmployeeRepository(employees.toList())
        val settings = FakeSettingsRepository(AppSettings(selectedEmployeeId = selectedId))
        private val coordinator = ConsultantSelectionCoordinator(repository, settings)

        fun viewModel() =
            ConsultantSettingsViewModel(
                employeeRepository = repository,
                settingsRepository = settings,
                selectionCoordinator = coordinator,
            )
    }

    private companion object {
        fun employee(
            id: String,
            name: String,
            active: Boolean = true,
        ) =
            Employee(
                id = id,
                name = name,
                canonicalName = name.lowercase(),
                isActive = active,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                archivedAt = Instant.EPOCH.takeIf { !active },
            )
    }
}
