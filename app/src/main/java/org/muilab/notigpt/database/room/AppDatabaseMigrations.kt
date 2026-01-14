package org.muilab.notigpt.database.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps AppDatabase.kt readable by isolating schema history.
 *
 * NOTE: These migrations are referenced from AppDatabase.buildDatabase().
 */
public object AppDatabaseMigrations {

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
                        `deadlineTimestamp` INTEGER NOT NULL,
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
                    `reminderId` TEXT,
                    `payloadJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`snapshotId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_snap_status_time` ON `esm_extraction_snapshot` (`status`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_snap_reminderId` ON `esm_extraction_snapshot` (`reminderId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `esm_instance` (
                    `instanceId` TEXT NOT NULL,
                    `questionnaireId` TEXT NOT NULL,
                    `questionnaireVersion` INTEGER NOT NULL,
                    `triggerType` TEXT NOT NULL,
                    `reminderId` TEXT NOT NULL,
                    `snapshotId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `availableAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `answeredAt` INTEGER NOT NULL DEFAULT 0,
                    `isLate` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`instanceId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_status_available` ON `esm_instance` (`status`, `availableAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_status_expires` ON `esm_instance` (`status`, `expiresAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_reminderId` ON `esm_instance` (`reminderId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `esm_answer_event` (
                    `instanceId` TEXT NOT NULL,
                    `questionId` TEXT NOT NULL,
                    `answerJson` TEXT NOT NULL,
                    `answeredAt` INTEGER NOT NULL,
                    PRIMARY KEY(`instanceId`, `questionId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_esm_answer_instance` ON `esm_answer_event` (`instanceId`)")
        }
    }
}
