package worq.order.data

import kotlinx.coroutines.flow.Flow
import worq.order.model.Employee

sealed interface EmployeeMutationResult {
    data class Success(
        val employee: Employee,
    ) : EmployeeMutationResult

    data class MatchingArchivedEmployee(
        val employee: Employee,
    ) : EmployeeMutationResult

    data class InvalidName(
        val reason: EmployeeNameValidationError,
    ) : EmployeeMutationResult

    data class DuplicateActiveName(
        val conflictingEmployeeId: String?,
    ) : EmployeeMutationResult

    data object NotFound : EmployeeMutationResult
}

interface EmployeeRepository {
    fun observeActiveEmployees(): Flow<List<Employee>>

    fun observeAllEmployees(): Flow<List<Employee>>

    suspend fun readEmployee(employeeId: String): Employee?

    suspend fun addEmployee(name: String): EmployeeMutationResult

    suspend fun renameEmployee(
        employeeId: String,
        name: String,
    ): EmployeeMutationResult

    suspend fun archiveEmployee(employeeId: String): EmployeeMutationResult

    suspend fun restoreEmployee(employeeId: String): EmployeeMutationResult
}
