package worq.order.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
        )
}
