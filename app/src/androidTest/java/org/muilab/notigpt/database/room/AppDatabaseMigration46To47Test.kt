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

/** Validates permanent generated-proposal retention against the committed Room schemas. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration46To47Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration46To47_createsGeneratedProposalDecisionStore() {
        helper.createDatabase(DB_NAME, 46).close()
        helper.runMigrationsAndValidate(
            DB_NAME,
            47,
            true,
            AppDatabaseMigrations.MIGRATION_46_47,
        ).use { db ->
            db.execSQL(
                "INSERT INTO generated_proposal " +
                    "(proposalId, uid, opId, batchId, opType, payload, targetItemId, itemType, decision, createdAt, decisionAt) " +
                    "VALUES ('u:p_1', 'u', 1, 'b', 'create', '{}', '', 'task', 'rejected', 10, 20)"
            )
            db.query("SELECT decision, payload FROM generated_proposal WHERE proposalId = 'u:p_1'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("rejected", cursor.getString(0))
                assertEquals("{}", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-46-47-test"
    }
}
