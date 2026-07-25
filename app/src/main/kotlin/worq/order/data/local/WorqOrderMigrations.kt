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

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
        )
}
