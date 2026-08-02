package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EmployeeDao {
    @Query(
        """
        SELECT * FROM employees
        WHERE is_active = 1
        ORDER BY name COLLATE NOCASE ASC, name ASC, id ASC
        """,
    )
    abstract fun observeActiveEmployees(): Flow<List<EmployeeEntity>>

    @Query(
        """
        SELECT * FROM employees
        ORDER BY is_active DESC, name COLLATE NOCASE ASC, name ASC, id ASC
        """,
    )
    abstract fun observeAllEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :employeeId LIMIT 1")
    abstract suspend fun readEmployee(employeeId: String): EmployeeEntity?

    @Query(
        """
        SELECT * FROM employees
        WHERE active_name_key = :canonicalName
          AND (:excludingEmployeeId IS NULL OR id != :excludingEmployeeId)
        LIMIT 1
        """,
    )
    abstract suspend fun findActiveNameConflict(
        canonicalName: String,
        excludingEmployeeId: String?,
    ): EmployeeEntity?

    @Query(
        """
        SELECT * FROM employees
        WHERE is_active = 0 AND canonical_name = :canonicalName
        ORDER BY created_at_epoch_ms ASC, id ASC
        LIMIT 1
        """,
    )
    abstract suspend fun findArchivedByName(canonicalName: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(employee: EmployeeEntity)

    @Query(
        """
        UPDATE employees
        SET name = :name,
            canonical_name = :canonicalName,
            active_name_key = CASE WHEN is_active = 1 THEN :canonicalName ELSE NULL END,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :employeeId
        """,
    )
    abstract suspend fun rename(
        employeeId: String,
        name: String,
        canonicalName: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE employees
        SET is_active = 0, active_name_key = NULL,
            archived_at_epoch_ms = :nowEpochMs, updated_at_epoch_ms = :nowEpochMs
        WHERE id = :employeeId AND is_active = 1
        """,
    )
    abstract suspend fun archive(employeeId: String, nowEpochMs: Long): Int

    @Query(
        """
        UPDATE employees
        SET is_active = 1, active_name_key = canonical_name,
            archived_at_epoch_ms = NULL, updated_at_epoch_ms = :nowEpochMs
        WHERE id = :employeeId AND is_active = 0
        """,
    )
    abstract suspend fun restore(employeeId: String, nowEpochMs: Long): Int
}
