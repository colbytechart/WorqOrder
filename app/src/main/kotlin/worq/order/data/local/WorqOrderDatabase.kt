package worq.order.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClientEntity::class,
        DailyTaskEntity::class,
        WorkIntervalEntity::class,
        ActiveTimerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WorqOrderDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    abstract fun taskDao(): TaskDao

    abstract fun workIntervalDao(): WorkIntervalDao

    abstract fun activeTimerDao(): ActiveTimerDao

    companion object {
        const val DATABASE_NAME = "worqorder.db"

        fun create(context: Context): WorqOrderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WorqOrderDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
