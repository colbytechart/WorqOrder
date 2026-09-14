package worq.order.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Schema6MigrationCoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun removeFileDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun populatedSchemaFiveUpgradePreservesTaskGraphAndOpenTimerWithBlankNotes() =
        runBlocking {
            createPopulatedVersionFiveDatabase()

            val migrated =
                Room
                    .databaseBuilder(context, WorqOrderDatabase::class.java, DATABASE_NAME)
                    .addMigrations(WorqOrderMigrations.MIGRATION_5_6)
                    .allowMainThreadQueries()
                    .build()
            try {
                val sqlite = migrated.openHelper.writableDatabase
                assertEquals(6, sqlite.version)
                assertEquals(2L, sqlite.scalarLong("SELECT COUNT(*) FROM daily_tasks"))
                assertEquals(1L, sqlite.scalarLong("SELECT COUNT(*) FROM work_intervals"))
                assertEquals(1L, sqlite.scalarLong("SELECT COUNT(*) FROM active_timer"))
                assertEquals(
                    0L,
                    sqlite.scalarLong("SELECT COUNT(*) FROM daily_tasks WHERE notes != ''"),
                )
                val timedTask = requireNotNull(migrated.taskDao().readTask("timed-task"))
                assertEquals("lineage-1", timedTask.seriesId)
                assertEquals("client-1", timedTask.clientId)
                assertEquals("Existing description", timedTask.description)
                assertEquals("Existing expense", timedTask.hardwareSoftwarePurchases)
                assertEquals("employee-1", timedTask.employeeId)
                assertEquals("Consultant Snapshot", timedTask.employeeNameSnapshot)
                assertEquals("ON_SITE", timedTask.workType)
                assertEquals("DO_NOT_CHARGE", timedTask.billingStatus)
                assertEquals("12.5", timedTask.mileage)
                assertEquals(20_000L, timedTask.workDateEpochDay)
                assertEquals("America/New_York", timedTask.zoneId)
                assertEquals(1_000L, timedTask.createdAtEpochMs)
                assertEquals(3_000L, timedTask.updatedAtEpochMs)
                assertEquals("", timedTask.notes)
                assertEquals("", migrated.taskDao().readTask("untimed-task")?.notes)
                assertFalse(requireNotNull(migrated.clientDao().readClient("client-1")).isActive)
                assertFalse(requireNotNull(migrated.employeeDao().readEmployee("employee-1")).isActive)
                val historicalClient =
                    requireNotNull(migrated.taskDao().readTaskWithClient("timed-task"))
                assertEquals("Existing client", historicalClient.clientName)
                assertFalse(historicalClient.clientIsActive)
                val interval =
                    migrated.workIntervalDao()
                        .readIntervalsForOverlapValidation("timed-task")
                        .single()
                assertEquals("open-interval", interval.id)
                assertEquals(1_000L, interval.startEpochMs)
                assertNull(interval.stopEpochMs)
                assertEquals(WorkIntervalEntity.ACTIVE_SLOT, interval.activeSlot)
                assertFalse(interval.wasManuallyEdited)
                val active = requireNotNull(migrated.activeTimerDao().readActiveTimer())
                assertEquals("open-interval", active.intervalId)
                assertEquals("timed-task", active.taskId)
                assertEquals("America/New_York", active.boundaryZoneId)
                assertEquals(1_000L, active.createdAtEpochMs)
                assertEquals(3_000L, active.updatedAtEpochMs)
                sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                    assertFalse(cursor.moveToFirst())
                }
            } finally {
                migrated.close()
            }

            val reopened =
                Room
                    .databaseBuilder(context, WorqOrderDatabase::class.java, DATABASE_NAME)
                    .allowMainThreadQueries()
                    .build()
            try {
                assertEquals("", reopened.taskDao().readTask("timed-task")?.notes)
                assertEquals(
                    "open-interval",
                    reopened.activeTimerDao().readActiveTimer()?.intervalId,
                )
                assertEquals(
                    1L,
                    reopened.openHelper.writableDatabase
                        .scalarLong("SELECT COUNT(*) FROM work_intervals"),
                )
            } finally {
                reopened.close()
            }
        }

    @Test
    fun populatedSchemaThreeUpgradePreservesArchivedReferencesAndActiveTimer() =
        runBlocking {
            createPopulatedVersionThreeDatabase()
            val migrated =
                Room
                    .databaseBuilder(context, WorqOrderDatabase::class.java, DATABASE_NAME)
                    .addMigrations(
                        WorqOrderMigrations.MIGRATION_3_4,
                        WorqOrderMigrations.MIGRATION_4_5,
                        WorqOrderMigrations.MIGRATION_5_6,
                    ).allowMainThreadQueries()
                    .build()
            try {
                val task = requireNotNull(migrated.taskDao().readTask("legacy-three-task"))
                assertEquals("legacy-three-lineage", task.seriesId)
                assertEquals("client-1", task.clientId)
                assertEquals("employee-1", task.employeeId)
                assertEquals("Consultant Snapshot", task.employeeNameSnapshot)
                assertEquals("Legacy task", task.description)
                assertEquals("Legacy expense", task.hardwareSoftwarePurchases)
                assertEquals("ON_SITE", task.workType)
                assertNull(task.billingStatus)
                assertEquals("12.5", task.mileage)
                assertEquals(20_000L, task.workDateEpochDay)
                assertEquals("America/New_York", task.zoneId)
                assertEquals(1_000L, task.createdAtEpochMs)
                assertEquals(3_000L, task.updatedAtEpochMs)
                assertEquals("", task.notes)
                assertFalse(requireNotNull(migrated.clientDao().readClient("client-1")).isActive)
                assertFalse(requireNotNull(migrated.employeeDao().readEmployee("employee-1")).isActive)
                val historicalClient =
                    requireNotNull(migrated.taskDao().readTaskWithClient("legacy-three-task"))
                assertEquals("Existing client", historicalClient.clientName)
                assertFalse(historicalClient.clientIsActive)
                val active = requireNotNull(migrated.activeTimerDao().readActiveTimer())
                assertEquals("legacy-three-task", active.taskId)
                assertEquals("legacy-three-open", active.intervalId)
                val sqlite = migrated.openHelper.writableDatabase
                assertEquals(6, sqlite.version)
                assertEquals(1L, sqlite.scalarLong("SELECT COUNT(*) FROM daily_tasks"))
                assertEquals(1L, sqlite.scalarLong("SELECT COUNT(*) FROM work_intervals"))
                sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                    assertFalse(cursor.moveToFirst())
                }
            } finally {
                migrated.close()
            }
        }

    private fun createPopulatedVersionFiveDatabase() =
        createDatabaseFromSchema(SCHEMA_FIVE_ASSET, 5) { db ->
            db.execSQL(
                """
                INSERT INTO clients (
                    id, name, canonical_name, active_name_key, is_active,
                    created_at_epoch_ms, updated_at_epoch_ms, archived_at_epoch_ms
                ) VALUES ('client-1', 'Existing client', 'existing client', NULL, 0, 1000, 2000, 2000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO employees (
                    id, name, canonical_name, active_name_key, is_active,
                    created_at_epoch_ms, updated_at_epoch_ms, archived_at_epoch_ms
                ) VALUES ('employee-1', 'Existing Consultant', 'existing consultant',
                          NULL, 0, 1000, 2000, 2000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks (
                    id, series_id, client_id, description, hardware_software_purchases,
                    employee_id, employee_name_snapshot, work_type, billing_status, mileage,
                    work_date_epoch_day, zone_id, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'timed-task', 'lineage-1', 'client-1', 'Existing description',
                    'Existing expense', 'employee-1', 'Consultant Snapshot',
                    'ON_SITE', 'DO_NOT_CHARGE', '12.5', 20000, 'America/New_York', 1000, 3000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks (
                    id, series_id, client_id, description, hardware_software_purchases,
                    employee_id, employee_name_snapshot, work_type, billing_status, mileage,
                    work_date_epoch_day, zone_id, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'untimed-task', 'lineage-2', 'client-1', 'Untimed',
                    '', NULL, '', 'UNSPECIFIED', NULL, NULL,
                    20000, 'America/New_York', 4000, 4000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO work_intervals (
                    id, task_id, start_epoch_ms, stop_epoch_ms, active_slot,
                    was_manually_edited, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES ('open-interval', 'timed-task', 1000, NULL, 1, 0, 1000, 3000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO active_timer (
                    singleton_id, interval_id, task_id, boundary_zone_id,
                    created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (1, 'open-interval', 'timed-task', 'America/New_York', 1000, 3000)
                """.trimIndent(),
            )
        }

    private fun createPopulatedVersionThreeDatabase() =
        createDatabaseFromSchema(SCHEMA_THREE_ASSET, 3) { db ->
            db.execSQL(
                """
                INSERT INTO clients (
                    id, name, canonical_name, active_name_key, is_active,
                    created_at_epoch_ms, updated_at_epoch_ms, archived_at_epoch_ms
                ) VALUES ('client-1', 'Existing client', 'existing client', NULL, 0, 1000, 2000, 2000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO employees (
                    id, name, canonical_name, active_name_key, is_active,
                    created_at_epoch_ms, updated_at_epoch_ms, archived_at_epoch_ms
                ) VALUES ('employee-1', 'Existing Consultant', 'existing consultant',
                          NULL, 0, 1000, 2000, 2000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO daily_tasks (
                    id, series_id, client_id, description, hardware_software_purchases,
                    employee_id, employee_name_snapshot, work_type, mileage,
                    work_date_epoch_day, zone_id, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'legacy-three-task', 'legacy-three-lineage', 'client-1', 'Legacy task',
                    'Legacy expense', 'employee-1', 'Consultant Snapshot', 'ON_SITE', '12.5',
                    20000, 'America/New_York', 1000, 3000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO work_intervals (
                    id, task_id, ordinal, start_epoch_ms, stop_epoch_ms, active_slot,
                    was_manually_edited, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES ('legacy-three-open', 'legacy-three-task', 1, 1000, NULL, 1, 0, 1000, 3000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO active_timer (
                    singleton_id, interval_id, task_id, boundary_zone_id,
                    created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (1, 'legacy-three-open', 'legacy-three-task', 'America/New_York', 1000, 3000)
                """.trimIndent(),
            )
        }

    private fun createDatabaseFromSchema(
        schemaAsset: String,
        version: Int,
        populate: (SQLiteDatabase) -> Unit,
    ) {
        context.deleteDatabase(DATABASE_NAME)
        val schema =
            InstrumentationRegistry.getInstrumentation()
                .context.assets
                .open(schemaAsset)
                .bufferedReader()
                .use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        val tablePlaceholder = "$" + "{TABLE_NAME}"
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(tablePlaceholder, tableName))
                val indices = entity.getJSONArray("indices")
                for (indexIndex in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(indexIndex)
                            .getString("createSql")
                            .replace(tablePlaceholder, tableName),
                    )
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            for (index in 0 until setupQueries.length()) {
                db.execSQL(setupQueries.getString(index))
            }
            populate(db)
            db.version = version
        }
    }

    private fun SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val DATABASE_NAME = "schema-6-upgrade-core-test.db"
        const val SCHEMA_THREE_ASSET = "worq.order.data.local.WorqOrderDatabase/3.json"
        const val SCHEMA_FIVE_ASSET = "worq.order.data.local.WorqOrderDatabase/5.json"
    }
}
