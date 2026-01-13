package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiGroup
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.VisibleNotiRecord

@Database(
    entities = [NotiUnit::class, NotiRecord::class, NotiAction::class, ReminderUnit::class, NotiGroup::class],
    views = [VisibleNotiRecord::class],
    version = 22,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun reminderListDao(): ReminderListDao
    abstract fun groupDao(): NotiGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_1_2)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_2_3)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_3_4)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_4_5)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_5_6)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_6_7)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_7_8)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_8_9)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_9_10)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_10_11)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_11_12)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_12_13)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_13_14)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_14_15)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_15_16)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_16_17)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_17_18)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_18_19)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_19_20)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_20_21)
                .addMigrations(org.muilab.notigpt.database.room.AppDatabaseMigrations.MIGRATION_21_22)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}