package org.muilab.notigpt.database.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations

/**
 * Direct test of [AppDatabaseMigrations.MIGRATION_40_41] through [AppDatabaseMigrations.MIGRATION_42_43].
 *
 * Because the database disables schema export, this builds the relevant slice of the v40 schema by hand,
 * seeds it, runs the three migrations, and asserts: the link table is rebuilt (and deliberately wiped —
 * pre-v41 links were "everything sent in the request", not evidence), the new saved_item columns default
 * correctly, the change-log/journal tables exist, and the savedItemId FK cascade still works.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration40To43Test {

    private val dbName = "migration-40-43-test.db"
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
    fun migration40To43_rebuildsLinksWiped_addsColumnsAndTables_keepsCascade() {
        val db = openV40Database()
        try {
            insertSavedItem(db, id = "s_task")
            db.execSQL(
                "INSERT INTO noti_saved_item_link (notiKey, notiRecordId, type, savedItemId, createdAt) VALUES (?,?,?,?,?)",
                arrayOf<Any?>("key.a", "key.a_100", "task", "s_task", 1L),
            )

            AppDatabaseMigrations.MIGRATION_40_41.migrate(db)
            AppDatabaseMigrations.MIGRATION_41_42.migrate(db)
            AppDatabaseMigrations.MIGRATION_42_43.migrate(db)

            // 1. Link table rebuilt with the new shape and wiped.
            val linkCols = tableColumns(db, "noti_saved_item_link")
            assertTrue("linkId" in linkCols)
            assertTrue("role" in linkCols)
            assertTrue("source" in linkCols)
            db.query("SELECT COUNT(*) FROM noti_saved_item_link").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }

            // 2. Unique index dedupes re-cited evidence.
            db.execSQL(
                "INSERT INTO noti_saved_item_link (notiKey, notiRecordId, type, savedItemId, role, source, createdAt) VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any?>("key.a", "key.a_100", "task", "s_task", "evidence", "llm_auto_extraction", 2L),
            )
            db.execSQL(
                "INSERT OR IGNORE INTO noti_saved_item_link (notiKey, notiRecordId, type, savedItemId, role, source, createdAt) VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any?>("key.a", "key.a_100", "task", "s_task", "evidence", "llm_auto_extraction", 3L),
            )
            db.query("SELECT COUNT(*) FROM noti_saved_item_link").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }

            // 3. New saved_item columns with defaults.
            db.query("SELECT isStarred, doAtMs, lastViewedChangeAt FROM saved_item WHERE savedItemId = 's_task'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
                assertEquals(0L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
            }

            // 4. Change log + journal tables exist and accept rows.
            assertTrue(tableExists(db, "saved_item_change_log"))
            assertTrue(tableExists(db, "extraction_journal_entry"))
            assertTrue(tableExists(db, "extraction_journal_summary"))
            db.execSQL(
                "INSERT INTO saved_item_change_log (savedItemId, createdAt, changeType) VALUES (?,?,?)",
                arrayOf<Any?>("s_task", 5L, "created"),
            )
            db.execSQL(
                "INSERT INTO extraction_journal_entry (notiKey, createdAt, eventType, savedItemId, itemTitle, detail) VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>("key.a", 5L, "item_created", "s_task", "title", ""),
            )
            db.execSQL(
                "INSERT INTO extraction_journal_summary (notiKey, summaryText, lastFoldedAt, foldedEntryCount) VALUES (?,?,?,?)",
                arrayOf<Any?>("key.a", "summary", 5L, 3),
            )

            // 5. Deleting the saved item cascades links and change-log rows (journal survives by design).
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM saved_item WHERE savedItemId = 's_task'")
            db.query("SELECT COUNT(*) FROM noti_saved_item_link").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM saved_item_change_log").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM extraction_journal_entry").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    private fun insertSavedItem(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            """
            INSERT INTO saved_item (
                savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, origin,
                humanEditCount, deletedAtMs, userEdited, isVisible, buttons, isViewed,
                sortScore, reRankHistory
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                id, "title", "content", "task", "saved", 0L,
                0L, 0L, 0L, 0L, "manual",
                0, null, 0, 1, "[]", 1,
                50.0, "[]",
            ),
        )
    }

    private fun openV40Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(40) {
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
                        PRIMARY KEY(`savedItemId`)
                    )
                    """.trimIndent()
                )
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
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { /* no-op */ }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
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
}
