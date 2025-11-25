package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.VisibleNotiRecord
import org.muilab.notigpt.model.features.TaskUnit
import org.muilab.notigpt.database.room.TaskListDao

// In database/room/AppDatabase.kt

@Database(
    entities = [NotiUnit::class, NotiRecord::class, NotiAction::class, TaskUnit::class],
    views = [VisibleNotiRecord::class], // <-- Add the view here
    version = 14, // bumped to add index on noti_record for faster queries
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun taskListDao(): TaskListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        // --- START MIGRATION DEFINITION ---

        // Define the migration from version 1 to 2.
        // Since we are only adding a view, the migration logic is empty.
        // Keep the old migration for users who might still be on version 1
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """)
            }
        }

        // --- THIS IS THE NEW REPAIR MIGRATION ---
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add int entry named taskState to noti_drawer table, initial value is 0
                db.execSQL("""
                    ALTER TABLE noti_drawer ADD COLUMN taskState INTEGER NOT NULL DEFAULT 0
                """)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add long entry named lastAppResumeTime to noti_action table, initial value is 0
                db.execSQL("""
                    ALTER TABLE notiAction ADD COLUMN lastAppResumeTime INTEGER NOT NULL DEFAULT 0
                """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add string entry named metadata to noti_action table, initial value is ""
                db.execSQL(
                    """
                    ALTER TABLE notiAction ADD COLUMN metadata TEXT NOT NULL DEFAULT ""
                """
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add long entry to noti_drawer table, initial value is 0
                db.execSQL(
                    """
                    ALTER TABLE noti_drawer ADD COLUMN lastSyncTime INTEGER NOT NULL DEFAULT 0
                """
                )
            }
        }

        // Migration to add the new task_list table introduced by TaskUnit entity
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_list` (
                        `taskId` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `isVisible` INTEGER NOT NULL,
                        `taskDescription` TEXT NOT NULL,
                        `deadlineTimestamp` INTEGER NOT NULL,
                        `estimatedCompletionTime` INTEGER NOT NULL,
                        `associatedNotis` TEXT NOT NULL,
                        `userEdited` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`taskId`)
                    )
                    """
                )
            }
        }

        // Migration 8 -> 9: add taskScanned & taskExtracted to noti_record, and NotiTaskAttr columns to noti_drawer
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskScanned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtracted INTEGER NOT NULL DEFAULT 0")

                // Add embedded NotiTaskAttr fields into noti_drawer table
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN shouldExtractTask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN hasGenuineTask INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 9 -> 10: add userEdited to task_list
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the userEdited column to the task_list table
                db.execSQL(
                    """
                    ALTER TABLE task_list ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0
                    """
                )
            }
        }

        // Migration 10 -> 11: rename taskDetectionChecked/taskExtractionChecked to taskScanned/taskExtracted
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record RENAME COLUMN taskDetectionChecked TO taskScanned")
                db.execSQL("ALTER TABLE noti_record RENAME COLUMN taskExtractionChecked TO taskExtracted")
            }
        }

        // Migration 11 -> 12: add taskExtractionClaimed column to noti_record to support atomic claiming
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtractionClaimed INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 12 -> 13: add taskExtractionClaimedAt column (timestamp) to noti_record
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtractionClaimedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 13 -> 14: rebuild the noti_record table to ensure exact schema matches Room's expected entity.
        // We create a new table with the desired schema, copy data from the old table (using COALESCE where necessary),
        // drop the old table, rename the new table, and recreate the index.
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Create a new table with the schema Room expects for NotiRecord.
                // Note: only task-related integer columns have SQL DEFAULT 0 to match entity @ColumnInfo(defaultValue = "0").
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `noti_record_new` (
                        `notiRecordId` TEXT NOT NULL,
                        `notiKey` TEXT NOT NULL,
                        `whenTime` INTEGER NOT NULL,
                        `postTime` INTEGER NOT NULL,
                        `person` TEXT NOT NULL,
                        `extraTitle` TEXT NOT NULL,
                        `extraBigTitle` TEXT NOT NULL,
                        `extraConversationTitle` TEXT NOT NULL,
                        `extraBigText` TEXT NOT NULL,
                        `extraText` TEXT NOT NULL,
                        `extraTextLines` TEXT NOT NULL,
                        `extraSummaryText` TEXT NOT NULL,
                        `extraInfoText` TEXT NOT NULL,
                        `extraSubText` TEXT NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `isVisible` INTEGER NOT NULL,
                        `taskScanned` INTEGER NOT NULL DEFAULT 0,
                        `taskExtracted` INTEGER NOT NULL DEFAULT 0,
                        `taskExtractionClaimed` INTEGER NOT NULL DEFAULT 0,
                        `taskExtractionClaimedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`notiRecordId`)
                    )
                    """
                )

                // 2) Copy data from existing noti_record into noti_record_new. Use COALESCE to provide fallback values.
                db.execSQL(
                    """
                    INSERT INTO noti_record_new (
                        notiRecordId, notiKey, whenTime, postTime, person, extraTitle, extraBigTitle, extraConversationTitle,
                        extraBigText, extraText, extraTextLines, extraSummaryText, extraInfoText, extraSubText,
                        isRead, isVisible, taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt
                    )
                    SELECT
                        notiRecordId,
                        notiKey,
                        COALESCE(whenTime, 0),
                        COALESCE(postTime, 0),
                        COALESCE(person, ''),
                        COALESCE(extraTitle, ''),
                        COALESCE(extraBigTitle, ''),
                        COALESCE(extraConversationTitle, ''),
                        COALESCE(extraBigText, ''),
                        COALESCE(extraText, ''),
                        COALESCE(extraTextLines, ''),
                        COALESCE(extraSummaryText, ''),
                        COALESCE(extraInfoText, ''),
                        COALESCE(extraSubText, ''),
                        COALESCE(isRead, 0),
                        COALESCE(isVisible, 1),
                        COALESCE(taskScanned, 0),
                        COALESCE(taskExtracted, 0),
                        COALESCE(taskExtractionClaimed, 0),
                        COALESCE(taskExtractionClaimedAt, 0)
                    FROM noti_record
                    """
                )

                // 3) Drop old table and rename the new one
                db.execSQL("DROP TABLE IF EXISTS noti_record")
                db.execSQL("ALTER TABLE noti_record_new RENAME TO noti_record")

                // 4) Recreate the index to match the entity's Index annotation (no DESC in DDL so Room's TableInfo matches).
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_notiKey_whenTime ON noti_record(notiKey, whenTime)")
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
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .addMigrations(MIGRATION_10_11)
                .addMigrations(MIGRATION_11_12)
                .addMigrations(MIGRATION_12_13)
                .addMigrations(MIGRATION_13_14)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}