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
class AppDatabaseMigration52To53Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration52To53_addsTranslationStateWithoutLosingWhen() {
        helper.createDatabase(DB_NAME, 52).use { db ->
            db.execSQL(
                "INSERT INTO pending_review_draft(reviewKey,whenAtMs,updatedAt) " +
                    "VALUES ('create_7',1234,100)",
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            53,
            true,
            AppDatabaseMigrations.MIGRATION_52_53,
        ).use { db ->
            db.execSQL(
                "UPDATE pending_review_draft SET translationStateJson='{" +
                    "\"status\":\"pending\"}' WHERE reviewKey='create_7'",
            )
            db.query(
                "SELECT whenAtMs,translationStateJson FROM pending_review_draft WHERE reviewKey='create_7'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1234L, cursor.getLong(0))
                assertEquals("{\"status\":\"pending\"}", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-52-53-test"
    }
}
