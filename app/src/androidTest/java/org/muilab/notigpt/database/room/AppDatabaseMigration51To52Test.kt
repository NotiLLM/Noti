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
class AppDatabaseMigration51To52Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration51To52_preservesHistoryAndCreatesPendingReviewDrafts() {
        helper.createDatabase(DB_NAME, 51).use { db ->
            db.execSQL(
                """
                INSERT INTO saved_item (
                    savedItemId,title,content,itemType,state,lastUpdateTimestamp,deadlineAtMs,
                    startAtMs,endAtMs,origin,humanEditCount,userEdited,buttons,isViewed,isStarred,
                    whenAtMs,lastViewedChangeAt
                ) VALUES ('target','Target','Existing','task','saved',100,0,0,0,'llm',0,0,'[]',1,0,0,0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO saved_item_change_log (
                    changeId,savedItemId,createdAt,changeType,changeSummary,appendedContent,
                    addedSubTasksJson,removedSubTasksJson,changedFieldsJson,evidenceRecordIdsJson,origin
                ) VALUES (7,'target',101,'llm_update','Existing history','','[]','[]','{}','[]','llm')
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            52,
            true,
            AppDatabaseMigrations.MIGRATION_51_52,
        ).use { db ->
            db.query(
                "SELECT changeSummary,sourceSavedItemId,sourceItemTitle " +
                    "FROM saved_item_change_log WHERE changeId=7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing history", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("", cursor.getString(2))
            }

            db.execSQL(
                "INSERT INTO pending_review_draft(reviewKey,whenAtMs,updatedAt) " +
                    "VALUES ('group:1',9223372036854775807,200)"
            )
            db.query("SELECT whenAtMs,updatedAt FROM pending_review_draft WHERE reviewKey='group:1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(Long.MAX_VALUE, cursor.getLong(0))
                assertEquals(200L, cursor.getLong(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-51-52-test"
    }
}
