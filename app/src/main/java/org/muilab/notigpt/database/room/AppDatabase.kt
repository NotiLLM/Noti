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
import org.muilab.notigpt.model.notifications.NotiGroup

@Database(
    entities = [NotiUnit::class, NotiRecord::class, NotiAction::class, TaskUnit::class, NotiGroup::class],
    views = [VisibleNotiRecord::class],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawerDao(): NotiDrawerDao
    abstract fun recordDao(): NotiRecordDao
    abstract fun actionDao(): NotiActionDao
    abstract fun taskListDao(): TaskListDao
    abstract fun groupDao(): NotiGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "notigpt_app.db"

        // ... existing migrations MIGRATION_1_2 to MIGRATION_13_14 ...
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """)
            }
        }
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
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN taskState INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notiAction ADD COLUMN lastAppResumeTime INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notiAction ADD COLUMN metadata TEXT NOT NULL DEFAULT \"\"")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN lastSyncTime INTEGER NOT NULL DEFAULT 0")
            }
        }
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
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskScanned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtracted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN shouldExtractTask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE noti_drawer ADD COLUMN hasGenuineTask INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_list ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record RENAME COLUMN taskDetectionChecked TO taskScanned")
                db.execSQL("ALTER TABLE noti_record RENAME COLUMN taskExtractionChecked TO taskExtracted")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtractionClaimed INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noti_record ADD COLUMN taskExtractionClaimedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
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
                """)
                db.execSQL("""
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
                """)
                db.execSQL("DROP TABLE IF EXISTS noti_record")
                db.execSQL("ALTER TABLE noti_record_new RENAME TO noti_record")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_notiKey_whenTime ON noti_record(notiKey, whenTime)")
            }
        }

        // --- FIXED MIGRATION 14->15 ---
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create NotiGroup table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `noti_group` (
                        `groupId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isExpanded` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`groupId`)
                    )
                """)

                // 2. Create new noti_drawer table (With 'groupId', WITHOUT 'sortPosition'/'appCategorySortPosition')
                // Note: The schema here must exactly match the expected schema from the crash log.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `noti_drawer_new` (
                        `notiKey` TEXT NOT NULL,
                        `appCategory` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `groupId` TEXT,
                        `groupKey` TEXT NOT NULL,
                        `hasGenuineTask` INTEGER NOT NULL,
                        `hashKey` INTEGER NOT NULL,
                        `icon` TEXT NOT NULL,
                        `isAppGroup` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `isCompletelyRead` INTEGER NOT NULL,
                        `isGroupChat` INTEGER NOT NULL,
                        `isPeople` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `isVisible` INTEGER NOT NULL,
                        `largeIcon` TEXT NOT NULL,
                        `lastSyncTime` INTEGER NOT NULL,
                        `lastUpdateTime` INTEGER NOT NULL,
                        `pkgName` TEXT NOT NULL,
                        `shouldExtractTask` INTEGER NOT NULL,
                        `sortKey` TEXT NOT NULL,
                        `sortScore` REAL NOT NULL,
                        `summary` TEXT NOT NULL,
                        `taskState` INTEGER NOT NULL,
                        PRIMARY KEY(`notiKey`)
                    )
                """)

                // 3. Copy data from old table to new table.
                // We omit 'sortPosition' and 'appCategorySortPosition' from the select.
                // 'groupId' in the new table is not present in the old, so it defaults to NULL (which is correct).
                db.execSQL("""
                    INSERT INTO `noti_drawer_new` (
                        notiKey, appCategory, appName, category, explanation, 
                        groupKey, hasGenuineTask, hashKey, icon, isAppGroup, 
                        isArchived, isCompletelyRead, isGroupChat, isPeople, isPinned, 
                        isVisible, largeIcon, lastSyncTime, lastUpdateTime, pkgName, 
                        shouldExtractTask, sortKey, sortScore, summary, taskState
                    )
                    SELECT 
                        notiKey, appCategory, appName, category, explanation, 
                        groupKey, hasGenuineTask, hashKey, icon, isAppGroup, 
                        isArchived, isCompletelyRead, isGroupChat, isPeople, isPinned, 
                        isVisible, largeIcon, lastSyncTime, lastUpdateTime, pkgName, 
                        shouldExtractTask, sortKey, sortScore, summary, taskState
                    FROM `noti_drawer`
                """)

                // 4. Drop the old table
                db.execSQL("DROP TABLE `noti_drawer`")

                // 5. Rename the new table to the original name
                db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")

                // 6. Create the index required by the @Index annotation in NotiUnit
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_drawer_groupId` ON `noti_drawer` (`groupId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
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
                .addMigrations(MIGRATION_14_15)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}