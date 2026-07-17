package org.muilab.notigpt.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ordered Room schema migrations for installed app databases.
 *
 * Keep these migrations compatible with real user upgrade paths. New schema cleanup should usually
 * be expressed as a later migration instead of rewriting assumptions in active models only.
 */
object AppDatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                    CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS 
                    SELECT * FROM noti_record WHERE isVisible = 1
                """.trimIndent()
            )
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
                        `deadlineAtMs` INTEGER NOT NULL,
                        `estimatedCompletionTime` INTEGER NOT NULL,
                        `associatedNotis` TEXT NOT NULL,
                        `userEdited` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`taskId`)
                    )
                """.trimIndent()
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
                """.trimIndent()
            )
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
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS noti_record")
            db.execSQL("ALTER TABLE noti_record_new RENAME TO noti_record")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_notiKey_whenTime ON noti_record(notiKey, whenTime)")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `noti_group` (
                        `groupId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isExpanded` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`groupId`)
                    )
                """.trimIndent()
            )

            db.execSQL(
                """
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
                """.trimIndent()
            )

            db.execSQL(
                """
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
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `noti_drawer`")
            db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_drawer_groupId` ON `noti_drawer` (`groupId`)")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE noti_drawer ADD COLUMN isSetToTop INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE noti_drawer ADD COLUMN setToTopTime INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
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
                        `isRead` INTEGER NOT NULL,
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
                        `isSetToTop` INTEGER NOT NULL,
                        `setToTopTime` INTEGER NOT NULL,
                        PRIMARY KEY(`notiKey`)
                    )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_drawer_new_groupId` ON `noti_drawer_new` (`groupId`)")

            db.execSQL(
                """
                    INSERT INTO `noti_drawer_new` (
                        notiKey, appCategory, appName, category, explanation, groupId, groupKey, 
                        hasGenuineTask, hashKey, icon, isAppGroup, isArchived, isRead, isGroupChat, 
                        isPeople, isPinned, isVisible, largeIcon, lastSyncTime, lastUpdateTime, 
                        pkgName, shouldExtractTask, sortKey, sortScore, summary, isSetToTop, setToTopTime
                    )
                    SELECT 
                        notiKey, appCategory, appName, category, explanation, groupId, groupKey, 
                        hasGenuineTask, hashKey, icon, isAppGroup, 
                        isArchived, isCompletelyRead, isGroupChat, isPeople, isPinned, 
                        isVisible, largeIcon, lastSyncTime, lastUpdateTime, 
                        pkgName, shouldExtractTask, sortKey, sortScore, summary, isSetToTop, setToTopTime
                    FROM `noti_drawer`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_drawer`")
            db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_drawer_groupId` ON `noti_drawer` (`groupId`)")

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
                        `isDismissed` INTEGER NOT NULL DEFAULT 0,
                        `taskScanned` INTEGER NOT NULL DEFAULT 0,
                        `taskExtracted` INTEGER NOT NULL DEFAULT 0,
                        `taskExtractionClaimed` INTEGER NOT NULL DEFAULT 0,
                        `taskExtractionClaimedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`notiRecordId`)
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                    INSERT INTO `noti_record_new` (
                        notiRecordId, notiKey, whenTime, postTime, person,
                        extraTitle, extraBigTitle, extraConversationTitle,
                        extraBigText, extraText, extraTextLines, extraSummaryText,
                        extraInfoText, extraSubText,
                        isDismissed,
                        taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt
                    )
                    SELECT
                        notiRecordId, notiKey, whenTime, postTime, person,
                        extraTitle, extraBigTitle, extraConversationTitle,
                        extraBigText, extraText, extraTextLines, extraSummaryText,
                        extraInfoText, extraSubText,
                        CASE WHEN COALESCE(isVisible, 1) = 1 THEN 0 ELSE 1 END AS isDismissed,
                        taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt
                    FROM `noti_record`
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `noti_record`")
            db.execSQL("ALTER TABLE `noti_record_new` RENAME TO `noti_record`")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_notiKey_whenTime ON noti_record(notiKey, whenTime)")

            // --- View: VisibleNotiRecord should reflect isDismissed semantics.
            db.execSQL("DROP VIEW IF EXISTS `VisibleNotiRecord`")
            db.execSQL("CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS SELECT * FROM noti_record WHERE isDismissed = 0")
        }
    }

    /**
     * NOTE: Versions 17+ existed in some released builds.
     * Keep these migrations as no-ops so Room has a valid path when opening older DBs.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // no-op
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // no-op
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // no-op
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // no-op
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Manual drawer ordering: -1 means unset
            db.execSQL("ALTER TABLE noti_drawer ADD COLUMN sortPosition INTEGER NOT NULL DEFAULT -1")
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rename columns (SQLite 3.25+; Android has it for a long time now)
            db.execSQL("ALTER TABLE noti_drawer RENAME COLUMN hasGenuineTask TO hasTask")
            db.execSQL("ALTER TABLE noti_drawer RENAME COLUMN shouldExtractTask TO shouldExtractReminder")

            // Add new column (booleans are INTEGER 0/1 in SQLite)
            db.execSQL("ALTER TABLE noti_drawer ADD COLUMN hasMemo INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `esm_extraction_snapshot` (
                    `snapshotId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `savedItemId` TEXT,
                    `payloadJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`snapshotId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_snap_status_time` ON `esm_extraction_snapshot` (`status`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_snap_savedItemId` ON `esm_extraction_snapshot` (`savedItemId`)")
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Link extracted reminders to the snapshot captured at extraction time.
            db.execSQL("ALTER TABLE saved_item ADD COLUMN sourceExtractionSnapshotId TEXT")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rename/remove ESM-named extraction snapshot table to reminder-owned naming.

            // 1) Create new table.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminder_extraction_snapshot` (
                    `snapshotId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `savedItemId` TEXT,
                    `payloadJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`snapshotId`)
                )
                """.trimIndent()
            )

            // 2) Copy data (if old table exists).
            // SQLite doesn't have IF EXISTS for INSERT FROM, so we rely on the table existing in v25.
            db.execSQL(
                """
                INSERT OR REPLACE INTO `reminder_extraction_snapshot` (`snapshotId`,`status`,`savedItemId`,`payloadJson`,`createdAt`)
                SELECT `snapshotId`,`status`,`savedItemId`,`payloadJson`,`createdAt` FROM `esm_extraction_snapshot`
                """.trimIndent()
            )

            // 3) Drop old indexes/table.
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_snap_status_time`")
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_snap_savedItemId`")
            db.execSQL("DROP TABLE IF EXISTS `esm_extraction_snapshot`")

            // 4) Create new indexes.
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_reminder_snap_status_time` ON `reminder_extraction_snapshot` (`status`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_reminder_snap_savedItemId` ON `reminder_extraction_snapshot` (`savedItemId`)")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_item ADD COLUMN isVisible INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_item ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN humanEditCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN deletedAtMs INTEGER")
        }
    }

    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS extraction_preferences (
                        id TEXT NOT NULL PRIMARY KEY,
                        statement TEXT NOT NULL,
                        preferenceType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
            )
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS preference_conflicts (
                        conflictId TEXT NOT NULL PRIMARY KEY,
                        description TEXT NOT NULL,
                        involvedPreferenceIds TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
            )
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_item ADD COLUMN isEvent INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN startAtMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN endAtMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE noti_drawer ADD COLUMN hasEvent INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_item ADD COLUMN buttons TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN isViewed INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN sortScore REAL NOT NULL DEFAULT 50.0")
            db.execSQL("ALTER TABLE saved_item ADD COLUMN reRankHistory TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS user_contexts (
                        id TEXT NOT NULL PRIMARY KEY,
                        statement TEXT NOT NULL,
                        category TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
            )
        }
    }

    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS saved_sub_item (
                        savedSubItemId TEXT NOT NULL PRIMARY KEY,
                        parentSavedItemId TEXT NOT NULL,
                        title TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        isTask INTEGER NOT NULL DEFAULT 1,
                        isEvent INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        deadlineAtMs INTEGER NOT NULL DEFAULT 0,
                        startAtMs INTEGER NOT NULL DEFAULT 0,
                        endAtMs INTEGER NOT NULL DEFAULT 0,
                        buttons TEXT NOT NULL DEFAULT '[]',
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        lastUpdateTimestamp INTEGER NOT NULL,
                        isVisible INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(parentSavedItemId) REFERENCES saved_item(savedItemId) ON DELETE NO ACTION
                    )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_subtask_parent ON saved_sub_item (parentSavedItemId)")
        }
    }

    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_status_available`")
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_status_expires`")
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_savedItemId`")
            db.execSQL("DROP INDEX IF EXISTS `idx_esm_answer_instance`")
            db.execSQL("DROP TABLE IF EXISTS `esm_answer_event`")
            db.execSQL("DROP TABLE IF EXISTS `esm_instance`")
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_noti_drawer_groupId`")
            db.execSQL("DROP TABLE IF EXISTS `noti_group`")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_drawer_new` (
                    `notiKey` TEXT NOT NULL,
                    `pkgName` TEXT NOT NULL,
                    `hashKey` INTEGER NOT NULL,
                    `groupKey` TEXT NOT NULL,
                    `isAppGroup` INTEGER NOT NULL,
                    `isGroupChat` INTEGER NOT NULL,
                    `sortKey` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `lastUpdateTime` INTEGER NOT NULL,
                    `lastSyncTime` INTEGER NOT NULL,
                    `icon` TEXT NOT NULL,
                    `largeIcon` TEXT NOT NULL,
                    `isPeople` INTEGER NOT NULL,
                    `isPinned` INTEGER NOT NULL,
                    `isArchived` INTEGER NOT NULL,
                    `isDismissed` INTEGER NOT NULL,
                    `isRead` INTEGER NOT NULL,
                    `isSetToTop` INTEGER NOT NULL,
                    `setToTopTime` INTEGER NOT NULL,
                    `sortPosition` INTEGER NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `sortScore` REAL NOT NULL,
                    `shouldExtractReminder` INTEGER NOT NULL,
                    `hasTask` INTEGER NOT NULL,
                    `hasMemo` INTEGER NOT NULL,
                    `hasEvent` INTEGER NOT NULL,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO `noti_drawer_new` (
                    notiKey, pkgName, hashKey, groupKey, isAppGroup, isGroupChat, sortKey,
                    appName, lastUpdateTime, lastSyncTime, icon, largeIcon, isPeople,
                    isPinned, isArchived, isDismissed, isRead, isSetToTop, setToTopTime,
                    sortPosition, explanation, summary, sortScore, shouldExtractReminder,
                    hasTask, hasMemo, hasEvent
                )
                SELECT
                    notiKey, pkgName, hashKey, groupKey, isAppGroup, isGroupChat, sortKey,
                    appName, lastUpdateTime, lastSyncTime, icon, largeIcon, isPeople,
                    isPinned, isArchived, isDismissed, isRead, isSetToTop, setToTopTime,
                    sortPosition, explanation, summary, sortScore, shouldExtractReminder,
                    hasTask, hasMemo, hasEvent
                FROM `noti_drawer`
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `noti_drawer`")
            db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")
        }
    }

    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item` (
                    `savedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `isCompleted` INTEGER NOT NULL,
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `estimatedCompletionTime` INTEGER NOT NULL,
                    `associatedNotis` TEXT NOT NULL,
                    `sourceExtractionSnapshotId` TEXT,
                    `origin` TEXT NOT NULL,
                    `humanEditCount` INTEGER NOT NULL,
                    `deletedAtMs` INTEGER,
                    `userEdited` INTEGER NOT NULL,
                    `isVisible` INTEGER NOT NULL,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `isViewed` INTEGER NOT NULL DEFAULT 1,
                    `isPinned` INTEGER NOT NULL DEFAULT 0,
                    `sortScore` REAL NOT NULL DEFAULT 50.0,
                    `reRankHistory` TEXT NOT NULL DEFAULT '[]',
                    PRIMARY KEY(`savedItemId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_item` (
                    savedItemId, title, content, itemType, isCompleted, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, associatedNotis,
                    sourceExtractionSnapshotId, origin, humanEditCount, deletedAtMs, userEdited,
                    isVisible, buttons, isViewed, isPinned, sortScore, reRankHistory
                )
                SELECT
                    reminderId, reminderTitle, reminderContent,
                    CASE WHEN isTask = 1 THEN 'task' ELSE 'keep' END,
                    isCompleted, lastUpdateTimestamp, deadlineTimestamp, startTime, endTime,
                    estimatedCompletionTime, associatedNotis, extractionSnapshotId, origin,
                    humanEditCount, deletedAtMs, userEdited, isVisible, buttons, isViewed,
                    isPinned, sortScore, reRankHistory
                FROM `reminder_list`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `reminder_list`")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_sub_item` (
                    `savedSubItemId` TEXT NOT NULL,
                    `parentSavedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `isCompleted` INTEGER NOT NULL DEFAULT 0,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `isVisible` INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY(`savedSubItemId`),
                    FOREIGN KEY(`parentSavedItemId`) REFERENCES `saved_item`(`savedItemId`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_sub_item` (
                    savedSubItemId, parentSavedItemId, title, description, itemType, isCompleted,
                    deadlineAtMs, startAtMs, endAtMs, buttons, sortOrder, createdAt,
                    lastUpdateTimestamp, isVisible
                )
                SELECT
                    subTaskId, parentReminderId, title, description,
                    CASE WHEN isTask = 1 THEN 'task' ELSE 'keep' END,
                    isCompleted, deadlineTimestamp, startTime, endTime, buttons, sortOrder,
                    createdAt, lastUpdateTimestamp, isVisible
                FROM `sub_tasks`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `sub_tasks`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_saved_sub_item_parent` ON `saved_sub_item` (`parentSavedItemId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminder` (
                    `reminderId` TEXT NOT NULL,
                    `remindAtMs` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `status` TEXT NOT NULL DEFAULT 'scheduled',
                    `sourceType` TEXT NOT NULL DEFAULT 'saved_item',
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    `seenAtMs` INTEGER,
                    `cancelledAtMs` INTEGER,
                    PRIMARY KEY(`reminderId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_saved_item_ref` (`reminderId` TEXT NOT NULL, `savedItemId` TEXT NOT NULL, PRIMARY KEY(`reminderId`, `savedItemId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_noti_record_ref` (`reminderId` TEXT NOT NULL, `notiRecordId` TEXT NOT NULL, PRIMARY KEY(`reminderId`, `notiRecordId`))")
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item_new` (
                    `savedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `state` TEXT NOT NULL DEFAULT 'saved',
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `estimatedCompletionTime` INTEGER NOT NULL,
                    `associatedNotis` TEXT NOT NULL,
                    `sourceExtractionSnapshotId` TEXT,
                    `origin` TEXT NOT NULL,
                    `humanEditCount` INTEGER NOT NULL,
                    `deletedAtMs` INTEGER,
                    `userEdited` INTEGER NOT NULL,
                    `isVisible` INTEGER NOT NULL,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `isViewed` INTEGER NOT NULL DEFAULT 1,
                    `isPinned` INTEGER NOT NULL DEFAULT 0,
                    `sortScore` REAL NOT NULL DEFAULT 50.0,
                    `reRankHistory` TEXT NOT NULL DEFAULT '[]',
                    PRIMARY KEY(`savedItemId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_item_new` (
                    savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, associatedNotis,
                    sourceExtractionSnapshotId, origin, humanEditCount, deletedAtMs, userEdited,
                    isVisible, buttons, isViewed, isPinned, sortScore, reRankHistory
                )
                SELECT
                    savedItemId, title, content, itemType,
                    CASE WHEN itemType = 'task' AND isCompleted = 1 THEN 'completed' ELSE 'saved' END,
                    lastUpdateTimestamp, deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime,
                    associatedNotis, sourceExtractionSnapshotId, origin, humanEditCount, deletedAtMs,
                    userEdited, isVisible, buttons, isViewed, isPinned, sortScore, reRankHistory
                FROM `saved_item`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `saved_item`")
            db.execSQL("ALTER TABLE `saved_item_new` RENAME TO `saved_item`")
            val snapshotHasReminderId = db.query("PRAGMA table_info(`reminder_extraction_snapshot`)").use { cursor ->
                var hasReminderId = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "reminderId") {
                        hasReminderId = true
                    }
                }
                hasReminderId
            }
            if (snapshotHasReminderId) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminder_extraction_snapshot_new` (
                        `snapshotId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `savedItemId` TEXT,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`snapshotId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `reminder_extraction_snapshot_new` (`snapshotId`,`status`,`savedItemId`,`payloadJson`,`createdAt`)
                    SELECT `snapshotId`,`status`,`reminderId`,`payloadJson`,`createdAt` FROM `reminder_extraction_snapshot`
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS `idx_reminder_snap_reminderId`")
                db.execSQL("DROP INDEX IF EXISTS `idx_reminder_snap_savedItemId`")
                db.execSQL("DROP INDEX IF EXISTS `idx_reminder_snap_status_time`")
                db.execSQL("DROP TABLE `reminder_extraction_snapshot`")
                db.execSQL("ALTER TABLE `reminder_extraction_snapshot_new` RENAME TO `reminder_extraction_snapshot`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_reminder_snap_status_time` ON `reminder_extraction_snapshot` (`status`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_reminder_snap_savedItemId` ON `reminder_extraction_snapshot` (`savedItemId`)")
            }
            db.execSQL("ALTER TABLE noti_record ADD COLUMN isNew INTEGER NOT NULL DEFAULT 1")
        }
    }



    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP VIEW IF EXISTS `VisibleNotiRecord`")
            db.execSQL("DROP INDEX IF EXISTS `idx_record_notiKey_whenTime`")
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
                    `isDismissed` INTEGER NOT NULL DEFAULT 0,
                    `taskScanned` INTEGER NOT NULL DEFAULT 0,
                    `taskExtracted` INTEGER NOT NULL DEFAULT 0,
                    `taskExtractionClaimed` INTEGER NOT NULL DEFAULT 0,
                    `taskExtractionClaimedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiRecordId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_record_new` (
                    notiRecordId, notiKey, whenTime, postTime, person,
                    extraTitle, extraBigTitle, extraConversationTitle,
                    extraBigText, extraText, extraTextLines, extraSummaryText,
                    extraInfoText, extraSubText, isDismissed,
                    taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt
                )
                SELECT
                    notiRecordId, notiKey, whenTime, postTime, person,
                    extraTitle, extraBigTitle, extraConversationTitle,
                    extraBigText, extraText, extraTextLines, extraSummaryText,
                    extraInfoText, extraSubText, isDismissed,
                    taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt
                FROM `noti_record`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_record`")
            db.execSQL("ALTER TABLE `noti_record_new` RENAME TO `noti_record`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_record_notiKey_whenTime` ON `noti_record` (`notiKey`, `whenTime`)")
            db.execSQL("CREATE VIEW IF NOT EXISTS `VisibleNotiRecord` AS SELECT * FROM noti_record WHERE isDismissed = 0")
        }
    }

    /**
     * v39 -> v40: Replace the reminder extraction snapshot mechanism with a normalized
     * noti-to-saved-item join table as the single source of truth for provenance.
     *
     * Steps:
     *  1. Create `noti_saved_item_link` (matches the Room-generated schema for [NotiSavedItemLink]).
     *  2. Backfill links from each saved_item's `associatedNotis` set (split "{notiKey}_{postTime}" to
     *     recover notiKey; type mirrors the row's itemType).
     *  3. Recreate `saved_item` without the now-removed `associatedNotis`, `sourceExtractionSnapshotId`,
     *     and `isPinned` columns.
     *  4. Drop the obsolete `reminder_extraction_snapshot` table and its indices.
     */
    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create the join table + indices (must match Room's generated schema exactly).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_saved_item_link` (
                    `notiKey` TEXT NOT NULL,
                    `notiRecordId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `savedItemId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`notiRecordId`, `savedItemId`),
                    FOREIGN KEY(`savedItemId`) REFERENCES `saved_item`(`savedItemId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_saved_item_link_savedItemId` ON `noti_saved_item_link` (`savedItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_saved_item_link_notiKey` ON `noti_saved_item_link` (`notiKey`)")

            // 2. Backfill from the existing associatedNotis JSON set. Parse in Kotlin since SQLite
            //    json_each is not reliably available at minSdk 29.
            val now = System.currentTimeMillis()
            db.query("SELECT `savedItemId`, `itemType`, `associatedNotis` FROM `saved_item`").use { cursor ->
                val idIdx = cursor.getColumnIndex("savedItemId")
                val typeIdx = cursor.getColumnIndex("itemType")
                val notisIdx = cursor.getColumnIndex("associatedNotis")
                while (cursor.moveToNext()) {
                    val savedItemId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    if (savedItemId.isNullOrBlank()) continue
                    val itemType = if (typeIdx >= 0 && !cursor.isNull(typeIdx)) cursor.getString(typeIdx) else "task"
                    val notisJson = if (notisIdx >= 0 && !cursor.isNull(notisIdx)) cursor.getString(notisIdx) else "[]"
                    val recordIds = try {
                        val arr = JSONArray(notisJson)
                        (0 until arr.length()).map { arr.optString(it) }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    for (recordId in recordIds) {
                        if (recordId.isNullOrBlank()) continue
                        val notiKey = recordId.substringBeforeLast("_")
                        db.execSQL(
                            "INSERT OR IGNORE INTO `noti_saved_item_link` (`notiKey`,`notiRecordId`,`type`,`savedItemId`,`createdAt`) VALUES (?,?,?,?,?)",
                            arrayOf(notiKey, recordId, itemType, savedItemId, now)
                        )
                    }
                }
            }

            // 3. Recreate saved_item without associatedNotis / sourceExtractionSnapshotId / isPinned.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item_new` (
                    `savedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `state` TEXT NOT NULL DEFAULT 'saved',
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `estimatedCompletionTime` INTEGER NOT NULL,
                    `origin` TEXT NOT NULL,
                    `humanEditCount` INTEGER NOT NULL,
                    `deletedAtMs` INTEGER,
                    `userEdited` INTEGER NOT NULL,
                    `isVisible` INTEGER NOT NULL,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `isViewed` INTEGER NOT NULL DEFAULT 1,
                    `sortScore` REAL NOT NULL DEFAULT 50.0,
                    `reRankHistory` TEXT NOT NULL DEFAULT '[]',
                    PRIMARY KEY(`savedItemId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_item_new` (
                    savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, origin,
                    humanEditCount, deletedAtMs, userEdited, isVisible, buttons, isViewed,
                    sortScore, reRankHistory
                )
                SELECT
                    savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, origin,
                    humanEditCount, deletedAtMs, userEdited, isVisible, buttons, isViewed,
                    sortScore, reRankHistory
                FROM `saved_item`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `saved_item`")
            db.execSQL("ALTER TABLE `saved_item_new` RENAME TO `saved_item`")

            // 4. Drop the obsolete snapshot table.
            db.execSQL("DROP INDEX IF EXISTS `idx_reminder_snap_status_time`")
            db.execSQL("DROP INDEX IF EXISTS `idx_reminder_snap_savedItemId`")
            db.execSQL("DROP TABLE IF EXISTS `reminder_extraction_snapshot`")
        }
    }

    /**
     * Rebuilds noti_saved_item_link for evidence-only provenance: surrogate linkId PK plus
     * role/source columns. Pre-existing links were "every record sent in the request", not
     * evidence, so this deliberately wipes them instead of copying (fresh-start decision).
     */
    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_noti_saved_item_link_savedItemId`")
            db.execSQL("DROP INDEX IF EXISTS `index_noti_saved_item_link_notiKey`")
            db.execSQL("DROP TABLE IF EXISTS `noti_saved_item_link`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_saved_item_link` (
                    `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `notiKey` TEXT NOT NULL,
                    `notiRecordId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `savedItemId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`savedItemId`) REFERENCES `saved_item`(`savedItemId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_noti_saved_item_link_savedItemId_notiRecordId_role` ON `noti_saved_item_link` (`savedItemId`, `notiRecordId`, `role`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_saved_item_link_savedItemId` ON `noti_saved_item_link` (`savedItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_saved_item_link_notiRecordId` ON `noti_saved_item_link` (`notiRecordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_noti_saved_item_link_notiKey` ON `noti_saved_item_link` (`notiKey`)")
        }
    }

    /** Adds user-owned star/When + review cursor to saved_item and the per-item change log. */
    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `saved_item` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `saved_item` ADD COLUMN `doAtMs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `saved_item` ADD COLUMN `lastViewedChangeAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item_change_log` (
                    `changeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `savedItemId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `changeType` TEXT NOT NULL,
                    `changeSummary` TEXT NOT NULL DEFAULT '',
                    `appendedContent` TEXT NOT NULL DEFAULT '',
                    `addedSubTasksJson` TEXT NOT NULL DEFAULT '[]',
                    `removedSubTasksJson` TEXT NOT NULL DEFAULT '[]',
                    `changedFieldsJson` TEXT NOT NULL DEFAULT '{}',
                    `evidenceRecordIdsJson` TEXT NOT NULL DEFAULT '[]',
                    `origin` TEXT NOT NULL DEFAULT 'llm',
                    FOREIGN KEY(`savedItemId`) REFERENCES `saved_item`(`savedItemId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_item_change_log_savedItemId_createdAt` ON `saved_item_change_log` (`savedItemId`, `createdAt`)")
        }
    }

    /** Adds the per-notiUnit extraction journal (verbatim entries + rolling summary). */
    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extraction_journal_entry` (
                    `entryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `notiKey` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `savedItemId` TEXT NOT NULL DEFAULT '',
                    `itemTitle` TEXT NOT NULL DEFAULT '',
                    `detail` TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_journal_entry_notiKey_createdAt` ON `extraction_journal_entry` (`notiKey`, `createdAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extraction_journal_summary` (
                    `notiKey` TEXT NOT NULL,
                    `summaryText` TEXT NOT NULL DEFAULT '',
                    `lastFoldedAt` INTEGER NOT NULL DEFAULT 0,
                    `foldedEntryCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Moves the LLM-derived scan flags off noti_drawer into the new per-thread noti_llm_state
     * table (which also carries the classification fields). NotiUnit stays purely notification
     * content + display state from here on; thread-level LLM conclusions live in noti_llm_state.
     */
    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_llm_state` (
                    `notiKey` TEXT NOT NULL,
                    `shouldExtractReminder` INTEGER NOT NULL DEFAULT 0,
                    `hasTask` INTEGER NOT NULL DEFAULT 0,
                    `hasMemo` INTEGER NOT NULL DEFAULT 0,
                    `hasEvent` INTEGER NOT NULL DEFAULT 0,
                    `categories` TEXT NOT NULL DEFAULT '[]',
                    `categoryReason` TEXT NOT NULL DEFAULT '',
                    `categorySource` TEXT NOT NULL DEFAULT '',
                    `lastClassifiedAt` INTEGER NOT NULL DEFAULT 0,
                    `lastClassifiedRecordCount` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
            // Seed from the old drawer flags. All-false rows are equivalent to no row at all.
            db.execSQL(
                """
                INSERT OR REPLACE INTO `noti_llm_state`
                    (`notiKey`, `shouldExtractReminder`, `hasTask`, `hasMemo`, `hasEvent`)
                SELECT `notiKey`, `shouldExtractReminder`, `hasTask`, `hasMemo`, `hasEvent`
                FROM `noti_drawer`
                WHERE `shouldExtractReminder` = 1 OR `hasTask` = 1 OR `hasMemo` = 1 OR `hasEvent` = 1
                """.trimIndent()
            )
            // Drop the moved columns: SQLite needs a table recreate for column removal.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_drawer_new` (
                    `notiKey` TEXT NOT NULL,
                    `pkgName` TEXT NOT NULL,
                    `hashKey` INTEGER NOT NULL,
                    `groupKey` TEXT NOT NULL,
                    `isAppGroup` INTEGER NOT NULL,
                    `isGroupChat` INTEGER NOT NULL,
                    `sortKey` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `lastUpdateTime` INTEGER NOT NULL,
                    `lastSyncTime` INTEGER NOT NULL,
                    `icon` TEXT NOT NULL,
                    `largeIcon` TEXT NOT NULL,
                    `isPeople` INTEGER NOT NULL,
                    `isPinned` INTEGER NOT NULL,
                    `isArchived` INTEGER NOT NULL,
                    `isDismissed` INTEGER NOT NULL,
                    `isRead` INTEGER NOT NULL,
                    `isSetToTop` INTEGER NOT NULL,
                    `setToTopTime` INTEGER NOT NULL,
                    `sortPosition` INTEGER NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `sortScore` REAL NOT NULL,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_drawer_new`
                    (`notiKey`, `pkgName`, `hashKey`, `groupKey`, `isAppGroup`, `isGroupChat`,
                     `sortKey`, `appName`, `lastUpdateTime`, `lastSyncTime`, `icon`, `largeIcon`,
                     `isPeople`, `isPinned`, `isArchived`, `isDismissed`, `isRead`, `isSetToTop`,
                     `setToTopTime`, `sortPosition`, `explanation`, `summary`, `sortScore`)
                SELECT `notiKey`, `pkgName`, `hashKey`, `groupKey`, `isAppGroup`, `isGroupChat`,
                     `sortKey`, `appName`, `lastUpdateTime`, `lastSyncTime`, `icon`, `largeIcon`,
                     `isPeople`, `isPinned`, `isArchived`, `isDismissed`, `isRead`, `isSetToTop`,
                     `setToTopTime`, `sortPosition`, `explanation`, `summary`, `sortScore`
                FROM `noti_drawer`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_drawer`")
            db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")
        }
    }

    /**
     * Per-notiKey pipeline redesign (contract v3):
     * 1. saved_item: user deletion becomes a hard delete — soft-deleted rows (isVisible = 0) are
     *    purged with their sub-items/links/change-logs, then the table is rebuilt without
     *    isVisible/deletedAtMs (soft delete), estimatedCompletionTime (feature removed), and
     *    sortScore/reRankHistory (rerank pipeline removed; ordering is date-based now).
     * 2. noti_llm_state: drops the old scan gate flags (hasTask/hasMemo/hasEvent) — the new
     *    per-thread scan stage only decides shouldExtract + category.
     * 3. pending_op: staged pipeline instructions awaiting review (fully-staged model).
     * 4. rejected_merge: merge-rejection cool-down pairs.
     * 5. extraction_journal_summary gains the record fold watermark (lastFoldedPostTime).
     */
    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1a. Purge soft-deleted items and their children before the rebuild drops the flag.
            db.execSQL(
                """
                DELETE FROM `saved_sub_item` WHERE `parentSavedItemId` IN
                    (SELECT `savedItemId` FROM `saved_item` WHERE `isVisible` = 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                DELETE FROM `noti_saved_item_link` WHERE `savedItemId` IN
                    (SELECT `savedItemId` FROM `saved_item` WHERE `isVisible` = 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                DELETE FROM `saved_item_change_log` WHERE `savedItemId` IN
                    (SELECT `savedItemId` FROM `saved_item` WHERE `isVisible` = 0)
                """.trimIndent()
            )
            db.execSQL("DELETE FROM `saved_item` WHERE `isVisible` = 0")

            // 1b. Rebuild without the dropped columns.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item_new` (
                    `savedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `state` TEXT NOT NULL DEFAULT 'saved',
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `origin` TEXT NOT NULL,
                    `humanEditCount` INTEGER NOT NULL,
                    `userEdited` INTEGER NOT NULL,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `isViewed` INTEGER NOT NULL DEFAULT 1,
                    `isStarred` INTEGER NOT NULL DEFAULT 0,
                    `doAtMs` INTEGER NOT NULL DEFAULT 0,
                    `lastViewedChangeAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`savedItemId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_item_new` (
                    savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, origin, humanEditCount, userEdited,
                    buttons, isViewed, isStarred, doAtMs, lastViewedChangeAt
                )
                SELECT
                    savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                    deadlineAtMs, startAtMs, endAtMs, origin, humanEditCount, userEdited,
                    buttons, isViewed, isStarred, doAtMs, lastViewedChangeAt
                FROM `saved_item`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `saved_item`")
            db.execSQL("ALTER TABLE `saved_item_new` RENAME TO `saved_item`")

            // 2. noti_llm_state without the old gate flags.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `noti_llm_state_new` (
                    `notiKey` TEXT NOT NULL,
                    `shouldExtractReminder` INTEGER NOT NULL DEFAULT 0,
                    `categories` TEXT NOT NULL DEFAULT '[]',
                    `categoryReason` TEXT NOT NULL DEFAULT '',
                    `categorySource` TEXT NOT NULL DEFAULT '',
                    `lastClassifiedAt` INTEGER NOT NULL DEFAULT 0,
                    `lastClassifiedRecordCount` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_llm_state_new` (
                    notiKey, shouldExtractReminder, categories, categoryReason, categorySource,
                    lastClassifiedAt, lastClassifiedRecordCount, updatedAt
                )
                SELECT
                    notiKey, shouldExtractReminder, categories, categoryReason, categorySource,
                    lastClassifiedAt, lastClassifiedRecordCount, updatedAt
                FROM `noti_llm_state`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_llm_state`")
            db.execSQL("ALTER TABLE `noti_llm_state_new` RENAME TO `noti_llm_state`")

            // 3. Staged pipeline instructions.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_op` (
                    `opId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `notiKey` TEXT NOT NULL DEFAULT '',
                    `opType` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `targetItemId` TEXT NOT NULL DEFAULT '',
                    `mergeSourceItemIds` TEXT NOT NULL DEFAULT '[]',
                    `evidenceRecordIds` TEXT NOT NULL DEFAULT '[]',
                    `reason` TEXT NOT NULL DEFAULT '',
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `batchId` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_op_targetItemId` ON `pending_op` (`targetItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_op_batchId` ON `pending_op` (`batchId`)")

            // 4. Merge-rejection cool-down pairs.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rejected_merge` (
                    `itemIdA` TEXT NOT NULL,
                    `itemIdB` TEXT NOT NULL,
                    `rejectedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`itemIdA`, `itemIdB`)
                )
                """.trimIndent()
            )

            // 5. Record fold watermark for the per-thread rolling summary.
            db.execSQL("ALTER TABLE `extraction_journal_summary` ADD COLUMN `lastFoldedPostTime` INTEGER NOT NULL DEFAULT 0")

            // 6. Drop the old-pipeline per-record processing flags (scan/extract markers and the
            //    claim/lease columns). The per-notiKey pipeline tracks progress with the fold
            //    watermark on extraction_journal_summary instead.
            db.execSQL("DROP VIEW IF EXISTS `VisibleNotiRecord`")
            db.execSQL("DROP INDEX IF EXISTS `idx_record_notiKey_whenTime`")
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
                    `isDismissed` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiRecordId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_record_new` (
                    notiRecordId, notiKey, whenTime, postTime, person,
                    extraTitle, extraBigTitle, extraConversationTitle,
                    extraBigText, extraText, extraTextLines, extraSummaryText,
                    extraInfoText, extraSubText, isDismissed
                )
                SELECT
                    notiRecordId, notiKey, whenTime, postTime, person,
                    extraTitle, extraBigTitle, extraConversationTitle,
                    extraBigText, extraText, extraTextLines, extraSummaryText,
                    extraInfoText, extraSubText, isDismissed
                FROM `noti_record`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_record`")
            db.execSQL("ALTER TABLE `noti_record_new` RENAME TO `noti_record`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_record_notiKey_whenTime` ON `noti_record` (`notiKey`, `whenTime`)")
        }
    }

    /** Adds the payload-free, account-scoped Firestore retry queue. */
    val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `firestore_outbox` (
                    `operationKey` TEXT NOT NULL,
                    `uid` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT NOT NULL,
                    PRIMARY KEY(`operationKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_firestore_outbox_uid_createdAt` " +
                    "ON `firestore_outbox` (`uid`, `createdAt`)"
            )
        }
    }

    /** Persists complete generated proposals and their user decision without source notification text. */
    val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `generated_proposal` (
                    `proposalId` TEXT NOT NULL,
                    `uid` TEXT NOT NULL,
                    `opId` INTEGER NOT NULL,
                    `batchId` TEXT NOT NULL,
                    `opType` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `targetItemId` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL,
                    `decision` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `decisionAt` INTEGER NOT NULL,
                    PRIMARY KEY(`proposalId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_generated_proposal_uid_createdAt` ON `generated_proposal` (`uid`, `createdAt`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_generated_proposal_opId` ON `generated_proposal` (`opId`)")
        }
    }

    /** Persists the successful automatic Stage B timestamp for per-thread request rate limiting. */
    val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `noti_llm_state` ADD COLUMN `lastItemExtractionAt` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /** Renames staged proposal and proposal-history storage without dropping persisted rows. */
    val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_pending_op_targetItemId`")
            db.execSQL("DROP INDEX IF EXISTS `index_pending_op_batchId`")
            db.execSQL("ALTER TABLE `pending_op` RENAME TO `pending_proposed_op`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_proposed_op_targetItemId` " +
                    "ON `pending_proposed_op` (`targetItemId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_proposed_op_batchId` " +
                    "ON `pending_proposed_op` (`batchId`)"
            )

            db.execSQL("DROP INDEX IF EXISTS `index_generated_proposal_uid_createdAt`")
            db.execSQL("DROP INDEX IF EXISTS `index_generated_proposal_opId`")
            db.execSQL("ALTER TABLE `generated_proposal` RENAME TO `proposed_op_record`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_proposed_op_record_uid_createdAt` " +
                    "ON `proposed_op_record` (`uid`, `createdAt`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_proposed_op_record_opId` " +
                    "ON `proposed_op_record` (`opId`)"
            )

            db.execSQL(
                "UPDATE `firestore_outbox` SET " +
                    "`operationKey` = REPLACE(`operationKey`, ':generated_proposal:', ':proposed_op_record:'), " +
                    "`kind` = 'sync_proposed_op_record' " +
                    "WHERE `kind` = 'sync_generated_proposal'"
            )
        }
    }

    /**
     * Simplifies SavedItem children, makes Keep childlessness durable, and adopts the current
     * SavedItem/When terminology in the active schema. Historical migrations intentionally retain
     * their original names so every installed-version upgrade path remains reproducible.
     */
    val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val childrenByParent = linkedMapOf<String, MutableList<LegacySavedSubItem>>()
            db.query(
                """
                SELECT s.savedSubItemId, s.parentSavedItemId, s.title, s.description,
                       s.isCompleted, s.deadlineAtMs, s.startAtMs, s.endAtMs, s.buttons,
                       s.sortOrder, s.createdAt, i.itemType
                FROM saved_sub_item s
                JOIN saved_item i ON i.savedItemId = s.parentSavedItemId
                WHERE s.isVisible = 1
                ORDER BY s.parentSavedItemId, s.sortOrder, s.createdAt, s.savedSubItemId
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val child = LegacySavedSubItem(
                        id = cursor.getString(0),
                        parentId = cursor.getString(1),
                        title = cursor.getString(2),
                        description = cursor.getString(3),
                        completed = cursor.getInt(4) != 0,
                        deadlineAtMs = cursor.getLong(5),
                        startAtMs = cursor.getLong(6),
                        endAtMs = cursor.getLong(7),
                        buttons = cursor.getString(8),
                        itemType = cursor.getString(11),
                    )
                    childrenByParent.getOrPut(child.parentId) { mutableListOf() } += child
                }
            }

            childrenByParent.forEach { (parentId, children) ->
                mergeLegacyButtons(db, parentId, children.map { it.buttons })
                if (children.firstOrNull()?.itemType == "keep") {
                    appendLegacyKeepChildren(db, parentId, children)
                }
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_sub_item_new` (
                    `savedSubItemId` TEXT NOT NULL,
                    `parentSavedItemId` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`savedSubItemId`),
                    FOREIGN KEY(`parentSavedItemId`) REFERENCES `saved_item`(`savedItemId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            childrenByParent.forEach { (_, children) ->
                if (children.firstOrNull()?.itemType != "task") return@forEach
                children.map { child -> child to child.flattenedText() }
                    .filter { (_, text) -> text.isNotBlank() }
                    .forEachIndexed { position, (child, text) ->
                        db.execSQL(
                            """
                            INSERT INTO saved_sub_item_new
                                (savedSubItemId, parentSavedItemId, text, isCompleted, position)
                            VALUES (?, ?, ?, ?, ?)
                            """.trimIndent(),
                            arrayOf<Any?>(child.id, child.parentId, text, if (child.completed) 1 else 0, position),
                        )
                }
            }
            db.execSQL("DROP TABLE `saved_sub_item`")
            db.execSQL("ALTER TABLE `saved_sub_item_new` RENAME TO `saved_sub_item`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_saved_sub_item_parent` " +
                    "ON `saved_sub_item` (`parentSavedItemId`)"
            )

            // API 29 ships a SQLite version without RENAME COLUMN. Rebuild the parent and every
            // FK child so links and change history survive without relying on that newer syntax.
            db.execSQL("CREATE TEMP TABLE `_v50_saved_items` AS SELECT * FROM `saved_item`")
            db.execSQL("CREATE TEMP TABLE `_v50_sub_items` AS SELECT * FROM `saved_sub_item`")
            db.execSQL("CREATE TEMP TABLE `_v50_links` AS SELECT * FROM `noti_saved_item_link`")
            db.execSQL("CREATE TEMP TABLE `_v50_changes` AS SELECT * FROM `saved_item_change_log`")
            db.execSQL("DROP TABLE `saved_sub_item`")
            db.execSQL("DROP TABLE `noti_saved_item_link`")
            db.execSQL("DROP TABLE `saved_item_change_log`")
            db.execSQL("DROP TABLE `saved_item`")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_item` (
                    `savedItemId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `itemType` TEXT NOT NULL DEFAULT 'task',
                    `state` TEXT NOT NULL DEFAULT 'saved',
                    `lastUpdateTimestamp` INTEGER NOT NULL,
                    `deadlineAtMs` INTEGER NOT NULL,
                    `startAtMs` INTEGER NOT NULL DEFAULT 0,
                    `endAtMs` INTEGER NOT NULL DEFAULT 0,
                    `origin` TEXT NOT NULL,
                    `humanEditCount` INTEGER NOT NULL,
                    `userEdited` INTEGER NOT NULL,
                    `buttons` TEXT NOT NULL DEFAULT '[]',
                    `isViewed` INTEGER NOT NULL DEFAULT 1,
                    `isStarred` INTEGER NOT NULL DEFAULT 0,
                    `whenAtMs` INTEGER NOT NULL DEFAULT 0,
                    `lastViewedChangeAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`savedItemId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `saved_item` (
                    savedItemId,title,content,itemType,state,lastUpdateTimestamp,deadlineAtMs,
                    startAtMs,endAtMs,origin,humanEditCount,userEdited,buttons,isViewed,isStarred,
                    whenAtMs,lastViewedChangeAt
                )
                SELECT
                    savedItemId,title,content,itemType,state,lastUpdateTimestamp,deadlineAtMs,
                    startAtMs,endAtMs,origin,humanEditCount,userEdited,buttons,isViewed,isStarred,
                    doAtMs,lastViewedChangeAt
                FROM `_v50_saved_items`
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `saved_sub_item` (
                    `savedSubItemId` TEXT NOT NULL,
                    `parentSavedItemId` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`savedSubItemId`),
                    FOREIGN KEY(`parentSavedItemId`) REFERENCES `saved_item`(`savedItemId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `saved_sub_item` SELECT * FROM `_v50_sub_items`")
            db.execSQL("CREATE INDEX `idx_saved_sub_item_parent` ON `saved_sub_item` (`parentSavedItemId`)")

            db.execSQL(
                """
                CREATE TABLE `noti_saved_item_link` (
                    `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `notiKey` TEXT NOT NULL,
                    `notiRecordId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `savedItemId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`savedItemId`) REFERENCES `saved_item`(`savedItemId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `noti_saved_item_link` SELECT * FROM `_v50_links`")
            db.execSQL("CREATE UNIQUE INDEX `index_noti_saved_item_link_savedItemId_notiRecordId_role` ON `noti_saved_item_link` (`savedItemId`,`notiRecordId`,`role`)")
            db.execSQL("CREATE INDEX `index_noti_saved_item_link_savedItemId` ON `noti_saved_item_link` (`savedItemId`)")
            db.execSQL("CREATE INDEX `index_noti_saved_item_link_notiRecordId` ON `noti_saved_item_link` (`notiRecordId`)")
            db.execSQL("CREATE INDEX `index_noti_saved_item_link_notiKey` ON `noti_saved_item_link` (`notiKey`)")

            db.execSQL(
                """
                CREATE TABLE `saved_item_change_log` (
                    `changeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `savedItemId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `changeType` TEXT NOT NULL,
                    `changeSummary` TEXT NOT NULL DEFAULT '',
                    `appendedContent` TEXT NOT NULL DEFAULT '',
                    `addedSubTasksJson` TEXT NOT NULL DEFAULT '[]',
                    `removedSubTasksJson` TEXT NOT NULL DEFAULT '[]',
                    `changedFieldsJson` TEXT NOT NULL DEFAULT '{}',
                    `evidenceRecordIdsJson` TEXT NOT NULL DEFAULT '[]',
                    `origin` TEXT NOT NULL DEFAULT 'llm',
                    FOREIGN KEY(`savedItemId`) REFERENCES `saved_item`(`savedItemId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `saved_item_change_log` SELECT * FROM `_v50_changes`")
            db.execSQL("CREATE INDEX `index_saved_item_change_log_savedItemId_createdAt` ON `saved_item_change_log` (`savedItemId`,`createdAt`)")
            db.execSQL("DROP TABLE `_v50_saved_items`")
            db.execSQL("DROP TABLE `_v50_sub_items`")
            db.execSQL("DROP TABLE `_v50_links`")
            db.execSQL("DROP TABLE `_v50_changes`")
            db.execSQL(
                """
                CREATE TABLE `noti_llm_state_new` (
                    `notiKey` TEXT NOT NULL,
                    `shouldExtractSavedItem` INTEGER NOT NULL DEFAULT 0,
                    `categories` TEXT NOT NULL DEFAULT '[]',
                    `categoryReason` TEXT NOT NULL DEFAULT '',
                    `categorySource` TEXT NOT NULL DEFAULT '',
                    `lastClassifiedAt` INTEGER NOT NULL DEFAULT 0,
                    `lastClassifiedRecordCount` INTEGER NOT NULL DEFAULT 0,
                    `lastItemExtractionAt` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_llm_state_new`
                SELECT notiKey,shouldExtractReminder,categories,categoryReason,categorySource,
                       lastClassifiedAt,lastClassifiedRecordCount,lastItemExtractionAt,updatedAt
                FROM `noti_llm_state`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_llm_state`")
            db.execSQL("ALTER TABLE `noti_llm_state_new` RENAME TO `noti_llm_state`")
        }
    }

    /** Removes viewport-derived read state from the current notification drawer schema. */
    val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE `noti_drawer_new` (
                    `notiKey` TEXT NOT NULL,
                    `pkgName` TEXT NOT NULL,
                    `hashKey` INTEGER NOT NULL,
                    `groupKey` TEXT NOT NULL,
                    `isAppGroup` INTEGER NOT NULL,
                    `isGroupChat` INTEGER NOT NULL,
                    `sortKey` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `lastUpdateTime` INTEGER NOT NULL,
                    `lastSyncTime` INTEGER NOT NULL,
                    `icon` TEXT NOT NULL,
                    `largeIcon` TEXT NOT NULL,
                    `isPeople` INTEGER NOT NULL,
                    `isPinned` INTEGER NOT NULL,
                    `isArchived` INTEGER NOT NULL,
                    `isDismissed` INTEGER NOT NULL,
                    `isSetToTop` INTEGER NOT NULL,
                    `setToTopTime` INTEGER NOT NULL,
                    `sortPosition` INTEGER NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `sortScore` REAL NOT NULL,
                    PRIMARY KEY(`notiKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `noti_drawer_new` (
                    notiKey,pkgName,hashKey,groupKey,isAppGroup,isGroupChat,sortKey,appName,
                    lastUpdateTime,lastSyncTime,icon,largeIcon,isPeople,isPinned,isArchived,
                    isDismissed,isSetToTop,setToTopTime,sortPosition,explanation,summary,sortScore
                )
                SELECT
                    notiKey,pkgName,hashKey,groupKey,isAppGroup,isGroupChat,sortKey,appName,
                    lastUpdateTime,lastSyncTime,icon,largeIcon,isPeople,isPinned,isArchived,
                    isDismissed,isSetToTop,setToTopTime,sortPosition,explanation,summary,sortScore
                FROM `noti_drawer`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `noti_drawer`")
            db.execSQL("ALTER TABLE `noti_drawer_new` RENAME TO `noti_drawer`")
        }
    }

    private data class LegacySavedSubItem(
        val id: String,
        val parentId: String,
        val title: String,
        val description: String,
        val completed: Boolean,
        val deadlineAtMs: Long,
        val startAtMs: Long,
        val endAtMs: Long,
        val buttons: String,
        val itemType: String,
    ) {
        fun flattenedText(): String {
            val parts = buildList {
                normalizeLegacyLine(title).takeIf(String::isNotBlank)?.let(::add)
                normalizeLegacyLine(description).takeIf(String::isNotBlank)?.let(::add)
                legacyTimeLabel("Deadline", "截止時間", deadlineAtMs)?.let(::add)
                legacyTimeLabel("Start", "開始時間", startAtMs)?.let(::add)
                legacyTimeLabel("End", "結束時間", endAtMs)?.let(::add)
            }
            return parts.distinct().joinToString(" — ")
        }
    }

    private fun appendLegacyKeepChildren(
        db: SupportSQLiteDatabase,
        parentId: String,
        children: List<LegacySavedSubItem>,
    ) {
        val lines = children.mapNotNull { child ->
            child.flattenedText().takeIf(String::isNotBlank)?.let { text ->
                (if (child.completed) "☑ " else "☐ ") + text
            }
        }
        if (lines.isEmpty()) return
        val existing = db.query("SELECT content FROM saved_item WHERE savedItemId = ?", arrayOf(parentId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }
        val heading = if (Locale.getDefault().language.startsWith("zh")) "子任務" else "Subtasks"
        val block = "$heading:\n${lines.joinToString("\n")}".trim()
        val merged = listOf(existing.trim(), block).filter(String::isNotBlank).joinToString("\n\n")
        db.execSQL("UPDATE saved_item SET content = ? WHERE savedItemId = ?", arrayOf(merged, parentId))
    }

    private fun mergeLegacyButtons(
        db: SupportSQLiteDatabase,
        parentId: String,
        childButtonJson: List<String>,
    ) {
        val parentJson = db.query("SELECT buttons FROM saved_item WHERE savedItemId = ?", arrayOf(parentId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "[]"
        }
        val merged = JSONArray()
        val seen = linkedSetOf<String>()
        fun append(raw: String) {
            val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index) ?: continue
                val type = obj.optString("type", "link")
                val intent = obj.optString("intent", "")
                if (intent.isBlank() || !seen.add("$type\u0000$intent")) continue
                merged.put(obj)
            }
        }
        append(parentJson)
        childButtonJson.forEach(::append)
        db.execSQL("UPDATE saved_item SET buttons = ? WHERE savedItemId = ?", arrayOf(merged.toString(), parentId))
    }

    private fun normalizeLegacyLine(value: String): String = value
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("[\\t ]+"), " ")
        .trim()

    private fun legacyTimeLabel(en: String, zh: String, timestamp: Long): String? {
        if (timestamp <= 0L) return null
        val label = if (Locale.getDefault().language.startsWith("zh")) zh else en
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        return "$label: $formatted"
    }

}
