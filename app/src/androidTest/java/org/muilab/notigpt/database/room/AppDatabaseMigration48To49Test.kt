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

/** Verifies proposal table renames preserve rows and queued Firestore sync operations. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration48To49Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration48To49_renamesProposalTablesAndPreservesData() {
        helper.createDatabase(DB_NAME, 48).use { db ->
            db.execSQL(
                "INSERT INTO pending_op " +
                    "(opId, notiKey, opType, payload, targetItemId, mergeSourceItemIds, " +
                    "evidenceRecordIds, reason, itemType, batchId, createdAt) " +
                    "VALUES (1, 'thread', 'create', '{}', '', '[]', '[]', 'reason', 'task', 'batch', 10)"
            )
            db.execSQL(
                "INSERT INTO generated_proposal " +
                    "(proposalId, uid, opId, batchId, opType, payload, targetItemId, itemType, " +
                    "decision, createdAt, decisionAt) " +
                    "VALUES ('u:p_1', 'u', 1, 'batch', 'create', '{}', '', 'task', 'pending', 10, 0)"
            )
            db.execSQL(
                "INSERT INTO firestore_outbox " +
                    "(operationKey, uid, kind, entityId, createdAt, attemptCount, lastError) " +
                    "VALUES ('u:generated_proposal:u:p_1', 'u', 'sync_generated_proposal', " +
                    "'u:p_1', 10, 0, '')"
            )
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            49,
            true,
            AppDatabaseMigrations.MIGRATION_48_49,
        ).use { db ->
            assertTrue(tableExists(db, "pending_proposed_op"))
            assertTrue(tableExists(db, "proposed_op_record"))
            assertFalse(tableExists(db, "pending_op"))
            assertFalse(tableExists(db, "generated_proposal"))

            db.query("SELECT reason FROM pending_proposed_op WHERE opId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("reason", cursor.getString(0))
            }
            db.query("SELECT decision FROM proposed_op_record WHERE proposalId = 'u:p_1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("pending", cursor.getString(0))
            }
            db.query("SELECT operationKey, kind FROM firestore_outbox WHERE entityId = 'u:p_1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("u:proposed_op_record:u:p_1", cursor.getString(0))
                assertEquals("sync_proposed_op_record", cursor.getString(1))
            }
        }
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): Boolean =
        db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { it.moveToFirst() }

    private companion object {
        const val DB_NAME = "migration-48-49-test"
    }
}
