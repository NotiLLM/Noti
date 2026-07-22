package org.muilab.notigpt.data.repository.personalization

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.muilab.notigpt.data.local.room.dao.ExtractionPreferenceDao
import org.muilab.notigpt.data.local.room.dao.UserContextDao
import org.muilab.notigpt.domain.personalization.PersonalizationMutationPlan
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore

/**
 * Read-only bridge from the live v54 stores to the field-free personalization contract.
 *
 * General Preferences do not exist in v54, and no prepared mutation is persisted before the single
 * coordinated activation. Plan 04-10 removes this adapter when all three v55 DAOs become active.
 */
class V54PersonalizationGateway(
    private val extractionPreferenceDao: ExtractionPreferenceDao,
    private val userContextDao: UserContextDao,
) : PersonalizationStoreGateway {
    override fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>> = combine(
        extractionPreferenceDao.getAllPreferencesFlow(),
        userContextDao.getAllContextsFlow(),
    ) { extractionPreferences, userContexts ->
        extractionPreferences.map { preference ->
            PersonalizationRecordSnapshot(
                targetStore = PersonalizationStore.EXTRACTION_PREFERENCE,
                id = preference.id,
                statement = preference.statement,
                createdAt = preference.createdAt,
                updatedAt = preference.updatedAt,
            )
        } + userContexts.map { context ->
            PersonalizationRecordSnapshot(
                targetStore = PersonalizationStore.USER_KNOWLEDGE,
                id = context.id,
                statement = context.statement,
                createdAt = context.createdAt,
                updatedAt = context.updatedAt,
            )
        }
    }

    override suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot> =
        extractionPreferenceDao.getAllPreferences().map { preference ->
            PersonalizationRecordSnapshot(
                targetStore = PersonalizationStore.EXTRACTION_PREFERENCE,
                id = preference.id,
                statement = preference.statement,
                createdAt = preference.createdAt,
                updatedAt = preference.updatedAt,
            )
        } + userContextDao.getAllContexts().map { context ->
            PersonalizationRecordSnapshot(
                targetStore = PersonalizationStore.USER_KNOWLEDGE,
                id = context.id,
                statement = context.statement,
                createdAt = context.createdAt,
                updatedAt = context.updatedAt,
            )
        }

    override suspend fun commit(
        plan: PersonalizationMutationPlan,
        committedAt: Long,
    ): PersonalizationApplyResult = PersonalizationApplyResult.ActivationPending
}
