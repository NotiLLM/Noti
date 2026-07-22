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
class PersonalizationActivationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration54To55_replacesLegacyStoresAndDeduplicatesExactStatements() {
        helper.createDatabase(DB_NAME, 54).use { db ->
            db.execSQL(
                "INSERT INTO extraction_preferences " +
                    "(id,statement,preferenceType,createdAt,updatedAt) VALUES " +
                    "('extract-preserved','Keep release notes','WHAT_TO_EXTRACT',10,20)," +
                    "('extract-old','Ignore promotions','WHETHER_TO_EXTRACT',30,40)," +
                    "('extract-new','Ignore promotions','REPRESENTATION',31,50)," +
                    "('extract-tie-z','Keep receipts','WHAT_TO_EXTRACT',60,70)," +
                    "('extract-tie-a','Keep receipts','REPRESENTATION',61,70)," +
                    "('extract-blank','   ','WHAT_TO_EXTRACT',80,90)",
            )
            db.execSQL(
                "INSERT INTO user_contexts " +
                    "(id,statement,category,createdAt,updatedAt) VALUES " +
                    "('knowledge-preserved','I work remotely','profession',100,110)," +
                    "('knowledge-old','I study Korean','interest',120,130)," +
                    "('knowledge-new','I study Korean','education',121,140)," +
                    "('knowledge-tie-z','I live in Taipei','location',150,160)," +
                    "('knowledge-tie-a','I live in Taipei','schedule',151,160)," +
                    "('knowledge-blank','', 'other',170,180)",
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            55,
            true,
            AppDatabaseMigrations.MIGRATION_54_55,
        ).use { db ->
            assertEquals(0, countRows(db, "general_preferences"))
            assertEquals(
                listOf("id", "statement", "createdAt", "updatedAt"),
                columnNames(db, "general_preferences"),
            )
            assertEquals(
                listOf("id", "statement", "createdAt", "updatedAt"),
                columnNames(db, "extraction_preferences"),
            )
            assertEquals(
                listOf("id", "statement", "createdAt", "updatedAt"),
                columnNames(db, "user_knowledge"),
            )

            db.query(
                "SELECT id,statement,createdAt,updatedAt FROM extraction_preferences ORDER BY id",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("extract-new", cursor.getString(0))
                assertEquals("Ignore promotions", cursor.getString(1))
                assertEquals(31L, cursor.getLong(2))
                assertEquals(50L, cursor.getLong(3))

                assertTrue(cursor.moveToNext())
                assertEquals("extract-preserved", cursor.getString(0))
                assertEquals("Keep release notes", cursor.getString(1))
                assertEquals(10L, cursor.getLong(2))
                assertEquals(20L, cursor.getLong(3))

                assertTrue(cursor.moveToNext())
                assertEquals("extract-tie-a", cursor.getString(0))
                assertEquals("Keep receipts", cursor.getString(1))
                assertEquals(61L, cursor.getLong(2))
                assertEquals(70L, cursor.getLong(3))
                assertFalse(cursor.moveToNext())
            }

            db.query(
                "SELECT id,statement,createdAt,updatedAt FROM user_knowledge ORDER BY id",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("knowledge-new", cursor.getString(0))
                assertEquals("I study Korean", cursor.getString(1))
                assertEquals(121L, cursor.getLong(2))
                assertEquals(140L, cursor.getLong(3))

                assertTrue(cursor.moveToNext())
                assertEquals("knowledge-preserved", cursor.getString(0))
                assertEquals("I work remotely", cursor.getString(1))
                assertEquals(100L, cursor.getLong(2))
                assertEquals(110L, cursor.getLong(3))

                assertTrue(cursor.moveToNext())
                assertEquals("knowledge-tie-a", cursor.getString(0))
                assertEquals("I live in Taipei", cursor.getString(1))
                assertEquals(151L, cursor.getLong(2))
                assertEquals(160L, cursor.getLong(3))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun countRows(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Int = db.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun columnNames(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): List<String> = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private companion object {
        const val DB_NAME = "personalization-activation-test"
    }
}
