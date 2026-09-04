package worq.order.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.ZoneId
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.EntityIdGenerator
import worq.order.timer.UtcClock

@RunWith(AndroidJUnit4::class)
class Schema5MigrationCoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun removeFileDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationSplitsHistoricalIntervalsAndRepointsActiveTimerWithoutDataLoss() =
        runBlocking {
            createPopulatedVersionFourDatabase()

            val migrated =
                Room
                    .databaseBuilder(
                        context,
                        WorqOrderDatabase::class.java,
                        DATABASE_NAME,
                    ).addMigrations(WorqOrderMigrations.MIGRATION_4_5)
                    .allowMainThreadQueries()
                    .build()
            try {
                val sqlite = migrated.openHelper.writableDatabase
                val secondTaskId =
                    WorqOrderMigrations.deriveMigratedTaskId("source-task", "interval-2")
                val activeTaskId =
                    WorqOrderMigrations.deriveMigratedTaskId("source-task", "interval-active")

                assertEquals(5L, sqlite.scalarLong("SELECT COUNT(*) FROM daily_tasks"))
                assertEquals(4L, sqlite.scalarLong("SELECT COUNT(*) FROM work_intervals"))
                assertEquals(
                    "source-task",
                    sqlite.scalarString(
                        "SELECT task_id FROM work_intervals WHERE id = 'interval-1'",
                    ),
                )
                assertEquals(
                    secondTaskId,
                    sqlite.scalarString(
                        "SELECT task_id FROM work_intervals WHERE id = 'interval-2'",
                    ),
                )
                assertEquals(
                    activeTaskId,
                    sqlite.scalarString(
                        "SELECT task_id FROM work_intervals WHERE id = 'interval-active'",
                    ),
                )
                assertEquals(
                    "single-task",
                    sqlite.scalarString(
                        "SELECT task_id FROM work_intervals WHERE id = 'single-interval'",
                    ),
                )
                assertEquals(
                    1_000L,
                    sqlite.scalarLong(
                        "SELECT start_epoch_ms FROM work_intervals WHERE id = 'interval-1'",
                    ),
                )
                assertEquals(
                    2_000L,
                    sqlite.scalarLong(
                        "SELECT stop_epoch_ms FROM work_intervals WHERE id = 'interval-1'",
                    ),
                )
                assertEquals(
                    1_000L,
                    sqlite.scalarLong(
                        "SELECT start_epoch_ms FROM work_intervals WHERE id = 'interval-2'",
                    ),
                )
                assertEquals(
                    3_000L,
                    sqlite.scalarLong(
                        "SELECT stop_epoch_ms FROM work_intervals WHERE id = 'interval-2'",
                    ),
                )
                assertEquals(
                    2_000L,
                    sqlite.scalarLong(
                        "SELECT created_at_epoch_ms FROM daily_tasks WHERE id = ?",
                        arrayOf(secondTaskId),
                    ),
                )
                assertEquals(
                    8_000L,
                    sqlite.scalarLong(
                        "SELECT updated_at_epoch_ms FROM daily_tasks WHERE id = ?",
                        arrayOf(secondTaskId),
                    ),
                )
                assertEquals(
                    3_000L,
                    sqlite.scalarLong(
                        "SELECT start_epoch_ms FROM work_intervals WHERE id = 'interval-active'",
                    ),
                )
                assertNull(
                    sqlite.scalarNullableLong(
                        "SELECT stop_epoch_ms FROM work_intervals WHERE id = 'interval-active'",
                    ),
                )
                assertEquals(
                    activeTaskId,
                    sqlite.scalarString("SELECT task_id FROM active_timer WHERE singleton_id = 1"),
                )
                assertEquals(
                    "interval-active",
                    sqlite.scalarString(
                        "SELECT interval_id FROM active_timer WHERE singleton_id = 1",
                    ),
                )
                assertEquals(
                    "DO_NOT_CHARGE",
                    sqlite.scalarString(
                        "SELECT billing_status FROM daily_tasks WHERE id = ?",
                        arrayOf(activeTaskId),
                    ),
                )
                assertEquals(
                    "Consultant Snapshot",
                    sqlite.scalarString(
                        "SELECT employee_name_snapshot FROM daily_tasks WHERE id = ?",
                        arrayOf(secondTaskId),
                    ),
                )
                assertEquals(
                    "Description",
                    sqlite.scalarString(
                        "SELECT description FROM daily_tasks WHERE id = ?",
                        arrayOf(secondTaskId),
                    ),
                )
                assertEquals(
                    "Expense",
                    sqlite.scalarString(
                        "SELECT hardware_software_purchases FROM daily_tasks WHERE id = ?",
                        arrayOf(secondTaskId),
                    ),
                )
                assertEquals(
                    "client-1",
                    sqlite.scalarString(
                        "SELECT client_id FROM daily_tasks WHERE id = ?",
                        arrayOf(activeTaskId),
                    ),
                )
                assertEquals(
                    "employee-1",
                    sqlite.scalarString(
                        "SELECT employee_id FROM daily_tasks WHERE id = ?",
                        arrayOf(activeTaskId),
                    ),
                )
                assertEquals(
                    0L,
                    sqlite.scalarLong("SELECT is_active FROM clients WHERE id = 'client-1'"),
                )
                assertEquals(
                    0L,
                    sqlite.scalarLong("SELECT is_active FROM employees WHERE id = 'employee-1'"),
                )
                assertEquals(
                    1L,
                    sqlite.scalarLong(
                        "SELECT COUNT(*) FROM daily_tasks WHERE id = 'empty-task'",
                    ),
                )
                assertEquals(
                    "",
                    sqlite.scalarString(
                        "SELECT hardware_software_purchases FROM daily_tasks WHERE id = 'empty-task'",
                    ),
                )
                assertNull(
                    sqlite.scalarNullableString(
                        "SELECT employee_id FROM daily_tasks WHERE id = 'empty-task'",
                    ),
                )
                assertNull(
                    sqlite.scalarNullableString(
                        "SELECT billing_status FROM daily_tasks WHERE id = 'empty-task'",
                    ),
                )
                assertNull(
                    sqlite.scalarNullableString(
                        "SELECT mileage FROM daily_tasks WHERE id = 'empty-task'",
                    ),
                )
                assertEquals(
                    3_000L,
                    sqlite.scalarLong(
                        "SELECT created_at_epoch_ms FROM daily_tasks WHERE id = ?",
                        arrayOf(activeTaskId),
                    ),
                )
                assertEquals(
                    9_000L,
                    sqlite.scalarLong(
                        "SELECT updated_at_epoch_ms FROM daily_tasks WHERE id = ?",
                        arrayOf(activeTaskId),
                    ),
                )
                assertFalse(sqlite.columns("work_intervals").contains("ordinal"))
                assertEquals(0L, sqlite.scalarLong("SELECT COUNT(*) FROM pragma_foreign_key_check"))
            } finally {
                migrated.close()
            }

            val reopened =
                Room
                    .databaseBuilder(context, WorqOrderDatabase::class.java, DATABASE_NAME)
                    .allowMainThreadQueries()
                    .build()
            try {
                assertEquals(
                    5,
                    reopened.openHelper.writableDatabase
                        .scalarLong("SELECT COUNT(*) FROM daily_tasks")
                        .toInt(),
                )
                assertEquals(
                    4L,
                    reopened.openHelper.writableDatabase
                        .scalarLong("SELECT COUNT(*) FROM work_intervals"),
                )
                assertEquals(
                    "interval-active",
                    reopened.activeTimerDao().readActiveTimer()?.intervalId,
                )
                assertEquals(
                    WorqOrderMigrations.deriveMigratedTaskId(
                        "source-task",
                        "interval-active",
                    ),
                    reopened.activeTimerDao().readActiveTimer()?.taskId,
                )
            } finally {
                reopened.close()
            }
        }

    @Test
    fun startingCompletedTaskCreatesMetadataCopyAndSingleActiveIntervalAtomically() =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, WorqOrderDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            try {
                database.clientDao().addClient(
                    ClientEntity(
                        id = "client-1",
                        name = "Client",
                        canonicalName = "client",
                        activeNameKey = "client",
                        isActive = true,
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 1_000,
                        archivedAtEpochMs = null,
                    ),
                )
                database.employeeDao().insert(
                    EmployeeEntity(
                        id = "employee-1",
                        name = "Consultant",
                        canonicalName = "consultant",
                        activeNameKey = "consultant",
                        isActive = true,
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 1_000,
                        archivedAtEpochMs = null,
                    ),
                )
                database.taskDao().insertDailyTask(
                    DailyTaskEntity(
                        id = "source-task",
                        seriesId = "lineage-1",
                        clientId = "client-1",
                        description = "Source description",
                        hardwareSoftwarePurchases = "Source expense",
                        employeeId = "employee-1",
                        employeeNameSnapshot = "Consultant Snapshot",
                        workType = "ON_SITE",
                        billingStatus = "DO_NOT_BILL",
                        mileage = "12.5",
                        workDateEpochDay = 20_000,
                        zoneId = "America/New_York",
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 2_000,
                    ),
                )
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO work_intervals (
                        id, task_id, start_epoch_ms, stop_epoch_ms, active_slot,
                        was_manually_edited, created_at_epoch_ms, updated_at_epoch_ms
                    ) VALUES ('completed', 'source-task', 1000, 2000, NULL, 0, 1000, 2000)
                    """.trimIndent(),
                )
                val repository =
                    RoomActiveTimerRepository(
                        activeTimerDao = database.activeTimerDao(),
                        idGenerator = QueueIdGenerator("new-interval", "repeated-task"),
                        clock = FixedClock(Instant.ofEpochMilli(5_000)),
                    )

                val result =
                    repository.createActiveInterval(
                        taskId = "source-task",
                        boundaryZoneId = ZoneId.of("America/New_York"),
                        start = Instant.ofEpochMilli(4_000),
                    ) as CreateActiveIntervalResult.Created

                assertTrue(result.repeatedTaskCreated)
                assertEquals("repeated-task", result.startedTask.id)
                assertEquals("lineage-1", result.startedTask.seriesId)
                assertEquals("client-1", result.startedTask.clientId)
                assertEquals("Source description", result.startedTask.description)
                assertEquals("Source expense", result.startedTask.hardwareSoftwarePurchases)
                assertEquals("employee-1", result.startedTask.employeeId)
                assertEquals("Consultant Snapshot", result.startedTask.employeeNameSnapshot)
                assertEquals(worq.order.model.WorkType.ON_SITE, result.startedTask.workType)
                assertEquals(
                    worq.order.model.BillingStatus.DO_NOT_BILL,
                    result.startedTask.billingStatus,
                )
                assertEquals("12.5", result.startedTask.mileage)
                assertEquals(java.time.LocalDate.ofEpochDay(20_000), result.startedTask.workDate)
                assertEquals(ZoneId.of("America/New_York"), result.startedTask.zoneId)
                assertEquals(Instant.ofEpochMilli(5_000), result.startedTask.createdAt)
                assertEquals(Instant.ofEpochMilli(5_000), result.startedTask.updatedAt)
                assertEquals("new-interval", result.snapshot.interval.id)
                assertEquals("repeated-task", result.snapshot.interval.taskId)
                assertNull(result.snapshot.interval.stop)
                assertEquals(
                    "repeated-task",
                    database.activeTimerDao().readActiveTimer()?.taskId,
                )
                assertEquals(
                    1L,
                    database.openHelper.writableDatabase.scalarLong(
                        "SELECT COUNT(*) FROM work_intervals WHERE task_id = 'source-task'",
                    ),
                )
                assertEquals(
                    1L,
                    database.openHelper.writableDatabase.scalarLong(
                        "SELECT COUNT(*) FROM work_intervals WHERE task_id = 'repeated-task'",
                    ),
                )
                assertEquals(
                    2_000L,
                    database.taskDao().readTask("source-task")?.updatedAtEpochMs,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun manualIntervalCompatibilityPathRejectsSecondIntervalWithoutMutatingTask() =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, WorqOrderDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            try {
                database.clientDao().addClient(
                    ClientEntity(
                        id = "client-1",
                        name = "Client",
                        canonicalName = "client",
                        activeNameKey = "client",
                        isActive = true,
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 1_000,
                        archivedAtEpochMs = null,
                    ),
                )
                database.taskDao().insertDailyTask(
                    DailyTaskEntity(
                        id = "task-1",
                        seriesId = "lineage-1",
                        clientId = "client-1",
                        description = "Task",
                        hardwareSoftwarePurchases = "",
                        employeeId = null,
                        employeeNameSnapshot = "",
                        workType = "ON_SITE",
                        billingStatus = null,
                        mileage = null,
                        workDateEpochDay = 20_000,
                        zoneId = "America/New_York",
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 1_000,
                    ),
                )
                val repository =
                    RoomTaskRepository(
                        taskDao = database.taskDao(),
                        workIntervalDao = database.workIntervalDao(),
                        idGenerator = QueueIdGenerator("manual-1", "manual-2"),
                        clock = FixedClock(Instant.ofEpochMilli(5_000)),
                    )

                val first =
                    repository.addManualInterval(
                        taskId = "task-1",
                        start = Instant.ofEpochMilli(1_000),
                        stop = Instant.ofEpochMilli(2_000),
                    )
                val second =
                    repository.addManualInterval(
                        taskId = "task-1",
                        start = Instant.ofEpochMilli(3_000),
                        stop = Instant.ofEpochMilli(4_000),
                    )

                assertTrue(first is worq.order.data.ManualIntervalPersistenceResult.Saved)
                val saved = first as worq.order.data.ManualIntervalPersistenceResult.Saved
                assertEquals(1, saved.interval.ordinal)
                assertEquals(
                    worq.order.data.ManualIntervalPersistenceResult.TaskAlreadyHasInterval,
                    second,
                )
                assertEquals(1, database.workIntervalDao().countIntervalsForTask("task-1"))
            } finally {
                database.close()
            }
        }

    private fun createPopulatedVersionFourDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "CREATE TABLE clients (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "canonical_name TEXT NOT NULL, active_name_key TEXT, is_active INTEGER NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, " +
                    "archived_at_epoch_ms INTEGER)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_clients_active_name_key ON clients (active_name_key)")
            db.execSQL("CREATE INDEX index_clients_active_sort ON clients (is_active, name, id)")
            db.execSQL(
                "CREATE TABLE employees (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "canonical_name TEXT NOT NULL, active_name_key TEXT, is_active INTEGER NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, " +
                    "archived_at_epoch_ms INTEGER)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_employees_active_name_key ON employees (active_name_key)")
            db.execSQL("CREATE INDEX index_employees_active_sort ON employees (is_active, name, id)")
            db.execSQL(
                """
                CREATE TABLE daily_tasks (
                    id TEXT NOT NULL PRIMARY KEY,
                    series_id TEXT NOT NULL,
                    client_id TEXT NOT NULL,
                    description TEXT NOT NULL,
                    hardware_software_purchases TEXT NOT NULL DEFAULT '',
                    employee_id TEXT,
                    employee_name_snapshot TEXT NOT NULL DEFAULT '',
                    work_type TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                    billing_status TEXT,
                    mileage TEXT,
                    work_date_epoch_day INTEGER NOT NULL,
                    zone_id TEXT NOT NULL,
                    created_at_epoch_ms INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(client_id) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(employee_id) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_daily_tasks_series_date_zone " +
                    "ON daily_tasks (series_id, work_date_epoch_day, zone_id)",
            )
            db.execSQL(
                "CREATE INDEX index_daily_tasks_work_date_sort " +
                    "ON daily_tasks (work_date_epoch_day, created_at_epoch_ms, id)",
            )
            db.execSQL("CREATE INDEX index_daily_tasks_client_id ON daily_tasks (client_id)")
            db.execSQL("CREATE INDEX index_daily_tasks_employee_id ON daily_tasks (employee_id)")
            db.execSQL(
                """
                CREATE TABLE work_intervals (
                    id TEXT NOT NULL PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    start_epoch_ms INTEGER NOT NULL,
                    stop_epoch_ms INTEGER,
                    active_slot INTEGER,
                    was_manually_edited INTEGER NOT NULL,
                    created_at_epoch_ms INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES daily_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_work_intervals_task_ordinal " +
                    "ON work_intervals (task_id, ordinal)",
            )
            db.execSQL(
                "CREATE INDEX index_work_intervals_task_start " +
                    "ON work_intervals (task_id, start_epoch_ms, ordinal, id)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_work_intervals_active_slot ON work_intervals (active_slot)")
            db.execSQL("CREATE UNIQUE INDEX index_work_intervals_id_task ON work_intervals (id, task_id)")
            db.execSQL(
                """
                CREATE TABLE active_timer (
                    singleton_id INTEGER NOT NULL PRIMARY KEY,
                    interval_id TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    boundary_zone_id TEXT NOT NULL,
                    created_at_epoch_ms INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(interval_id, task_id) REFERENCES work_intervals(id, task_id)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_active_timer_interval_task " +
                    "ON active_timer (interval_id, task_id)",
            )
            db.execSQL("CREATE INDEX index_active_timer_task_id ON active_timer (task_id)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '7ac2d6556908d24f1e2921fec5114d44')",
            )
            db.execSQL(
                "INSERT INTO clients VALUES " +
                    "('client-1', 'Client', 'client', NULL, 0, 1000, 9000, 9000)",
            )
            db.execSQL(
                "INSERT INTO employees VALUES " +
                    "('employee-1', 'Consultant', 'consultant', NULL, 0, 1000, 9000, 9000)",
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks VALUES (
                    'source-task', 'lineage-1', 'client-1', 'Description', 'Expense',
                    'employee-1', 'Consultant Snapshot', 'ON_SITE', 'DO_NOT_CHARGE', '12.5',
                    20000, 'America/New_York', 1000, 8000
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO work_intervals VALUES " +
                    "('interval-1', 'source-task', 1, 1000, 2000, NULL, 0, 1000, 2000)",
            )
            db.execSQL(
                "INSERT INTO work_intervals VALUES " +
                    "('interval-2', 'source-task', 2, 1000, 3000, NULL, 1, 2000, 7000)",
            )
            db.execSQL(
                "INSERT INTO work_intervals VALUES " +
                    "('interval-active', 'source-task', 3, 3000, NULL, 1, 0, 3000, 9000)",
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks VALUES (
                    'single-task', 'single-lineage', 'client-1', 'Single history', 'Single expense',
                    'employee-1', 'Consultant Snapshot', 'IN_OFFICE', 'BILLABLE', '4.25',
                    20001, 'America/New_York', 3500, 6500
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO work_intervals VALUES " +
                    "('single-interval', 'single-task', 1, 4000, 6000, NULL, 0, 4000, 6000)",
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks VALUES (
                    'empty-task', 'empty-lineage', 'client-1', 'Empty history', '',
                    NULL, '', 'UNSPECIFIED', NULL, NULL,
                    20000, 'America/New_York', 4000, 4000
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO active_timer VALUES " +
                    "(1, 'interval-active', 'source-task', 'America/New_York', 3000, 9000)",
            )
            db.version = 4
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(
        sql: String,
        bindArgs: Array<out Any?> = emptyArray(),
    ): Long =
        query(sql, bindArgs).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarString(
        sql: String,
        bindArgs: Array<out Any?> = emptyArray(),
    ): String =
        query(sql, bindArgs).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarNullableString(
        sql: String,
        bindArgs: Array<out Any?> = emptyArray(),
    ): String? =
        query(sql, bindArgs).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarNullableLong(
        sql: String,
        bindArgs: Array<out Any?> = emptyArray(),
    ): Long? =
        query(sql, bindArgs).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info($table)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private class QueueIdGenerator(
        vararg ids: String,
    ) : EntityIdGenerator {
        private val values = ArrayDeque(ids.toList())

        override fun newId(): String = values.removeFirst()
    }

    private class FixedClock(
        private val instant: Instant,
    ) : UtcClock {
        override fun now(): Instant = instant
    }

    private companion object {
        const val DATABASE_NAME = "schema-5-core-test.db"
    }
}
