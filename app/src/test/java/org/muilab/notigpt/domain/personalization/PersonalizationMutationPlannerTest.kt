package org.muilab.notigpt.domain.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationMutationPlannerTest {
    @Test
    fun plan_validCrossStoreSet_preservesUpdateCreationAndDefersCreateIdentity() {
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
                mutation(
                    proposalId = "add-general",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.ADD,
                    statement = "Messages from my family deserve immediate attention.",
                ),
                mutation(
                    proposalId = "update-extraction",
                    store = PersonalizationStore.EXTRACTION_PREFERENCE,
                    operation = PersonalizationOperation.UPDATE,
                    statement = "Create Keeps for reference numbers.",
                    target = ExpectedTarget("extraction-1", 22),
                ),
                mutation(
                    proposalId = "delete-knowledge",
                    store = PersonalizationStore.USER_KNOWLEDGE,
                    operation = PersonalizationOperation.DELETE,
                    target = ExpectedTarget("knowledge-1", 33),
                ),
            ),
            snapshots = listOf(
                snapshot(PersonalizationStore.EXTRACTION_PREFERENCE, "extraction-1", 11, 22),
                snapshot(PersonalizationStore.USER_KNOWLEDGE, "knowledge-1", 30, 33),
            ),
        )

        assertTrue(result is PersonalizationPreflightResult.Ready)
        val writes = (result as PersonalizationPreflightResult.Ready).plan.writes
        assertEquals(3, writes.size)
        val create = writes[0] as PlannedPersonalizationWrite.Create
        assertEquals("add-general", create.proposalId)
        assertFalse(create::class.members.any { it.name == "id" })
        val update = writes[1] as PlannedPersonalizationWrite.Update
        assertEquals("extraction-1", update.id)
        assertEquals(11L, update.createdAt)
        assertEquals(22L, update.expectedUpdatedAt)
        val delete = writes[2] as PlannedPersonalizationWrite.Delete
        assertEquals("knowledge-1", delete.id)
    }

    @Test
    fun plan_rejectsStaleTarget() {
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
                mutation(
                    proposalId = "stale",
                    store = PersonalizationStore.EXTRACTION_PREFERENCE,
                    operation = PersonalizationOperation.UPDATE,
                    statement = "Create Todos only when a deadline is explicit.",
                    target = ExpectedTarget("extraction-1", 21),
                ),
            ),
            snapshots = listOf(snapshot(PersonalizationStore.EXTRACTION_PREFERENCE, "extraction-1", 11, 22)),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.STALE_TARGET)
    }

    @Test
    fun plan_rejectsMissingTarget() {
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
                mutation(
                    proposalId = "missing",
                    store = PersonalizationStore.USER_KNOWLEDGE,
                    operation = PersonalizationOperation.DELETE,
                    target = ExpectedTarget("knowledge-missing", 7),
                ),
            ),
            snapshots = emptyList(),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.TARGET_NOT_FOUND)
    }

    @Test
    fun plan_rejectsDuplicateMutationTarget() {
        val target = ExpectedTarget("general-1", 9)
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
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
            snapshots = listOf(snapshot(PersonalizationStore.GENERAL_PREFERENCE, "general-1", 8, 9)),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.DUPLICATE_TARGET)
    }

    @Test
    fun plan_rejectsMalformedCreateStatement() {
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
                mutation(
                    proposalId = "malformed",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.ADD,
                    statement = "Prioritize invoices. Ignore newsletters.",
                ),
            ),
            snapshots = emptyList(),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.INVALID_STATEMENT)
    }

    @Test
    fun plan_lateFailureRollsBackTheCompletePlan() {
        val result = PersonalizationMutationPlanner.plan(
            changeSet = changeSet(
                mutation(
                    proposalId = "valid-first",
                    store = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.ADD,
                    statement = "Project launch updates deserve immediate attention.",
                ),
                mutation(
                    proposalId = "missing-second",
                    store = PersonalizationStore.USER_KNOWLEDGE,
                    operation = PersonalizationOperation.DELETE,
                    target = ExpectedTarget("missing", 1),
                ),
            ),
            snapshots = emptyList(),
        )

        assertRejected(result, PersonalizationPreflightFailure.Code.TARGET_NOT_FOUND)
        assertFalse(result is PersonalizationPreflightResult.Ready)
    }

    private fun assertRejected(
        result: PersonalizationPreflightResult,
        code: PersonalizationPreflightFailure.Code,
    ) {
        assertTrue(result is PersonalizationPreflightResult.Rejected)
        assertEquals(code, (result as PersonalizationPreflightResult.Rejected).failure.code)
    }

    private fun snapshot(
        store: PersonalizationStore,
        id: String,
        createdAt: Long,
        updatedAt: Long,
    ) = PersonalizationRecordSnapshot(
        targetStore = store,
        id = id,
        statement = "Existing statement.",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

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
        resultingBehavior = "The complete requested behavior is applied.",
        mutations = mutations.toList(),
    )
}
