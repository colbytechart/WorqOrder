package worq.order.data.local

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import worq.order.data.EmployeeMutationResult
import worq.order.data.EmployeeNameNormalizer
import worq.order.data.EmployeeNameValidationResult
import worq.order.data.EmployeeRepository
import worq.order.data.EntityIdGenerator
import worq.order.model.Employee
import worq.order.timer.UtcClock

class RoomEmployeeRepository(
    private val dao: EmployeeDao,
    private val idGenerator: EntityIdGenerator,
    private val clock: UtcClock,
) : EmployeeRepository {
    override fun observeActiveEmployees(): Flow<List<Employee>> =
        dao.observeActiveEmployees().map { rows -> rows.map(EmployeeEntity::toModel) }

    override fun observeAllEmployees(): Flow<List<Employee>> =
        dao.observeAllEmployees().map { rows -> rows.map(EmployeeEntity::toModel) }

    override suspend fun readEmployee(employeeId: String): Employee? =
        dao.readEmployee(employeeId)?.toModel()

    override suspend fun addEmployee(name: String): EmployeeMutationResult {
        val normalized = validName(name) ?: return invalidName(name)
        dao.findActiveNameConflict(normalized.canonicalName, null)?.let {
            return EmployeeMutationResult.DuplicateActiveName(it.id)
        }
        dao.findArchivedByName(normalized.canonicalName)?.let {
            return EmployeeMutationResult.MatchingArchivedEmployee(it.toModel())
        }
        val now = clock.now().toEpochMilli()
        val employee =
            EmployeeEntity(
                id = idGenerator.newId(),
                name = normalized.displayName,
                canonicalName = normalized.canonicalName,
                activeNameKey = normalized.canonicalName,
                isActive = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                archivedAtEpochMs = null,
            )
        return try {
            dao.insert(employee)
            EmployeeMutationResult.Success(employee.toModel())
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(normalized.canonicalName, error)
        }
    }

    override suspend fun renameEmployee(
        employeeId: String,
        name: String,
    ): EmployeeMutationResult {
        val current = dao.readEmployee(employeeId) ?: return EmployeeMutationResult.NotFound
        val normalized = validName(name) ?: return invalidName(name)
        if (current.isActive) {
            dao.findActiveNameConflict(normalized.canonicalName, employeeId)?.let {
                return EmployeeMutationResult.DuplicateActiveName(it.id)
            }
        }
        return try {
            if (
                dao.rename(
                    employeeId,
                    normalized.displayName,
                    normalized.canonicalName,
                    clock.now().toEpochMilli(),
                ) == 0
            ) {
                EmployeeMutationResult.NotFound
            } else {
                EmployeeMutationResult.Success(requireNotNull(dao.readEmployee(employeeId)).toModel())
            }
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(normalized.canonicalName, error)
        }
    }

    override suspend fun archiveEmployee(employeeId: String): EmployeeMutationResult {
        val current = dao.readEmployee(employeeId) ?: return EmployeeMutationResult.NotFound
        if (current.isActive) dao.archive(employeeId, clock.now().toEpochMilli())
        return EmployeeMutationResult.Success(requireNotNull(dao.readEmployee(employeeId)).toModel())
    }

    override suspend fun restoreEmployee(employeeId: String): EmployeeMutationResult {
        val current = dao.readEmployee(employeeId) ?: return EmployeeMutationResult.NotFound
        if (current.isActive) return EmployeeMutationResult.Success(current.toModel())
        dao.findActiveNameConflict(current.canonicalName, employeeId)?.let {
            return EmployeeMutationResult.DuplicateActiveName(it.id)
        }
        return try {
            dao.restore(employeeId, clock.now().toEpochMilli())
            EmployeeMutationResult.Success(requireNotNull(dao.readEmployee(employeeId)).toModel())
        } catch (error: SQLiteConstraintException) {
            duplicateAfterConstraint(current.canonicalName, error)
        }
    }

    private fun validName(name: String) =
        (EmployeeNameNormalizer.validate(name) as? EmployeeNameValidationResult.Valid)?.name

    private fun invalidName(name: String): EmployeeMutationResult.InvalidName =
        EmployeeMutationResult.InvalidName(
            (EmployeeNameNormalizer.validate(name) as EmployeeNameValidationResult.Invalid).error,
        )

    private suspend fun duplicateAfterConstraint(
        canonicalName: String,
        error: SQLiteConstraintException,
    ): EmployeeMutationResult {
        val conflict = dao.findActiveNameConflict(canonicalName, null)
        if (conflict != null) return EmployeeMutationResult.DuplicateActiveName(conflict.id)
        throw error
    }
}
