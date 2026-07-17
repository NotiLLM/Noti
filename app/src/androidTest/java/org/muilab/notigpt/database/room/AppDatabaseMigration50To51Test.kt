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
class AppDatabaseMigration50To51Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration50To51_removesReadStateAndPreservesDrawerRows() {
        helper.createDatabase(DB_NAME, 50).use { db ->
            db.execSQL(
                """
                INSERT INTO noti_drawer (
                    notiKey,pkgName,hashKey,groupKey,isAppGroup,isGroupChat,sortKey,appName,
                    lastUpdateTime,lastSyncTime,icon,largeIcon,isPeople,isPinned,isArchived,
                    isDismissed,isRead,isSetToTop,setToTopTime,sortPosition,explanation,summary,sortScore
                ) VALUES (
                    'thread','org.example',7,'group',0,0,'sort','Example',100,90,'','',0,1,0,
                    0,1,1,80,3,'why','summary',42.5
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            51,
            true,
            AppDatabaseMigrations.MIGRATION_50_51,
        ).use { db ->
            assertFalse(columnExists(db, "noti_drawer", "isRead"))
            db.query(
                "SELECT pkgName,isPinned,isDismissed,isSetToTop,setToTopTime,sortPosition,summary,sortScore " +
                    "FROM noti_drawer WHERE notiKey='thread'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("org.example", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(80L, cursor.getLong(4))
                assertEquals(3, cursor.getInt(5))
                assertEquals("summary", cursor.getString(6))
                assertEquals(42.5, cursor.getDouble(7), 0.0)
            }
        }
    }

    private fun columnExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String,
    ): Boolean = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }

    private companion object {
        const val DB_NAME = "migration-50-51-test"
    }
}
