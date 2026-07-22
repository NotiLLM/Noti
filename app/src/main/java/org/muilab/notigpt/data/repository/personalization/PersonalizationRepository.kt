package org.muilab.notigpt.data.repository.personalization

import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutationPlanner
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightFailure
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightResult
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot

/** Stable consumer-facing boundary for confirmed personalization state and atomic change sets. */
interface PersonalizationRepository {
    fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>>

    suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot>

    suspend fun apply(changeSet: PersonalizationChangeSet): PersonalizationApplyResult
}

sealed interface PersonalizationApplyResult {
    data class Applied(val changedRecordIds: List<String>) : PersonalizationApplyResult

    data class Rejected(val failure: PersonalizationPreflightFailure) : PersonalizationApplyResult

    /** The compile-safe v54 adapter cannot persist the prepared three-store contract. */
    data object ActivationPending : PersonalizationApplyResult
}

/** Runs pure preflight before the gateway can observe any write operation. */
class StoreBackedPersonalizationRepository(
    private val gateway: PersonalizationStoreGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : PersonalizationRepository {
    override fun observeConfirmedSnapshots(): Flow<List<PersonalizationRecordSnapshot>> =
        gateway.observeConfirmedSnapshots()

    override suspend fun getConfirmedSnapshots(): List<PersonalizationRecordSnapshot> =
        gateway.getConfirmedSnapshots()

    override suspend fun apply(changeSet: PersonalizationChangeSet): PersonalizationApplyResult {
        return when (
            val preflight = PersonalizationMutationPlanner.plan(
                changeSet = changeSet,
                snapshots = gateway.getConfirmedSnapshots(),
            )
        ) {
            is PersonalizationPreflightResult.Ready -> gateway.commit(preflight.plan, clock())
            is PersonalizationPreflightResult.Rejected -> PersonalizationApplyResult.Rejected(preflight.failure)
        }
    }
}
