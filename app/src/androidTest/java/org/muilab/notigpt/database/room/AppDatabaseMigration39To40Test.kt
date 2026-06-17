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
 * Direct test of [AppDatabaseMigrations.MIGRATION_39_40].
 *
 * Because the database disables schema export, this builds the relevant slice of the v39 schema by hand,
 * seeds it, runs the migration's [androidx.room.migration.Migration.migrate], and asserts the link-table
 * backfill plus the dropped columns/table.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration39To40Test {

    private val dbName = "migration-39-40-test.db"
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
    fun migration39To40_backfillsLinks_andDropsLegacyColumnsAndSnapshotTable() {
        val db = openV39Database()
        try {
            // A task with two source records and an archived keep with one.
            insertSavedItem(db, id = "s_task", itemType = "task", associatedNotis = """["key.a_100","key.a_200"]""", isPinned = 1)
            insertSavedItem(db, id = "s_keep", itemType = "keep", associatedNotis = """["key.b_300"]""", isPinned = 0)
            // A saved item with no associations should produce no link rows.
            insertSavedItem(db, id = "s_empty", itemType = "task", associatedNotis = "[]", isPinned = 0)

            db.execSQL(
                "INSERT INTO reminder_extraction_snapshot (snapshotId, status, savedItemId, payloadJson, createdAt) VALUES (?,?,?,?,?)",
                arrayOf<Any?>("snap_1", "KEPT", "s_task", "{}", 1L),
            )

            AppDatabaseMigrations.MIGRATION_39_40.migrate(db)

            // 1. Links backfilled with derived notiKey and type mirroring itemType.
            val links = mutableListOf<List<String>>()
            db.query("SELECT notiKey, notiRecordId, type, savedItemId FROM noti_saved_item_link ORDER BY notiRecordId").use { c ->
                while (c.moveToNext()) {
                    links += listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
                }
            }
            assertEquals(3, links.size)
            assertEquals(listOf("key.a", "key.a_100", "task", "s_task"), links[0])
            assertEquals(listOf("key.a", "key.a_200", "task", "s_task"), links[1])
            assertEquals(listOf("key.b", "key.b_300", "keep", "s_keep"), links[2])

            // 2. Legacy columns dropped from saved_item.
            val savedItemCols = tableColumns(db, "saved_item")
            assertFalse("associatedNotis" in savedItemCols)
            assertFalse("sourceExtractionSnapshotId" in savedItemCols)
            assertFalse("isPinned" in savedItemCols)
            // Retained columns survive.
            assertTrue("sortScore" in savedItemCols)
            assertTrue("itemType" in savedItemCols)

            // 3. Snapshot table dropped.
            assertFalse(tableExists(db, "reminder_extraction_snapshot"))

            // 4. saved_item rows preserved.
            db.query("SELECT COUNT(*) FROM saved_item").use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    private fun insertSavedItem(db: SupportSQLiteDatabase, id: String, itemType: String, associatedNotis: String, isPinned: Int) {
        db.execSQL(
            """
            INSERT INTO saved_item (
                savedItemId, title, content, itemType, state, lastUpdateTimestamp,
                deadlineAtMs, startAtMs, endAtMs, estimatedCompletionTime, associatedNotis,
                sourceExtractionSnapshotId, origin, humanEditCount, deletedAtMs, userEdited,
                isVisible, buttons, isViewed, isPinned, sortScore, reRankHistory
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                id, "title", "content", itemType, "saved", 0L,
                0L, 0L, 0L, 0L, associatedNotis,
                null, "manual", 0, null, 0,
                1, "[]", 1, isPinned, 50.0, "[]",
            ),
        )
    }

    private fun openV39Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(39) {
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
