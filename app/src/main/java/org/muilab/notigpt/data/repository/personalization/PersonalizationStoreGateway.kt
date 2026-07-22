package org.muilab.notigpt.data.repository.personalization

import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.domain.personalization.PersonalizationMutationPlan
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot

/** Persistence port used by the stable personalization repository boundary. */
interface PersonalizationStoreGateway {
    fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>>

    suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot>

    suspend fun commit(
        plan: PersonalizationMutationPlan,
        committedAt: Long,
    ): PersonalizationApplyResult
}
