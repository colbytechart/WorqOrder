package worq.order.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employee_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            name = "index_daily_tasks_series_date_zone",
            value = ["series_id", "work_date_epoch_day", "zone_id"],
            unique = true,
        ),
        Index(
            name = "index_daily_tasks_work_date_sort",
            value = ["work_date_epoch_day", "created_at_epoch_ms", "id"],
        ),
        Index(
            name = "index_daily_tasks_client_id",
            value = ["client_id"],
        ),
        Index(
            name = "index_daily_tasks_employee_id",
            value = ["employee_id"],
        ),
    ],
)
data class DailyTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "client_id")
    val clientId: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(
        name = "hardware_software_purchases",
        defaultValue = "''",
    )
    val hardwareSoftwarePurchases: String = "",
    @ColumnInfo(name = "employee_id")
    val employeeId: String? = null,
    @ColumnInfo(
        name = "employee_name_snapshot",
        defaultValue = "''",
    )
    val employeeNameSnapshot: String = "",
    @ColumnInfo(
        name = "work_type",
        defaultValue = "'UNSPECIFIED'",
    )
    val workType: String = "UNSPECIFIED",
    @ColumnInfo(name = "billing_status")
    val billingStatus: String? = null,
    @ColumnInfo(name = "mileage")
    val mileage: String? = null,
    @ColumnInfo(name = "work_date_epoch_day")
    val workDateEpochDay: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
