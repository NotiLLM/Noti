package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.AppDatabaseMigrations
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.FirestoreOutboxDao
import org.muilab.notigpt.data.local.room.dao.GeneralPreferenceDao
import org.muilab.notigpt.data.local.room.dao.UserKnowledgeDao
import org.muilab.notigpt.data.repository.personalization.PersonalizationApplyResult
import org.muilab.notigpt.data.repository.personalization.RoomPersonalizationRepository
import org.muilab.notigpt.domain.personalization.ExpectedTarget
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutation
import org.muilab.notigpt.domain.personalization.PersonalizationOperation
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightFailure
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.FirestoreOutboxKind
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.GeneralPreference
import org.muilab.notigpt.model.features.UserKnowledge
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalizationActivationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private lateinit var database: ActivationTestDatabase
    private lateinit var repository: RoomPersonalizationRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ActivationTestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomPersonalizationRepository(
            database = database,
            generalPreferenceDao = database.generalPreferenceDao(),
            extractionPreferenceDao = database.extractionPreferenceDao(),
            userKnowledgeDao = database.userKnowledgeDao(),
            firestoreOutboxDao = database.firestoreOutboxDao(),
            uidProvider = { TEST_UID },
            clock = { COMMITTED_AT },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

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
            assertFalse(tableExists(db, "preference_conflicts"))

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

    @Test
    fun apply_validCrossStoreSet_commitsCrudUuidAndOutboxTogether() = runBlocking {
        database.extractionPreferenceDao().upsert(
            ExtractionPreference("extraction-1", "Create Keeps for receipts.", 10, 20),
        )
        database.userKnowledgeDao().upsert(
            UserKnowledge("knowledge-1", "I work remotely.", 30, 40),
        )

        val result = repository.apply(
            changeSet(
                mutation(
                    proposalId = "create-general",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.ADD,
                    statement = "Messages from my family deserve immediate attention.",
                ),
                mutation(
                    proposalId = "update-extraction",
                    store = PersonalizationStore.EXTRACTION_PREFERENCE,
                    operation = PersonalizationOperation.UPDATE,
                    statement = "Create Keeps for reference numbers.",
                    target = ExpectedTarget("extraction-1", 20),
                ),
                mutation(
                    proposalId = "delete-knowledge",
                    store = PersonalizationStore.USER_KNOWLEDGE,
                    operation = PersonalizationOperation.DELETE,
                    target = ExpectedTarget("knowledge-1", 40),
                ),
            ),
        )

        assertTrue(result is PersonalizationApplyResult.Applied)
        val changedIds = (result as PersonalizationApplyResult.Applied).changedRecordIds
        assertEquals(3, changedIds.size)
        val createdId = changedIds.single { it != "extraction-1" && it != "knowledge-1" }
        assertEquals(createdId, UUID.fromString(createdId).toString())

        val general = database.generalPreferenceDao().getById(createdId)!!
        assertEquals(COMMITTED_AT, general.createdAt)
        assertEquals(COMMITTED_AT, general.updatedAt)
        val extraction = database.extractionPreferenceDao().getById("extraction-1")!!
        assertEquals("Create Keeps for reference numbers.", extraction.statement)
        assertEquals(10L, extraction.createdAt)
        assertEquals(COMMITTED_AT, extraction.updatedAt)
        assertNull(database.userKnowledgeDao().getById("knowledge-1"))

        val outbox = database.firestoreOutboxDao().getPending(TEST_UID)
        assertEquals(3, outbox.size)
        assertEquals(
            setOf(
                "$TEST_UID:personalization:general_preference:$createdId",
                "$TEST_UID:personalization:extraction_preference:extraction-1",
                "$TEST_UID:personalization:user_knowledge:knowledge-1",
            ),
            outbox.mapTo(mutableSetOf(), FirestoreOutboxOp::operationKey),
        )
        assertTrue(outbox.all { it.kind == FirestoreOutboxKind.SyncPreferencesAndContexts })
    }

    @Test
    fun apply_staleTarget_rejectsWithoutWritesOrOutbox() = runBlocking {
        database.extractionPreferenceDao().upsert(
            ExtractionPreference("extraction-1", "Create Keeps for receipts.", 10, 20),
        )

        val result = repository.apply(
            changeSet(
                mutation(
                    proposalId = "stale",
                    store = PersonalizationStore.EXTRACTION_PREFERENCE,
                    operation = PersonalizationOperation.UPDATE,
                    statement = "Create Keeps for reference numbers.",
                    target = ExpectedTarget("extraction-1", 19),
                ),
            ),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.STALE_TARGET)
        assertEquals("Create Keeps for receipts.", database.extractionPreferenceDao().getById("extraction-1")?.statement)
        assertTrue(database.firestoreOutboxDao().getPending(TEST_UID).isEmpty())
    }

    @Test
    fun apply_missingTarget_rejectsWithoutWritesOrOutbox() = runBlocking {
        val result = repository.apply(
            changeSet(
                mutation(
                    proposalId = "missing",
                    store = PersonalizationStore.USER_KNOWLEDGE,
                    operation = PersonalizationOperation.DELETE,
                    target = ExpectedTarget("missing", 1),
                ),
            ),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.TARGET_NOT_FOUND)
        assertTrue(database.firestoreOutboxDao().getPending(TEST_UID).isEmpty())
    }

    @Test
    fun apply_duplicateTarget_rejectsCompleteSetWithoutWritesOrOutbox() = runBlocking {
        database.generalPreferenceDao().upsert(
            GeneralPreference("general-1", "Travel disruptions deserve attention.", 5, 6),
        )
        val target = ExpectedTarget("general-1", 6)

        val result = repository.apply(
            changeSet(
                mutation(
                    proposalId = "update",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.UPDATE,
                    statement = "Travel disruptions deserve immediate attention.",
                    target = target,
                ),
                mutation(
                    proposalId = "delete",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.DELETE,
                    target = target,
                ),
            ),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.DUPLICATE_TARGET)
        assertEquals("Travel disruptions deserve attention.", database.generalPreferenceDao().getById("general-1")?.statement)
        assertTrue(database.firestoreOutboxDao().getPending(TEST_UID).isEmpty())
    }

    @Test
    fun apply_lateRoomFailure_rollsBackEarlierWriteAndOutbox() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_extraction_insert BEFORE INSERT ON extraction_preferences " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END",
        )

        try {
            repository.apply(
                changeSet(
                    mutation(
                        proposalId = "create-general",
                        store = PersonalizationStore.GENERAL_PREFERENCE,
                        operation = PersonalizationOperation.ADD,
                        statement = "Project launch updates deserve immediate attention.",
                    ),
                    mutation(
                        proposalId = "create-extraction",
                        store = PersonalizationStore.EXTRACTION_PREFERENCE,
                        operation = PersonalizationOperation.ADD,
                        statement = "Create Todos for explicit deadlines.",
                    ),
                ),
            )
            fail("Expected the forced Room failure")
        } catch (_: Exception) {
            // Expected: the transaction must roll back every prior row and outbox operation.
        }

        assertTrue(database.generalPreferenceDao().getAll().isEmpty())
        assertTrue(database.extractionPreferenceDao().getAll().isEmpty())
        assertTrue(database.firestoreOutboxDao().getPending(TEST_UID).isEmpty())
    }

    private fun assertRejected(
        result: PersonalizationApplyResult,
        code: PersonalizationPreflightFailure.Code,
    ) {
        assertTrue(result is PersonalizationApplyResult.Rejected)
        assertEquals(code, (result as PersonalizationApplyResult.Rejected).failure.code)
    }

    private fun mutation(
        proposalId: String,
        store: PersonalizationStore,
        operation: PersonalizationOperation,
        statement: String? = null,
        target: ExpectedTarget? = null,
    ) = PersonalizationMutation(
        proposalId = proposalId,
        targetStore = store,
        operation = operation,
        statement = statement,
        expectedTarget = target,
    )

    private fun changeSet(vararg mutations: PersonalizationMutation) = PersonalizationChangeSet(
        proposalId = "set-1",
        resultingBehavior = "Apply the selected behavior.",
        mutations = mutations.toList(),
    )

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

    private fun tableExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Boolean = db.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(table),
    ).use { it.moveToFirst() }

    private companion object {
        const val DB_NAME = "personalization-activation-test"
        const val TEST_UID = "uid-test"
        const val COMMITTED_AT = 1_234L
    }
}

@Database(
    entities = [
        GeneralPreference::class,
        ExtractionPreference::class,
        UserKnowledge::class,
        FirestoreOutboxOp::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ActivationTestDatabase : RoomDatabase() {
    abstract fun generalPreferenceDao(): GeneralPreferenceDao
    abstract fun extractionPreferenceDao(): ExtractionPreferenceDao
    abstract fun userKnowledgeDao(): UserKnowledgeDao
    abstract fun firestoreOutboxDao(): FirestoreOutboxDao
}
