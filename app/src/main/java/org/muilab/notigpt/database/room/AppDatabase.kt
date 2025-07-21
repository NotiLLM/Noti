package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.VisibleNotiRecord

// In database/room/AppDatabase.kt

@Database(
    entities = [NotiUnit::class, NotiRecord::class, NotiAction::class],
    views = [VisibleNotiRecord::class], // <-- Add the view here
    version = 3, // <-- Increment the version number
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        // --- START MIGRATION DEFINITION ---

        // Define the migration from version 1 to 2.
        // Since we are only adding a view, the migration logic is empty.
        // Keep the old migration for users who might still be on version 1
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """)
            }
        }

        // --- THIS IS THE NEW REPAIR MIGRATION ---
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """)
            }
        }

        // --- END MIGRATION DEFINITION ---

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                // Remove any old callbacks if they are still there
                .addMigrations(MIGRATION_1_2) // <-- Add the migration here
                .addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}