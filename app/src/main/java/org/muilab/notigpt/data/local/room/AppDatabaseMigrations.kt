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
    val MIGRATION_39_40 = Migration(39, 40) { db ->
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

    /**
     * Rebuilds noti_saved_item_link for evidence-only provenance: surrogate linkId PK plus
     * role/source columns. Pre-existing links were "every record sent in the request", not
     * evidence, so this deliberately wipes them instead of copying (fresh-start decision).
     */
    val MIGRATION_40_41 = Migration(40, 41) { db ->
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

    /** Adds user-owned star/When + review cursor to saved_item and the per-item change log. */
    val MIGRATION_41_42 = Migration(41, 42) { db ->
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

    /** Adds the per-notiUnit extraction journal (verbatim entries + rolling summary). */
    val MIGRATION_42_43 = Migration(42, 43) { db ->
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

    /**
     * Moves the LLM-derived scan flags off noti_drawer into the new per-thread noti_llm_state
     * table (which also carries the classification fields). NotiUnit stays purely notification
     * content + display state from here on; thread-level LLM conclusions live in noti_llm_state.
     */
    val MIGRATION_43_44 = Migration(43, 44) { db ->
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
    val MIGRATION_44_45 = Migration(44, 45) { db ->
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

    /** Adds the payload-free, account-scoped Firestore retry queue. */
    val MIGRATION_45_46 = Migration(45, 46) { db ->
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

    /** Persists complete generated proposals and their user decision without source notification text. */
    val MIGRATION_46_47 = Migration(46, 47) { db ->
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

    /** Persists the successful automatic Stage B timestamp for per-thread request rate limiting. */
    val MIGRATION_47_48 = sqlMigration(
        47,
        48,
        "ALTER TABLE `noti_llm_state` ADD COLUMN `lastItemExtractionAt` INTEGER NOT NULL DEFAULT 0",
    )

    /** Renames staged proposal and proposal-history storage without dropping persisted rows. */
    val MIGRATION_48_49 = Migration(48, 49) { db ->
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

    /**
     * Simplifies SavedItem children, makes Keep childlessness durable, and adopts the current
     * SavedItem/When terminology in the active schema. Historical migrations intentionally retain
     * their original names so every installed-version upgrade path remains reproducible.
     */
    val MIGRATION_49_50 = Migration(49, 50) { db ->
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

    /** Removes viewport-derived read state from the current notification drawer schema. */
    val MIGRATION_50_51 = Migration(50, 51) { db ->
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

    /** Adds durable review-only When overrides and labels history inherited through merges. */
    val MIGRATION_51_52 = Migration(51, 52) { db ->
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_review_draft` (
                `reviewKey` TEXT NOT NULL,
                `whenAtMs` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`reviewKey`)
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE `saved_item_change_log` ADD COLUMN `sourceSavedItemId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `saved_item_change_log` ADD COLUMN `sourceItemTitle` TEXT NOT NULL DEFAULT ''")
    }

    val ALL: Array<Migration> = LegacyAppDatabaseMigrations.ALL + arrayOf(
        MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43,
        MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47,
        MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51,
        MIGRATION_51_52,
    )

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
