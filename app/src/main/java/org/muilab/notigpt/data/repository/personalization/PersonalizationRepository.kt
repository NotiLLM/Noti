package org.muilab.notigpt.data.repository.personalization

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.FirestoreOutboxDao
import org.muilab.notigpt.data.local.room.dao.GeneralPreferenceDao
import org.muilab.notigpt.data.local.room.dao.UserKnowledgeDao
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutationPlanner
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightFailure
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightResult
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import org.muilab.notigpt.domain.personalization.PlannedPersonalizationWrite
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.GeneralPreference
import org.muilab.notigpt.model.features.UserKnowledge
import java.util.UUID

/** Stable consumer-facing boundary for confirmed personalization state and atomic change sets. */
interface PersonalizationRepository {
    fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>>

    suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot>

    suspend fun apply(changeSet: PersonalizationChangeSet): PersonalizationApplyResult
}

sealed interface PersonalizationApplyResult {
    data class Applied(val changedRecordIds: List<String>) : PersonalizationApplyResult

    data class Rejected(val failure: PersonalizationPreflightFailure) : PersonalizationApplyResult
}

/**
 * Owns confirmed personalization persistence and its payload-free Firestore outbox instructions.
 *
 * Preflight and every selected write execute inside the same Room transaction so a stale target,
 * failed write, or outbox failure cannot expose a partially applied cross-store change set.
 */
class RoomPersonalizationRepository(
    private val database: RoomDatabase,
    private val generalPreferenceDao: GeneralPreferenceDao,
    private val extractionPreferenceDao: ExtractionPreferenceDao,
    private val userKnowledgeDao: UserKnowledgeDao,
    private val firestoreOutboxDao: FirestoreOutboxDao,
    private val uidProvider: () -> String = { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : PersonalizationRepository {
    override fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>> = combine(
        generalPreferenceDao.observeAll(),
        extractionPreferenceDao.observeAll(),
        userKnowledgeDao.observeAll(),
    ) { general, extraction, knowledge ->
        general.map { it.snapshot(PersonalizationStore.GENERAL_PREFERENCE) } +
            extraction.map { it.snapshot() } +
            knowledge.map { it.snapshot(PersonalizationStore.USER_KNOWLEDGE) }
    }

    override suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot> =
        confirmedSnapshots()

    override suspend fun apply(changeSet: PersonalizationChangeSet): PersonalizationApplyResult {
        val uid = uidProvider().also { check(it.isNotBlank()) { "Confirmed personalization requires an account." } }
        return database.withTransaction {
            when (
                val preflight = PersonalizationMutationPlanner.plan(
                    changeSet = changeSet,
                    snapshots = confirmedSnapshots(),
                )
            ) {
                is PersonalizationPreflightResult.Rejected ->
                    PersonalizationApplyResult.Rejected(preflight.failure)

                is PersonalizationPreflightResult.Ready -> {
                    val committedAt = clock()
                    val changedIds = preflight.plan.writes.map { write ->
                        val id = when (write) {
                            is PlannedPersonalizationWrite.Create -> idFactory()
                            is PlannedPersonalizationWrite.Update -> write.id
                            is PlannedPersonalizationWrite.Delete -> write.id
                        }
                        when (write) {
                            is PlannedPersonalizationWrite.Create -> upsert(
                                store = write.targetStore,
                                id = id,
                                statement = write.statement,
                                createdAt = committedAt,
                                updatedAt = committedAt,
                            )
                            is PlannedPersonalizationWrite.Update -> upsert(
                                store = write.targetStore,
                                id = id,
                                statement = write.statement,
                                createdAt = write.createdAt,
                                updatedAt = committedAt,
                            )
                            is PlannedPersonalizationWrite.Delete -> delete(write.targetStore, id)
                        }
                        firestoreOutboxDao.upsert(
                            FirestoreOutboxOp.personalization(
                                uid = uid,
                                store = write.targetStore.name.lowercase(),
                                recordId = id,
                                createdAt = committedAt,
                            ),
                        )
                        id
                    }
                    PersonalizationApplyResult.Applied(changedIds)
                }
            }
        }
    }

    private suspend fun confirmedSnapshots(): List<PersonalizationRecordSnapshot> =
        generalPreferenceDao.getAll().map { it.snapshot(PersonalizationStore.GENERAL_PREFERENCE) } +
            extractionPreferenceDao.getAll().map { it.snapshot() } +
            userKnowledgeDao.getAll().map { it.snapshot(PersonalizationStore.USER_KNOWLEDGE) }

    private suspend fun upsert(
        store: PersonalizationStore,
        id: String,
        statement: String,
        createdAt: Long,
        updatedAt: Long,
    ) = when (store) {
        PersonalizationStore.GENERAL_PREFERENCE -> generalPreferenceDao.upsert(
            GeneralPreference(id, statement, createdAt, updatedAt),
        )
        PersonalizationStore.EXTRACTION_PREFERENCE -> extractionPreferenceDao.upsert(
            ExtractionPreference(id, statement, createdAt, updatedAt),
        )
        PersonalizationStore.USER_KNOWLEDGE -> userKnowledgeDao.upsert(
            UserKnowledge(id, statement, createdAt, updatedAt),
        )
    }

    private suspend fun delete(store: PersonalizationStore, id: String) = when (store) {
        PersonalizationStore.GENERAL_PREFERENCE -> generalPreferenceDao.deleteById(id)
        PersonalizationStore.EXTRACTION_PREFERENCE -> extractionPreferenceDao.deleteById(id)
        PersonalizationStore.USER_KNOWLEDGE -> userKnowledgeDao.deleteById(id)
    }

    private fun GeneralPreference.snapshot(store: PersonalizationStore) = PersonalizationRecordSnapshot(
        targetStore = store,
        id = id,
        statement = statement,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ExtractionPreference.snapshot() = PersonalizationRecordSnapshot(
        targetStore = PersonalizationStore.EXTRACTION_PREFERENCE,
        id = id,
        statement = statement,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun UserKnowledge.snapshot(store: PersonalizationStore) = PersonalizationRecordSnapshot(
        targetStore = store,
        id = id,
        statement = statement,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
