package org.muilab.notigpt.domain.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AssistantMutationContractsTest {
    @Test
    fun `store and operation vocabularies are closed and exact`() {
        assertEquals(
            listOf("GENERAL_PREFERENCE", "EXTRACTION_PREFERENCE", "USER_KNOWLEDGE"),
            PersonalizationStore.entries.map { it.name },
        )
        assertEquals(
            listOf("ADD", "UPDATE", "DELETE"),
            PersonalizationOperation.entries.map { it.name },
        )
    }

    @Test
    fun `turn vocabulary is closed and exact`() {
        assertEquals(
            listOf("QUESTION", "ALTERNATIVE_SET", "KNOWLEDGE_CANDIDATES", "MESSAGE"),
            PersonalizationTurnType.entries.map { it.name },
        )
    }

    @Test
    fun `add mutation carries only a proposal local id`() {
        val mutation = PersonalizationMutation(
            proposalId = "proposal-1",
            targetStore = PersonalizationStore.GENERAL_PREFERENCE,
            operation = PersonalizationOperation.ADD,
            statement = "Messages from my family deserve immediate attention.",
        )

        assertNull(mutation.expectedTarget)
    }

    @Test
    fun `alternative set requires a meaningful axis and one to three complete sets`() {
        val changeSet = PersonalizationChangeSet(
            proposalId = "set-1",
            resultingBehavior = "Messages from my family will receive more attention.",
            mutations = listOf(
                PersonalizationMutation(
                    proposalId = "mutation-1",
                    targetStore = PersonalizationStore.GENERAL_PREFERENCE,
                    operation = PersonalizationOperation.ADD,
                    statement = "Messages from my family deserve more attention.",
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AlternativeSetTurn(
                uiLanguage = "en",
                decisionQuestion = "How broadly should this apply?",
                variationAxis = " ",
                alternatives = listOf(changeSet),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlternativeSetTurn(
                uiLanguage = "en",
                decisionQuestion = "How broadly should this apply?",
                variationAxis = "scope",
                alternatives = emptyList(),
            )
        }
    }
}
