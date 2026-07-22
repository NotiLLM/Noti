package org.muilab.notigpt.domain.personalization

/** A request-local view of a confirmed record. It is not a persistence model. */
data class PersonalizationRecordSnapshot(
    val targetStore: PersonalizationStore,
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Temporary assistant-authored text awaiting explicit confirmation. */
data class ProposedStatementDraft(
    val proposalId: String,
    val targetStore: PersonalizationStore,
    val statement: String,
    val evidenceRefs: List<String> = emptyList(),
)

enum class PersonalizationStore {
    GENERAL_PREFERENCE,
    EXTRACTION_PREFERENCE,
    USER_KNOWLEDGE,
}

enum class PersonalizationOperation {
    ADD,
    UPDATE,
    DELETE,
}

/** Identity and version observed by the assistant for a mutation of an existing record. */
data class ExpectedTarget(
    val id: String,
    val updatedAt: Long,
)

/** One atomic proposed CRUD operation. Durable IDs are never assigned to ADD operations. */
data class PersonalizationMutation(
    val proposalId: String,
    val targetStore: PersonalizationStore,
    val operation: PersonalizationOperation,
    val statement: String? = null,
    val expectedTarget: ExpectedTarget? = null,
    val reason: String? = null,
    val evidenceRefs: List<String> = emptyList(),
) {
    init {
        require(proposalId.isNotBlank()) { "A mutation requires a proposal-local ID." }
        when (operation) {
            PersonalizationOperation.ADD -> {
                require(expectedTarget == null) { "ADD must not carry a durable target ID." }
                require(!statement.isNullOrBlank()) { "ADD requires a statement." }
            }

            PersonalizationOperation.UPDATE -> {
                require(expectedTarget != null) { "UPDATE requires stale-target metadata." }
                require(!statement.isNullOrBlank()) { "UPDATE requires a statement." }
            }

            PersonalizationOperation.DELETE -> {
                require(expectedTarget != null) { "DELETE requires stale-target metadata." }
                require(statement == null) { "DELETE must not carry replacement text." }
            }
        }
    }
}

/** A complete, all-or-nothing option shown to the user. */
data class PersonalizationChangeSet(
    val proposalId: String,
    val resultingBehavior: String,
    val mutations: List<PersonalizationMutation>,
    val recommended: Boolean = false,
    val recommendationReason: String? = null,
    val reason: String? = null,
    val consequence: String? = null,
) {
    init {
        require(proposalId.isNotBlank()) { "A change set requires a proposal-local ID." }
        require(resultingBehavior.isNotBlank()) { "A change set must describe its exact resulting behavior." }
        require(mutations.isNotEmpty()) { "A change set must contain at least one mutation." }
    }
}

enum class PersonalizationTurnType {
    QUESTION,
    ALTERNATIVE_SET,
    KNOWLEDGE_CANDIDATES,
    MESSAGE,
}

/** A validated assistant response that can safely cross the remote boundary. */
sealed interface PersonalizationAssistantTurn {
    val turnType: PersonalizationTurnType
    val uiLanguage: String
}

data class QuestionTurn(
    override val uiLanguage: String,
    val question: String,
    val rationale: String,
    val answerStarters: List<String> = emptyList(),
) : PersonalizationAssistantTurn {
    override val turnType = PersonalizationTurnType.QUESTION

    init {
        require(uiLanguage.isNotBlank()) { "QUESTION requires uiLanguage." }
        require(question.isNotBlank()) { "QUESTION requires question text." }
        require(rationale.isNotBlank()) { "QUESTION requires a rationale." }
    }
}

data class AlternativeSetTurn(
    override val uiLanguage: String,
    val decisionQuestion: String,
    val variationAxis: String,
    val alternatives: List<PersonalizationChangeSet>,
) : PersonalizationAssistantTurn {
    override val turnType = PersonalizationTurnType.ALTERNATIVE_SET

    init {
        require(uiLanguage.isNotBlank()) { "ALTERNATIVE_SET requires uiLanguage." }
        require(decisionQuestion.isNotBlank()) { "ALTERNATIVE_SET requires a decision question." }
        require(variationAxis.isNotBlank()) { "ALTERNATIVE_SET requires one variation axis." }
        require(alternatives.size in 1..3) { "ALTERNATIVE_SET requires one to three complete sets." }
        require(alternatives.count { it.recommended } <= 1) { "At most one alternative may be recommended." }
    }
}

data class KnowledgeCandidatesTurn(
    override val uiLanguage: String,
    val candidates: List<PersonalizationMutation>,
) : PersonalizationAssistantTurn {
    override val turnType = PersonalizationTurnType.KNOWLEDGE_CANDIDATES

    init {
        require(uiLanguage.isNotBlank()) { "KNOWLEDGE_CANDIDATES requires uiLanguage." }
        require(candidates.isNotEmpty()) { "KNOWLEDGE_CANDIDATES requires at least one candidate." }
        require(candidates.all { it.targetStore == PersonalizationStore.USER_KNOWLEDGE }) {
            "Knowledge candidates must target the knowledge store."
        }
    }
}

data class MessageTurn(
    override val uiLanguage: String,
    val message: String,
) : PersonalizationAssistantTurn {
    override val turnType = PersonalizationTurnType.MESSAGE

    init {
        require(uiLanguage.isNotBlank()) { "MESSAGE requires uiLanguage." }
        require(message.isNotBlank()) { "MESSAGE requires text." }
    }
}
