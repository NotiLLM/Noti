package org.muilab.notigpt.domain.personalization

import java.text.BreakIterator
import java.util.Locale

/** A complete write set that may be committed atomically after successful preflight. */
class PersonalizationMutationPlan internal constructor(
    val changeSetProposalId: String,
    val writes: List<PlannedPersonalizationWrite>,
)

/** Persistence-neutral writes. Creation identity and write timestamps are assigned only at commit. */
sealed interface PlannedPersonalizationWrite {
    val proposalId: String
    val targetStore: PersonalizationStore

    data class Create(
        override val proposalId: String,
        override val targetStore: PersonalizationStore,
        val statement: String,
    ) : PlannedPersonalizationWrite

    data class Update(
        override val proposalId: String,
        override val targetStore: PersonalizationStore,
        val id: String,
        val statement: String,
        val createdAt: Long,
        val expectedUpdatedAt: Long,
    ) : PlannedPersonalizationWrite

    data class Delete(
        override val proposalId: String,
        override val targetStore: PersonalizationStore,
        val id: String,
        val expectedUpdatedAt: Long,
    ) : PlannedPersonalizationWrite
}

sealed interface PersonalizationPreflightResult {
    data class Ready(val plan: PersonalizationMutationPlan) : PersonalizationPreflightResult

    data class Rejected(val failure: PersonalizationPreflightFailure) : PersonalizationPreflightResult
}

data class PersonalizationPreflightFailure(
    val code: Code,
    val detail: String,
) {
    enum class Code {
        DUPLICATE_SNAPSHOT,
        DUPLICATE_TARGET,
        TARGET_NOT_FOUND,
        STALE_TARGET,
        INVALID_STATEMENT,
    }
}

/** Builds an all-or-nothing persistence plan without touching Room or assigning durable identity. */
object PersonalizationMutationPlanner {
    fun plan(
        changeSet: PersonalizationChangeSet,
        snapshots: List<PersonalizationRecordSnapshot>,
    ): PersonalizationPreflightResult {
        val snapshotsById = snapshots.associateBy(PersonalizationRecordSnapshot::id)
        if (snapshotsById.size != snapshots.size) {
            return rejected(
                PersonalizationPreflightFailure.Code.DUPLICATE_SNAPSHOT,
                "Confirmed snapshots contain duplicate durable IDs.",
            )
        }

        val targetedIds = mutableSetOf<String>()
        val writes = mutableListOf<PlannedPersonalizationWrite>()
        changeSet.mutations.forEach { mutation ->
            when (mutation.operation) {
                PersonalizationOperation.ADD -> {
                    val statement = mutation.statement.orEmpty()
                    if (!isAtomicStatement(statement)) {
                        return rejected(
                            PersonalizationPreflightFailure.Code.INVALID_STATEMENT,
                            "ADD requires one atomic statement.",
                        )
                    }
                    writes += PlannedPersonalizationWrite.Create(
                        proposalId = mutation.proposalId,
                        targetStore = mutation.targetStore,
                        statement = statement,
                    )
                }

                PersonalizationOperation.UPDATE, PersonalizationOperation.DELETE -> {
                    val expected = requireNotNull(mutation.expectedTarget)
                    if (!targetedIds.add(expected.id)) {
                        return rejected(
                            PersonalizationPreflightFailure.Code.DUPLICATE_TARGET,
                            "A change set targets ${expected.id} more than once.",
                        )
                    }
                    val current = snapshotsById[expected.id]
                    if (current == null || current.targetStore != mutation.targetStore) {
                        return rejected(
                            PersonalizationPreflightFailure.Code.TARGET_NOT_FOUND,
                            "Target ${expected.id} is absent from ${mutation.targetStore}.",
                        )
                    }
                    if (current.updatedAt != expected.updatedAt) {
                        return rejected(
                            PersonalizationPreflightFailure.Code.STALE_TARGET,
                            "Target ${expected.id} changed after the proposal snapshot.",
                        )
                    }

                    if (mutation.operation == PersonalizationOperation.UPDATE) {
                        val statement = mutation.statement.orEmpty()
                        if (!isAtomicStatement(statement)) {
                            return rejected(
                                PersonalizationPreflightFailure.Code.INVALID_STATEMENT,
                                "UPDATE requires one atomic statement.",
                            )
                        }
                        writes += PlannedPersonalizationWrite.Update(
                            proposalId = mutation.proposalId,
                            targetStore = mutation.targetStore,
                            id = current.id,
                            statement = statement,
                            createdAt = current.createdAt,
                            expectedUpdatedAt = current.updatedAt,
                        )
                    } else {
                        writes += PlannedPersonalizationWrite.Delete(
                            proposalId = mutation.proposalId,
                            targetStore = mutation.targetStore,
                            id = current.id,
                            expectedUpdatedAt = current.updatedAt,
                        )
                    }
                }
            }
        }

        return PersonalizationPreflightResult.Ready(
            PersonalizationMutationPlan(
                changeSetProposalId = changeSet.proposalId,
                writes = writes.toList(),
            ),
        )
    }

    private fun isAtomicStatement(statement: String): Boolean {
        if (statement.isBlank() || statement.none(Char::isLetterOrDigit)) return false
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT)
        iterator.setText(statement)
        var start = iterator.first()
        var sentenceCount = 0
        while (true) {
            val end = iterator.next()
            if (end == BreakIterator.DONE) break
            if (statement.substring(start, end).any(Char::isLetterOrDigit)) sentenceCount++
            start = end
        }
        return sentenceCount == 1
    }

    private fun rejected(
        code: PersonalizationPreflightFailure.Code,
        detail: String,
    ) = PersonalizationPreflightResult.Rejected(PersonalizationPreflightFailure(code, detail))
}
