package org.muilab.notigpt.database.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration55To56Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration55To56_preservesReviewDraftAndAddsSplitBatch() {
        helper.createDatabase(DB_NAME, 55).use { db ->
            db.execSQL(
                "INSERT INTO pending_review_draft(reviewKey,translationStateJson,updatedAt) " +
                    "VALUES ('item_source','{\"status\":\"ready\"}',100)",
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            56,
            true,
            AppDatabaseMigrations.MIGRATION_55_56,
        ).use { db ->
            db.execSQL(
                "UPDATE saved_item SET pendingTransformType='split', " +
                    "pendingTransformStatus='processing' WHERE savedItemId='missing'",
            )
            db.execSQL(
                "UPDATE pending_review_draft SET batchDraftJson='[{\"title\":\"A\"}]' " +
                    "WHERE reviewKey='item_source'",
            )
            db.query(
                "SELECT translationStateJson,batchDraftJson FROM pending_review_draft " +
                    "WHERE reviewKey='item_source'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{\"status\":\"ready\"}", cursor.getString(0))
                assertEquals("[{\"title\":\"A\"}]", cursor.getString(1))
            }
            db.query("PRAGMA table_info(saved_item)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
                assertTrue("pendingTransformType" in columns)
                assertTrue("pendingTransformStatus" in columns)
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-55-56-test"
    }
}
