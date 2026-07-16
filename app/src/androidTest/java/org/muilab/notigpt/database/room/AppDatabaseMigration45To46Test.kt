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

/** Validates the v46 outbox table against Room's committed v45 and v46 schemas. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration45To46Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration45To46_createsPayloadFreeFirestoreOutbox() {
        helper.createDatabase(DB_NAME, 45).close()

        helper.runMigrationsAndValidate(
            DB_NAME,
            46,
            true,
            AppDatabaseMigrations.MIGRATION_45_46,
        ).use { db ->
            db.execSQL(
                "INSERT INTO firestore_outbox " +
                    "(operationKey, uid, kind, entityId, createdAt, attemptCount, lastError) " +
                    "VALUES ('u:saved_item:s1', 'u', 'delete_saved_item', 's1', 100, 0, '')"
            )
            db.query("SELECT uid, kind, entityId FROM firestore_outbox").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("u", cursor.getString(0))
                assertEquals("delete_saved_item", cursor.getString(1))
                assertEquals("s1", cursor.getString(2))
                assertEquals(3, cursor.columnCount)
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-45-46-test"
    }
}
