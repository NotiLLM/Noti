package org.muilab.notigpt.database.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations

/** Validates persisted per-thread cooldown state for automatic Stage B requests. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration47To48Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration47To48_addsItemExtractionCooldownTimestamp() {
        helper.createDatabase(DB_NAME, 47).use { db ->
            db.execSQL("INSERT INTO noti_llm_state (notiKey) VALUES ('thread')")
        }
        helper.runMigrationsAndValidate(
            DB_NAME,
            48,
            true,
            AppDatabaseMigrations.MIGRATION_47_48,
        ).use { db ->
            db.query("SELECT lastItemExtractionAt FROM noti_llm_state WHERE notiKey = 'thread'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-47-48-test"
    }
}
