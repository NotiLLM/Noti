package org.muilab.notigpt.database.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration49To50Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration49To50_flattensSubtasksAndRenamesWhenFields() {
        helper.createDatabase(DB_NAME, 49).use { db ->
            db.execSQL(
                "INSERT INTO saved_item " +
                    "(savedItemId,title,content,itemType,lastUpdateTimestamp,deadlineAtMs,origin,humanEditCount,userEdited,buttons,doAtMs) " +
                    "VALUES ('task','Task','Body','task',10,0,'manual',0,0,'[]',1234)"
            )
            db.execSQL(
                "INSERT INTO saved_item " +
                    "(savedItemId,title,content,itemType,lastUpdateTimestamp,deadlineAtMs,origin,humanEditCount,userEdited,buttons,doAtMs) " +
                    "VALUES ('keep','Keep','Notes','keep',10,0,'manual',0,0,'[]',5678)"
            )
            db.execSQL(
                "INSERT INTO saved_sub_item " +
                    "(savedSubItemId,parentSavedItemId,title,description,isCompleted,deadlineAtMs,buttons,sortOrder,createdAt,lastUpdateTimestamp) " +
                    "VALUES ('later','task','Second','detail',0,9000,'[{\"type\":\"copy\",\"intent\":\"B\"}]',9,20,20)"
            )
            db.execSQL(
                "INSERT INTO saved_sub_item " +
                    "(savedSubItemId,parentSavedItemId,title,description,isCompleted,deadlineAtMs,buttons,sortOrder,createdAt,lastUpdateTimestamp) " +
                    "VALUES ('first','task','First','',1,0,'[]',1,10,10)"
            )
            db.execSQL(
                "INSERT INTO saved_sub_item " +
                    "(savedSubItemId,parentSavedItemId,title,description,isCompleted,deadlineAtMs,buttons,sortOrder,createdAt,lastUpdateTimestamp) " +
                    "VALUES ('keep-child','keep','Kept line','',0,0,'[]',0,10,10)"
            )
            db.execSQL(
                "INSERT INTO noti_saved_item_link " +
                    "(linkId,notiKey,notiRecordId,type,savedItemId,role,source,createdAt) " +
                    "VALUES (7,'thread','thread_1','task','task','evidence','llm',10)"
            )
            db.execSQL(
                "INSERT INTO saved_item_change_log (changeId,savedItemId,createdAt,changeType) " +
                    "VALUES (8,'task',10,'llm_update')"
            )
            db.execSQL("INSERT INTO noti_llm_state (notiKey,shouldExtractReminder) VALUES ('thread',1)")
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            50,
            true,
            AppDatabaseMigrations.MIGRATION_49_50,
        ).use { db ->
            assertTrue(columnExists(db, "saved_item", "whenAtMs"))
            assertFalse(columnExists(db, "saved_item", "doAtMs"))
            assertTrue(columnExists(db, "noti_llm_state", "shouldExtractSavedItem"))
            assertFalse(columnExists(db, "saved_sub_item", "description"))
            assertTrue(columnExists(db, "saved_sub_item", "position"))

            db.query("SELECT whenAtMs FROM saved_item WHERE savedItemId='task'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1234L, it.getLong(0))
            }
            db.query("SELECT text,isCompleted,position FROM saved_sub_item WHERE parentSavedItemId='task' ORDER BY position").use {
                assertTrue(it.moveToFirst())
                assertEquals("First", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertTrue(it.moveToNext())
                assertTrue(it.getString(0).contains("Second"))
                assertEquals(1, it.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM saved_sub_item WHERE parentSavedItemId='keep'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT content FROM saved_item WHERE savedItemId='keep'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(0).contains("Kept line"))
            }
            db.query("SELECT shouldExtractSavedItem FROM noti_llm_state WHERE notiKey='thread'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            db.query("SELECT savedItemId FROM noti_saved_item_link WHERE linkId=7").use {
                assertTrue(it.moveToFirst())
                assertEquals("task", it.getString(0))
            }
            db.query("SELECT savedItemId FROM saved_item_change_log WHERE changeId=8").use {
                assertTrue(it.moveToFirst())
                assertEquals("task", it.getString(0))
            }

            db.execSQL("DELETE FROM saved_item WHERE savedItemId='task'")
            db.query("SELECT COUNT(*) FROM saved_sub_item WHERE parentSavedItemId='task'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private fun columnExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String,
    ): Boolean = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.any { it == column }
    }

    private companion object {
        const val DB_NAME = "migration-49-50-test"
    }
}
