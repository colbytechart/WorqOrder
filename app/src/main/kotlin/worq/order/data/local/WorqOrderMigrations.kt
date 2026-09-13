package worq.order.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.charset.StandardCharsets
import java.util.UUID

object WorqOrderMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE daily_tasks
                    ADD COLUMN hardware_software_purchases TEXT NOT NULL DEFAULT ''
                    """.trimIndent(),
                )
            }
        }

    /**
     * Adds v0.2 employee/task metadata without risking cascades from rebuilding daily_tasks.
     *
     * The dependent interval and active-timer rows are copied to transaction-local tables before
     * their foreign-key chain is rebuilt. Room runs this migration in one transaction, so either
     * the complete graph is restored or the released v2 database remains untouched.
     */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS employees (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        canonical_name TEXT NOT NULL,
                        active_name_key TEXT,
                        is_active INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        archived_at_epoch_ms INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_employees_active_name_key " +
                        "ON employees (active_name_key)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_employees_active_sort " +
                        "ON employees (is_active, name, id)",
                )

                db.execSQL(
                    "CREATE TEMP TABLE work_intervals_v2_backup AS SELECT * FROM work_intervals",
                )
                db.execSQL(
                    "CREATE TEMP TABLE active_timer_v2_backup AS SELECT * FROM active_timer",
                )
                db.execSQL("DROP TABLE active_timer")
                db.execSQL("DROP TABLE work_intervals")
                db.execSQL("ALTER TABLE daily_tasks RENAME TO daily_tasks_v2_backup")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_tasks (
                        id TEXT NOT NULL,
                        series_id TEXT NOT NULL,
                        client_id TEXT NOT NULL,
                        description TEXT NOT NULL,
                        hardware_software_purchases TEXT NOT NULL DEFAULT '',
                        employee_id TEXT,
                        employee_name_snapshot TEXT NOT NULL DEFAULT '',
                        work_type TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                        mileage TEXT,
                        work_date_epoch_day INTEGER NOT NULL,
                        zone_id TEXT NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(client_id) REFERENCES clients(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(employee_id) REFERENCES employees(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO daily_tasks (
                        id, series_id, client_id, description,
                        hardware_software_purchases, employee_id,
                        employee_name_snapshot, work_type, mileage,
                        work_date_epoch_day, zone_id,
                        created_at_epoch_ms, updated_at_epoch_ms
                    )
                    SELECT
                        id, series_id, client_id, description,
                        hardware_software_purchases, NULL,
                        '', 'UNSPECIFIED', NULL,
                        work_date_epoch_day, zone_id,
                        created_at_epoch_ms, updated_at_epoch_ms
                    FROM daily_tasks_v2_backup
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE daily_tasks_v2_backup")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_daily_tasks_series_date_zone " +
                        "ON daily_tasks (series_id, work_date_epoch_day, zone_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_tasks_work_date_sort " +
                        "ON daily_tasks (work_date_epoch_day, created_at_epoch_ms, id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_tasks_client_id " +
                        "ON daily_tasks (client_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_tasks_employee_id " +
                        "ON daily_tasks (employee_id)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS work_intervals (
                        id TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        start_epoch_ms INTEGER NOT NULL,
                        stop_epoch_ms INTEGER,
                        active_slot INTEGER,
                        was_manually_edited INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(task_id) REFERENCES daily_tasks(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO work_intervals SELECT * FROM work_intervals_v2_backup")
                db.execSQL("DROP TABLE work_intervals_v2_backup")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_work_intervals_task_ordinal " +
                        "ON work_intervals (task_id, ordinal)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_work_intervals_task_start " +
                        "ON work_intervals (task_id, start_epoch_ms, ordinal, id)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_work_intervals_active_slot " +
                        "ON work_intervals (active_slot)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_work_intervals_id_task " +
                        "ON work_intervals (id, task_id)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS active_timer (
                        singleton_id INTEGER NOT NULL,
                        interval_id TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        boundary_zone_id TEXT NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(singleton_id),
                        FOREIGN KEY(interval_id, task_id)
                            REFERENCES work_intervals(id, task_id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO active_timer SELECT * FROM active_timer_v2_backup")
                db.execSQL("DROP TABLE active_timer_v2_backup")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_active_timer_interval_task " +
                        "ON active_timer (interval_id, task_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_active_timer_task_id " +
                        "ON active_timer (task_id)",
                )
            }
        }

    /** Adds nullable Billing Status; released and existing v0.2 tasks remain unassigned. */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_tasks ADD COLUMN billing_status TEXT",
                )
            }
        }

    /**
     * Converts the released many-interval schema into the zero-or-one-interval schema without
     * discarding or merging any historical interval.
     *
     * Room invokes migrations transactionally. Additional task IDs and timestamps are derived
     * exclusively from preserved values, so a rolled-back attempt produces the same rows when it
     * is retried.
     */
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val taskCountBefore = db.queryLong("SELECT COUNT(*) FROM daily_tasks")
                val intervalCountBefore = db.queryLong("SELECT COUNT(*) FROM work_intervals")
                val activeTimerCountBefore = db.queryLong("SELECT COUNT(*) FROM active_timer")
                if (activeTimerCountBefore !in 0L..1L) {
                    throw PersistenceInvariantException(
                        "Schema 4 contains $activeTimerCountBefore active-timer rows",
                    )
                }
                val openIntervalCountBefore =
                    db.queryLong(
                        """
                        SELECT COUNT(*)
                        FROM work_intervals
                        WHERE stop_epoch_ms IS NULL OR active_slot IS NOT NULL
                        """.trimIndent(),
                    )
                if (openIntervalCountBefore != activeTimerCountBefore) {
                    throw PersistenceInvariantException(
                        "Schema 4 open-interval and active-timer counts differ",
                    )
                }
                val invalidActivePairCount =
                    db.queryLong(
                        """
                        SELECT COUNT(*)
                        FROM active_timer AS active
                        LEFT JOIN work_intervals AS interval
                          ON interval.id = active.interval_id
                         AND interval.task_id = active.task_id
                        WHERE active.singleton_id != 1
                           OR interval.id IS NULL
                           OR interval.stop_epoch_ms IS NOT NULL
                           OR interval.active_slot != 1
                        """.trimIndent(),
                    )
                if (invalidActivePairCount != 0L) {
                    throw PersistenceInvariantException(
                        "Schema 4 active-timer state is inconsistent",
                    )
                }

                db.execSQL(
                    "CREATE TEMP TABLE active_timer_v4_backup AS SELECT * FROM active_timer",
                )
                db.execSQL("DROP TABLE active_timer")
                db.execSQL("DROP INDEX index_daily_tasks_series_date_zone")
                db.execSQL(
                    "CREATE INDEX index_daily_tasks_series_date_zone " +
                        "ON daily_tasks (series_id, work_date_epoch_day, zone_id)",
                )
                db.execSQL(
                    """
                    CREATE TEMP TABLE interval_task_v5_map (
                        interval_id TEXT NOT NULL PRIMARY KEY,
                        original_task_id TEXT NOT NULL,
                        migrated_task_id TEXT NOT NULL UNIQUE
                    )
                    """.trimIndent(),
                )

                var previousTaskId: String? = null
                var generatedTaskCount = 0L
                db.query(
                    """
                    SELECT
                        interval.id AS interval_id,
                        interval.task_id AS task_id,
                        interval.created_at_epoch_ms AS interval_created_at_epoch_ms,
                        interval.updated_at_epoch_ms AS interval_updated_at_epoch_ms,
                        task.created_at_epoch_ms AS task_created_at_epoch_ms,
                        task.updated_at_epoch_ms AS task_updated_at_epoch_ms
                    FROM work_intervals AS interval
                    INNER JOIN daily_tasks AS task ON task.id = interval.task_id
                    ORDER BY
                        interval.task_id ASC,
                        interval.start_epoch_ms ASC,
                        interval.ordinal ASC,
                        interval.id ASC
                    """.trimIndent(),
                ).use { cursor ->
                    val intervalIdColumn = cursor.getColumnIndexOrThrow("interval_id")
                    val taskIdColumn = cursor.getColumnIndexOrThrow("task_id")
                    val intervalCreatedColumn =
                        cursor.getColumnIndexOrThrow("interval_created_at_epoch_ms")
                    val intervalUpdatedColumn =
                        cursor.getColumnIndexOrThrow("interval_updated_at_epoch_ms")
                    val taskCreatedColumn =
                        cursor.getColumnIndexOrThrow("task_created_at_epoch_ms")
                    val taskUpdatedColumn =
                        cursor.getColumnIndexOrThrow("task_updated_at_epoch_ms")

                    while (cursor.moveToNext()) {
                        val intervalId = cursor.getString(intervalIdColumn)
                        val sourceTaskId = cursor.getString(taskIdColumn)
                        val migratedTaskId =
                            if (sourceTaskId != previousTaskId) {
                                sourceTaskId
                            } else {
                                val generatedId = deriveMigratedTaskId(sourceTaskId, intervalId)
                                if (
                                    db.queryLong(
                                        "SELECT COUNT(*) FROM daily_tasks WHERE id = ?",
                                        arrayOf(generatedId),
                                    ) != 0L
                                ) {
                                    throw PersistenceInvariantException(
                                        "Generated schema-5 task ID collides with existing task",
                                    )
                                }
                                val createdAtEpochMs =
                                    maxOf(
                                        cursor.getLong(taskCreatedColumn),
                                        cursor.getLong(intervalCreatedColumn),
                                    )
                                val updatedAtEpochMs =
                                    maxOf(
                                        createdAtEpochMs,
                                        cursor.getLong(taskUpdatedColumn),
                                        cursor.getLong(intervalUpdatedColumn),
                                    )
                                db.execSQL(
                                    """
                                    INSERT INTO daily_tasks (
                                        id,
                                        series_id,
                                        client_id,
                                        description,
                                        hardware_software_purchases,
                                        employee_id,
                                        employee_name_snapshot,
                                        work_type,
                                        billing_status,
                                        mileage,
                                        work_date_epoch_day,
                                        zone_id,
                                        created_at_epoch_ms,
                                        updated_at_epoch_ms
                                    )
                                    SELECT
                                        ?,
                                        series_id,
                                        client_id,
                                        description,
                                        hardware_software_purchases,
                                        employee_id,
                                        employee_name_snapshot,
                                        work_type,
                                        billing_status,
                                        mileage,
                                        work_date_epoch_day,
                                        zone_id,
                                        ?,
                                        ?
                                    FROM daily_tasks
                                    WHERE id = ?
                                    """.trimIndent(),
                                    arrayOf<Any>(
                                        generatedId,
                                        createdAtEpochMs,
                                        updatedAtEpochMs,
                                        sourceTaskId,
                                    ),
                                )
                                if (db.queryLong("SELECT changes()") != 1L) {
                                    throw PersistenceInvariantException(
                                        "Could not copy schema-4 task $sourceTaskId",
                                    )
                                }
                                generatedTaskCount += 1L
                                generatedId
                            }
                        db.execSQL(
                            """
                            INSERT INTO interval_task_v5_map (
                                interval_id,
                                original_task_id,
                                migrated_task_id
                            ) VALUES (?, ?, ?)
                            """.trimIndent(),
                            arrayOf<Any>(intervalId, sourceTaskId, migratedTaskId),
                        )
                        previousTaskId = sourceTaskId
                    }
                }

                db.execSQL(
                    """
                    CREATE TABLE work_intervals_v5 (
                        id TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        start_epoch_ms INTEGER NOT NULL,
                        stop_epoch_ms INTEGER,
                        active_slot INTEGER,
                        was_manually_edited INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(task_id) REFERENCES daily_tasks(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO work_intervals_v5 (
                        id,
                        task_id,
                        start_epoch_ms,
                        stop_epoch_ms,
                        active_slot,
                        was_manually_edited,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        interval.id,
                        mapping.migrated_task_id,
                        interval.start_epoch_ms,
                        interval.stop_epoch_ms,
                        interval.active_slot,
                        interval.was_manually_edited,
                        interval.created_at_epoch_ms,
                        interval.updated_at_epoch_ms
                    FROM work_intervals AS interval
                    INNER JOIN interval_task_v5_map AS mapping
                      ON mapping.interval_id = interval.id
                    """.trimIndent(),
                )
                if (db.queryLong("SELECT COUNT(*) FROM work_intervals_v5") != intervalCountBefore) {
                    throw PersistenceInvariantException(
                        "Schema-5 interval copy did not preserve every interval",
                    )
                }
                db.execSQL("DROP TABLE work_intervals")
                db.execSQL("ALTER TABLE work_intervals_v5 RENAME TO work_intervals")
                db.execSQL(
                    "CREATE UNIQUE INDEX index_work_intervals_task_id " +
                        "ON work_intervals (task_id)",
                )
                db.execSQL(
                    "CREATE INDEX index_work_intervals_task_start " +
                        "ON work_intervals (task_id, start_epoch_ms, id)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_work_intervals_active_slot " +
                        "ON work_intervals (active_slot)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_work_intervals_id_task " +
                        "ON work_intervals (id, task_id)",
                )

                db.execSQL(
                    """
                    CREATE TABLE active_timer (
                        singleton_id INTEGER NOT NULL,
                        interval_id TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        boundary_zone_id TEXT NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(singleton_id),
                        FOREIGN KEY(interval_id, task_id)
                            REFERENCES work_intervals(id, task_id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO active_timer (
                        singleton_id,
                        interval_id,
                        task_id,
                        boundary_zone_id,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        active.singleton_id,
                        active.interval_id,
                        mapping.migrated_task_id,
                        active.boundary_zone_id,
                        active.created_at_epoch_ms,
                        active.updated_at_epoch_ms
                    FROM active_timer_v4_backup AS active
                    INNER JOIN interval_task_v5_map AS mapping
                      ON mapping.interval_id = active.interval_id
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_active_timer_interval_task " +
                        "ON active_timer (interval_id, task_id)",
                )
                db.execSQL(
                    "CREATE INDEX index_active_timer_task_id ON active_timer (task_id)",
                )

                val taskCountAfter = db.queryLong("SELECT COUNT(*) FROM daily_tasks")
                if (taskCountAfter != taskCountBefore + generatedTaskCount) {
                    throw PersistenceInvariantException(
                        "Schema-5 task copy count is inconsistent",
                    )
                }
                if (db.queryLong("SELECT COUNT(*) FROM active_timer") != activeTimerCountBefore) {
                    throw PersistenceInvariantException(
                        "Schema-5 active timer was not preserved",
                    )
                }
                if (
                    db.queryLong(
                        """
                        SELECT COUNT(*)
                        FROM active_timer AS active
                        LEFT JOIN work_intervals AS interval
                          ON interval.id = active.interval_id
                         AND interval.task_id = active.task_id
                        WHERE interval.id IS NULL
                           OR interval.stop_epoch_ms IS NOT NULL
                           OR interval.active_slot != 1
                        """.trimIndent(),
                    ) != 0L
                ) {
                    throw PersistenceInvariantException(
                        "Schema-5 active timer does not own its open interval",
                    )
                }
                db.query("PRAGMA foreign_key_check").use { cursor ->
                    if (cursor.moveToFirst()) {
                        throw PersistenceInvariantException(
                            "Schema-5 migration produced a foreign-key violation",
                        )
                    }
                }

                db.execSQL("DROP TABLE active_timer_v4_backup")
                db.execSQL("DROP TABLE interval_task_v5_map")
            }
        }

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )

    internal fun deriveMigratedTaskId(
        sourceTaskId: String,
        intervalId: String,
    ): String =
        UUID.nameUUIDFromBytes(
            "worqorder:schema5:task:$sourceTaskId:interval:$intervalId"
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()

    private fun SupportSQLiteDatabase.queryLong(
        sql: String,
        bindArgs: Array<out Any?> = emptyArray(),
    ): Long =
        query(sql, bindArgs).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw PersistenceInvariantException("Expected a scalar query result")
            }
            cursor.getLong(0)
        }
}
