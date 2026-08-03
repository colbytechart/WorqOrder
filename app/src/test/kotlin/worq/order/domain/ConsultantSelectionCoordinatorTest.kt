package worq.order.domain

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.AppSettings
import worq.order.data.EmployeeMutationResult
import worq.order.model.Employee
import worq.order.testing.FakeEmployeeRepository
import worq.order.testing.FakeSettingsRepository

class ConsultantSelectionCoordinatorTest {
    @Test
    fun selectionRequiresAnActiveDirectoryEntryAndPersists() =
        runTest {
            val active = employee("active", "Alex Rivera")
            val archived = employee("archived", "Morgan Lee", active = false)
            val employees = FakeEmployeeRepository(listOf(active, archived))
            val settings = FakeSettingsRepository()
            val coordinator = ConsultantSelectionCoordinator(employees, settings)

            assertEquals(
                ConsultantSelectionResult.Selected(active),
                coordinator.select(active.id),
            )
            assertEquals(active.id, settings.readSettings().selectedEmployeeId)
            assertEquals(
                ConsultantSelectionResult.Archived,
                coordinator.select(archived.id),
            )
            assertEquals(active.id, settings.readSettings().selectedEmployeeId)
            assertEquals(
                ConsultantSelectionResult.NotFound,
                coordinator.select("missing"),
            )
        }

    @Test
    fun staleOrArchivedSelectionIsClearedDuringRecovery() =
        runTest {
            val archived = employee("archived", "Morgan Lee", active = false)
            val employees = FakeEmployeeRepository(listOf(archived))
            val settings =
                FakeSettingsRepository(AppSettings(selectedEmployeeId = archived.id))
            val coordinator = ConsultantSelectionCoordinator(employees, settings)

            assertNull(coordinator.reconcileSelection())
            assertNull(settings.readSettings().selectedEmployeeId)

            settings.setSelectedEmployeeId("missing")
            assertNull(coordinator.reconcileSelection())
            assertNull(settings.readSettings().selectedEmployeeId)
        }

    @Test
    fun archivingSelectedConsultantClearsOnlyFutureSelection() =
        runTest {
            val active = employee("active", "Alex Rivera")
            val employees = FakeEmployeeRepository(listOf(active))
            val settings =
                FakeSettingsRepository(AppSettings(selectedEmployeeId = active.id))
            val coordinator = ConsultantSelectionCoordinator(employees, settings)

            val result = coordinator.archive(active.id)

            assertTrue(result is EmployeeMutationResult.Success)
            assertNull(settings.readSettings().selectedEmployeeId)
            assertEquals(false, employees.readEmployee(active.id)?.isActive)
        }

    private fun employee(
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
