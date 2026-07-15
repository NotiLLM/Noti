package org.muilab.notigpt.database.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations

/**
 * Direct test of [AppDatabaseMigrations.MIGRATION_44_45] (per-notiKey pipeline redesign).
 *
 * Builds the relevant slice of the v44 schema by hand, seeds it (including a soft-deleted
 * `isVisible = 0` saved item with a sub-item / link / change-log), runs the migration, and asserts:
 * soft-deleted rows and their children are purged; saved_item drops isVisible/deletedAtMs/
 * estimatedCompletionTime/sortScore/reRankHistory; noti_llm_state drops hasTask/hasMemo/hasEvent;
 * pending_op and rejected_merge are created; extraction_journal_summary gains lastFoldedPostTime; and
 * noti_record drops the old per-record processing flags.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration44To45Test {

    private val dbName = "migration-44-45-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration44To45_purgesSoftDeleted_dropsColumns_addsTablesAndWatermark() {
        val db = openV44Database()
        try {
            insertSavedItem(db, id = "s_visible", isVisible = 1)
            insertSavedItem(db, id = "s_hidden", isVisible = 0)
            db.execSQL(
                "INSERT INTO saved_sub_item (savedSubItemId, parentSavedItemId, title, description, createdAt, lastUpdateTimestamp) VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>("sub_hidden", "s_hidden", "t", "d", 0L, 0L),
            )
            db.execSQL(
                "INSERT INTO noti_saved_item_link (notiKey, notiRecordId, role, source, type, savedItemId, createdAt) VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any?>("k.a", "k.a_1", "evidence", "llm_auto_extraction", "task", "s_hidden", 0L),
            )
            db.execSQL(
                "INSERT INTO saved_item_change_log (savedItemId, createdAt, changeType) VALUES (?,?,?)",
                arrayOf<Any?>("s_hidden", 0L, "created"),
            )
            db.execSQL(
                "INSERT INTO noti_llm_state (notiKey, shouldExtractReminder, hasTask, hasMemo, hasEvent) VALUES (?,?,?,?,?)",
                arrayOf<Any?>("k.a", 1, 1, 0, 0),
            )
            db.execSQL(
                "INSERT INTO extraction_journal_summary (notiKey, summaryText, lastFoldedAt, foldedEntryCount) VALUES (?,?,?,?)",
                arrayOf<Any?>("k.a", "sum", 0L, 0),
            )
            db.execSQL(
                "INSERT INTO noti_record (notiRecordId, notiKey, whenTime, postTime, person, extraTitle, extraBigTitle, extraConversationTitle, extraBigText, extraText, extraTextLines, extraSummaryText, extraInfoText, extraSubText, isDismissed, taskScanned, taskExtracted, taskExtractionClaimed, taskExtractionClaimedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("k.a_1", "k.a", 10L, 10L, "", "", "", "", "", "", "", "", "", "", 0, 1, 1, 0, 0L),
            )

            AppDatabaseMigrations.MIGRATION_44_45.migrate(db)

            // 1. Soft-deleted saved item purged; visible one survives.
            db.query("SELECT savedItemId FROM saved_item").use { c ->
                val ids = buildSet { while (c.moveToNext()) add(c.getString(0)) }
                assertEquals(setOf("s_visible"), ids)
            }
            // Children of the purged item are gone.
            assertEquals(0, count(db, "SELECT COUNT(*) FROM saved_sub_item"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM noti_saved_item_link"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM saved_item_change_log"))

            // 2. saved_item dropped the removed columns.
            val savedCols = tableColumns(db, "saved_item")
            listOf("isVisible", "deletedAtMs", "estimatedCompletionTime", "sortScore", "reRankHistory").forEach {
                assertFalse("saved_item should drop $it", it in savedCols)
            }
            assertTrue("isStarred" in savedCols && "doAtMs" in savedCols && "lastViewedChangeAt" in savedCols)

            // 3. noti_llm_state dropped the old gate flags.
            val llmCols = tableColumns(db, "noti_llm_state")
            listOf("hasTask", "hasMemo", "hasEvent").forEach { assertFalse(it in llmCols) }
            assertTrue("shouldExtractReminder" in llmCols && "categories" in llmCols)

            // 4. New tables exist.
            assertTrue(tableExists(db, "pending_op"))
            assertTrue(tableExists(db, "rejected_merge"))

            // 5. Fold watermark added, defaulting to 0.
            assertTrue("lastFoldedPostTime" in tableColumns(db, "extraction_journal_summary"))
            db.query("SELECT lastFoldedPostTime FROM extraction_journal_summary WHERE notiKey = 'k.a'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }

            // 6. noti_record dropped the per-record processing flags but kept its content + rows.
            val recordCols = tableColumns(db, "noti_record")
            listOf("taskScanned", "taskExtracted", "taskExtractionClaimed", "taskExtractionClaimedAt").forEach {
                assertFalse("noti_record should drop $it", it in recordCols)
            }
            assertEquals(1, count(db, "SELECT COUNT(*) FROM noti_record"))

            // VisibleNotiRecord was a dead legacy Room view. v45 removes it from the database
            // contract, so the migration must leave no view behind after rebuilding noti_record.
            assertFalse(viewExists(db, "VisibleNotiRecord"))
        } finally {
            db.close()
        }
    }

    private fun insertSavedItem(db: SupportSQLiteDatabase, id: String, isVisible: Int) {
        db.execSQL(
            """
            INSERT INTO saved_item (
                savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, origin,
                humanEditCount, deletedAtMs, userEdited, isVisible, buttons, isViewed,
                sortScore, reRankHistory, isStarred, doAtMs, lastViewedChangeAt
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                id, "title", "content", "task", "saved", 0L,
                0L, 0L, 0L, 0L, "manual",
                0, null, 0, isVisible, "[]", 1,
                50.0, "[]", 0, 0L, 0L,
            ),
        )
    }

    private fun openV44Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(44) {
            override fun onCreate(db: SupportSQLiteDatabase) {
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
                        `isStarred` INTEGER NOT NULL DEFAULT 0,
                        `doAtMs` INTEGER NOT NULL DEFAULT 0,
                        `lastViewedChangeAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`savedItemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_sub_item` (
                        `savedSubItemId` TEXT NOT NULL,
                        `parentSavedItemId` TEXT NOT NULL,
                        `title` TEXT NOT NULL DEFAULT '',
                        `description` TEXT NOT NULL DEFAULT '',
                        `itemType` TEXT NOT NULL DEFAULT 'task',
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `deadlineAtMs` INTEGER NOT NULL DEFAULT 0,
                        `startAtMs` INTEGER NOT NULL DEFAULT 0,
                        `endAtMs` INTEGER NOT NULL DEFAULT 0,
                        `buttons` TEXT NOT NULL DEFAULT '[]',
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `lastUpdateTimestamp` INTEGER NOT NULL,
                        `isVisible` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`savedSubItemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `noti_saved_item_link` (
                        `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `notiKey` TEXT NOT NULL,
                        `notiRecordId` TEXT NOT NULL,
                        `role` TEXT NOT NULL DEFAULT 'evidence',
                        `source` TEXT NOT NULL DEFAULT '',
                        `type` TEXT NOT NULL,
                        `savedItemId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_item_change_log` (
                        `changeLogId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `savedItemId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `changeType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
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
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `noti_record` (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_notiKey_whenTime ON noti_record(notiKey, whenTime)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { /* no-op */ }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int {
        db.query(sql).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameIdx >= 0) cols += c.getString(nameIdx)
            }
        }
        return cols
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf<Any?>(table)).use { c ->
            return c.count > 0
        }
    }

    private fun viewExists(db: SupportSQLiteDatabase, view: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='view' AND name=?", arrayOf<Any?>(view)).use { c ->
            return c.count > 0
        }
    }
}
