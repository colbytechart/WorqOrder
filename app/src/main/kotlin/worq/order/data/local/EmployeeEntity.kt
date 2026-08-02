package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [
        Index(
            name = "index_employees_active_name_key",
            value = ["active_name_key"],
            unique = true,
        ),
        Index(
            name = "index_employees_active_sort",
            value = ["is_active", "name", "id"],
        ),
    ],
)
data class EmployeeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "active_name_key")
    val activeNameKey: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "archived_at_epoch_ms")
    val archivedAtEpochMs: Long?,
)
