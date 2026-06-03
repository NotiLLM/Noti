package org.muilab.notigpt.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
            db.execSQL("ALTER TABLE noti_record ADD COLUMN isNew INTEGER NOT NULL DEFAULT 1")
        }
    }

}
