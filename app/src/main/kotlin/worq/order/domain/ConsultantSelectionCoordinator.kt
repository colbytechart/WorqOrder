package worq.order.domain

import worq.order.data.EmployeeMutationResult
import worq.order.data.EmployeeRepository
import worq.order.data.SettingsRepository
import worq.order.model.Employee

sealed interface ConsultantSelectionResult {
    data class Selected(
        val consultant: Employee,
    ) : ConsultantSelectionResult

    data object NotFound : ConsultantSelectionResult

    data object Archived : ConsultantSelectionResult
}

/**
 * Coordinates the Room-owned Consultant directory with the non-authoritative DataStore selection.
 */
class ConsultantSelectionCoordinator(
    private val employeeRepository: EmployeeRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun select(employeeId: String): ConsultantSelectionResult {
        val employee = employeeRepository.readEmployee(employeeId)
            ?: return ConsultantSelectionResult.NotFound
        if (!employee.isActive) return ConsultantSelectionResult.Archived
        settingsRepository.setSelectedEmployeeId(employee.id)
        return ConsultantSelectionResult.Selected(employee)
    }

    suspend fun clearSelection() {
        settingsRepository.setSelectedEmployeeId(null)
    }

    suspend fun reconcileSelection(): Employee? {
        val selectedId = settingsRepository.readSettings().selectedEmployeeId ?: return null
        val employee = employeeRepository.readEmployee(selectedId)
        if (employee?.isActive == true) return employee
        settingsRepository.setSelectedEmployeeId(null)
        return null
    }

    suspend fun archive(employeeId: String): EmployeeMutationResult {
        val result = employeeRepository.archiveEmployee(employeeId)
        if (
            result is EmployeeMutationResult.Success &&
            settingsRepository.readSettings().selectedEmployeeId == employeeId
        ) {
            settingsRepository.setSelectedEmployeeId(null)
        }
        return result
    }
}
