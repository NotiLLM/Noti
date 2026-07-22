package org.muilab.notigpt.data.remote.n8n

import java.text.BreakIterator
import java.util.Locale
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationAssistantResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChangeSetDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationMutationDto
import org.muilab.notigpt.domain.personalization.AlternativeSetTurn
import org.muilab.notigpt.domain.personalization.ExpectedTarget
import org.muilab.notigpt.domain.personalization.KnowledgeCandidatesTurn
import org.muilab.notigpt.domain.personalization.MessageTurn
import org.muilab.notigpt.domain.personalization.PersonalizationAssistantTurn
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutation
import org.muilab.notigpt.domain.personalization.PersonalizationOperation
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import org.muilab.notigpt.domain.personalization.PersonalizationTurnType
import org.muilab.notigpt.domain.personalization.QuestionTurn

sealed interface PersonalizationValidationResult {
    data class Valid(val turn: PersonalizationAssistantTurn) : PersonalizationValidationResult

    data class Invalid(val failure: PersonalizationValidationFailure) : PersonalizationValidationResult
}

data class PersonalizationValidationFailure(
    val code: Code,
    val detail: String,
) {
    enum class Code {
        UNKNOWN_TURN,
        UNKNOWN_STORE,
        UNKNOWN_OPERATION,
        INVALID_SHAPE,
        INVALID_STATEMENT,
        DUPLICATE_PROPOSAL_ID,
        DUPLICATE_TARGET_ID,
        TARGET_REQUIRED,
        TARGET_NOT_FOUND,
        STALE_TARGET_REQUIRED,
        STALE_TARGET_MISMATCH,
        TOO_MANY_ALTERNATIVES,
        EMPTY_CHANGE_SET,
        DUPLICATE_ALTERNATIVE,
        INVALID_EVIDENCE_REF,
    }
}

/** Strict, Android-owned mapping from permissive Gson values into closed assistant turns. */
object PersonalizationResponseValidator {
    fun validate(
        response: PersonalizationAssistantResponseDto,
        targetSnapshots: List<PersonalizationRecordSnapshot>,
        evidenceIds: Set<String>? = null,
    ): PersonalizationValidationResult {
        val targetsById = targetSnapshots.associateBy { it.id }
        if (targetsById.size != targetSnapshots.size) {
            return invalid(
                PersonalizationValidationFailure.Code.DUPLICATE_TARGET_ID,
                "The request contains duplicate target IDs.",
            )
        }

        val uiLanguage = normalize(response.uiLanguage)
        if (uiLanguage.isEmpty()) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "uiLanguage is required.")
        }
        val turnType = PersonalizationTurnType.entries.singleOrNull { it.name == response.turnType }
            ?: return invalid(
                PersonalizationValidationFailure.Code.UNKNOWN_TURN,
                "Unknown turn discriminator.",
            )

        val context = ValidationContext(
            targetsById = targetsById,
            evidenceIds = evidenceIds,
        )
        return when (turnType) {
            PersonalizationTurnType.QUESTION -> validateQuestion(response, uiLanguage)
            PersonalizationTurnType.ALTERNATIVE_SET -> validateAlternatives(response, uiLanguage, context)
            PersonalizationTurnType.KNOWLEDGE_CANDIDATES -> validateKnowledge(response, uiLanguage, context)
            PersonalizationTurnType.MESSAGE -> validateMessage(response, uiLanguage)
        }
    }

    private fun validateQuestion(
        response: PersonalizationAssistantResponseDto,
        uiLanguage: String,
    ): PersonalizationValidationResult {
        if (response.decisionQuestion != null || response.variationAxis != null || response.alternatives != null ||
            response.knowledgeCandidates != null || response.message != null
        ) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "QUESTION contains unrelated fields.")
        }
        val question = normalize(response.question)
        val rationale = normalize(response.rationale)
        if (question.isEmpty() || rationale.isEmpty()) {
            return invalid(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "QUESTION requires question and rationale text.",
            )
        }
        val starters = response.answerStarters.orEmpty().map(::normalize)
        if (starters.any { it.isEmpty() } || starters.distinct().size != starters.size) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "Answer starters are malformed.")
        }
        return PersonalizationValidationResult.Valid(
            QuestionTurn(
                uiLanguage = uiLanguage,
                question = question,
                rationale = rationale,
                answerStarters = starters,
            ),
        )
    }

    private fun validateAlternatives(
        response: PersonalizationAssistantResponseDto,
        uiLanguage: String,
        context: ValidationContext,
    ): PersonalizationValidationResult {
        if (response.question != null || response.rationale != null || response.answerStarters != null ||
            response.knowledgeCandidates != null || response.message != null
        ) {
            return invalid(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "ALTERNATIVE_SET contains unrelated fields.",
            )
        }
        val decisionQuestion = normalize(response.decisionQuestion)
        val variationAxis = normalize(response.variationAxis)
        if (decisionQuestion.isEmpty() || variationAxis.isEmpty()) {
            return invalid(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "ALTERNATIVE_SET requires a decision question and variation axis.",
            )
        }
        val alternatives = response.alternatives
            ?: return invalid(PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET, "Alternatives are required.")
        if (alternatives.isEmpty()) {
            return invalid(PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET, "At least one alternative is required.")
        }
        if (alternatives.size > 3) {
            return invalid(
                PersonalizationValidationFailure.Code.TOO_MANY_ALTERNATIVES,
                "At most three alternatives are allowed.",
            )
        }

        val mapped = mutableListOf<PersonalizationChangeSet>()
        for (alternative in alternatives) {
            when (val result = mapChangeSet(alternative, context)) {
                is MappingResult.Failure -> return PersonalizationValidationResult.Invalid(result.failure)
                is MappingResult.Success -> mapped += result.value
            }
        }
        val signatures = mapped.map(::changeSetSignature)
        if (signatures.distinct().size != signatures.size) {
            return invalid(
                PersonalizationValidationFailure.Code.DUPLICATE_ALTERNATIVE,
                "Alternatives must differ in their normalized mutations.",
            )
        }
        if (mapped.count { it.recommended } > 1) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "Only one alternative may be recommended.")
        }
        return PersonalizationValidationResult.Valid(
            AlternativeSetTurn(
                uiLanguage = uiLanguage,
                decisionQuestion = decisionQuestion,
                variationAxis = variationAxis,
                alternatives = mapped,
            ),
        )
    }

    private fun validateKnowledge(
        response: PersonalizationAssistantResponseDto,
        uiLanguage: String,
        context: ValidationContext,
    ): PersonalizationValidationResult {
        if (response.question != null || response.rationale != null || response.answerStarters != null ||
            response.decisionQuestion != null || response.variationAxis != null || response.alternatives != null ||
            response.message != null
        ) {
            return invalid(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "KNOWLEDGE_CANDIDATES contains unrelated fields.",
            )
        }
        val candidates = response.knowledgeCandidates
        if (candidates.isNullOrEmpty()) {
            return invalid(
                PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET,
                "At least one knowledge candidate is required.",
            )
        }
        val mapped = mutableListOf<PersonalizationMutation>()
        val targetIds = mutableSetOf<String>()
        for (candidate in candidates) {
            when (val result = mapMutation(candidate, context, evidenceRequired = true)) {
                is MappingResult.Failure -> return PersonalizationValidationResult.Invalid(result.failure)
                is MappingResult.Success -> {
                    if (result.value.targetStore != PersonalizationStore.USER_KNOWLEDGE) {
                        return invalid(
                            PersonalizationValidationFailure.Code.INVALID_SHAPE,
                            "Knowledge candidates must target the knowledge store.",
                        )
                    }
                    val targetId = result.value.expectedTarget?.id
                    if (targetId != null && !targetIds.add(targetId)) {
                        return invalid(
                            PersonalizationValidationFailure.Code.DUPLICATE_TARGET_ID,
                            "Knowledge candidates contain a duplicate target ID.",
                        )
                    }
                    if (normalize(result.value.reason).isEmpty()) {
                        return invalid(
                            PersonalizationValidationFailure.Code.INVALID_SHAPE,
                            "Knowledge candidates require an evidence explanation.",
                        )
                    }
                    mapped += result.value
                }
            }
        }
        return PersonalizationValidationResult.Valid(
            KnowledgeCandidatesTurn(
                uiLanguage = uiLanguage,
                candidates = mapped,
            ),
        )
    }

    private fun validateMessage(
        response: PersonalizationAssistantResponseDto,
        uiLanguage: String,
    ): PersonalizationValidationResult {
        if (response.question != null || response.rationale != null || response.answerStarters != null ||
            response.decisionQuestion != null || response.variationAxis != null || response.alternatives != null ||
            response.knowledgeCandidates != null
        ) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "MESSAGE contains unrelated fields.")
        }
        val message = normalize(response.message)
        if (message.isEmpty()) {
            return invalid(PersonalizationValidationFailure.Code.INVALID_SHAPE, "MESSAGE requires text.")
        }
        return PersonalizationValidationResult.Valid(MessageTurn(uiLanguage = uiLanguage, message = message))
    }

    private fun mapChangeSet(
        dto: PersonalizationChangeSetDto,
        context: ValidationContext,
    ): MappingResult<PersonalizationChangeSet> {
        val proposalId = validProposalId(dto.proposalId, context)
            ?: return MappingResult.Failure(context.lastFailure)
        val resultingBehavior = normalize(dto.resultingBehavior)
        if (resultingBehavior.isEmpty()) {
            return context.failure(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "A change set requires exact resulting-behavior copy.",
            )
        }
        val mutations = dto.mutations
        if (mutations.isNullOrEmpty()) {
            return context.failure(
                PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET,
                "A complete change set requires at least one mutation.",
            )
        }
        val mapped = mutableListOf<PersonalizationMutation>()
        val targetIds = mutableSetOf<String>()
        for (mutation in mutations) {
            when (val result = mapMutation(mutation, context, evidenceRequired = false)) {
                is MappingResult.Failure -> return result
                is MappingResult.Success -> {
                    val targetId = result.value.expectedTarget?.id
                    if (targetId != null && !targetIds.add(targetId)) {
                        return context.failure(
                            PersonalizationValidationFailure.Code.DUPLICATE_TARGET_ID,
                            "A change set contains a duplicate target ID.",
                        )
                    }
                    mapped += result.value
                }
            }
        }
        return MappingResult.Success(
            PersonalizationChangeSet(
                proposalId = proposalId,
                resultingBehavior = resultingBehavior,
                mutations = mapped,
                recommended = dto.recommended == true,
                recommendationReason = normalizeOptional(dto.recommendationReason),
                reason = normalizeOptional(dto.reason),
                consequence = normalizeOptional(dto.consequence),
            ),
        )
    }

    private fun mapMutation(
        dto: PersonalizationMutationDto,
        context: ValidationContext,
        evidenceRequired: Boolean,
    ): MappingResult<PersonalizationMutation> {
        val proposalId = validProposalId(dto.proposalId, context)
            ?: return MappingResult.Failure(context.lastFailure)
        val store = PersonalizationStore.entries.singleOrNull { it.name == dto.targetStore }
            ?: return context.failure(
                PersonalizationValidationFailure.Code.UNKNOWN_STORE,
                "Unknown store discriminator.",
            )
        val operation = PersonalizationOperation.entries.singleOrNull { it.name == dto.operation }
            ?: return context.failure(
                PersonalizationValidationFailure.Code.UNKNOWN_OPERATION,
                "Unknown operation discriminator.",
            )
        val evidenceRefs = dto.evidenceRefs.orEmpty()
        if (evidenceRefs.any { !isValidIdentifier(it) } || evidenceRefs.distinct().size != evidenceRefs.size ||
            evidenceRequired && evidenceRefs.isEmpty() ||
            evidenceRefs.isNotEmpty() && (context.evidenceIds == null || !context.evidenceIds.containsAll(evidenceRefs))
        ) {
            return context.failure(
                PersonalizationValidationFailure.Code.INVALID_EVIDENCE_REF,
                "Evidence references must be unique members of the request evidence set.",
            )
        }

        val statement = when (operation) {
            PersonalizationOperation.ADD, PersonalizationOperation.UPDATE -> {
                normalizeAtomicStatement(dto.statement)
                    ?: return context.failure(
                        PersonalizationValidationFailure.Code.INVALID_STATEMENT,
                        "ADD and UPDATE require one atomic statement.",
                    )
            }

            PersonalizationOperation.DELETE -> {
                if (dto.statement != null) {
                    return context.failure(
                        PersonalizationValidationFailure.Code.INVALID_STATEMENT,
                        "DELETE must not carry replacement text.",
                    )
                }
                null
            }
        }

        val expectedTarget = when (operation) {
            PersonalizationOperation.ADD -> {
                if (dto.expectedTarget != null) {
                    return context.failure(
                        PersonalizationValidationFailure.Code.TARGET_REQUIRED,
                        "ADD must not carry a durable target.",
                    )
                }
                null
            }

            PersonalizationOperation.UPDATE, PersonalizationOperation.DELETE -> {
                val expected = dto.expectedTarget
                    ?: return context.failure(
                        PersonalizationValidationFailure.Code.TARGET_REQUIRED,
                        "$operation requires a target.",
                    )
                val id = expected.id
                if (!isValidIdentifier(id)) {
                    return context.failure(
                        PersonalizationValidationFailure.Code.TARGET_REQUIRED,
                        "$operation requires a valid target ID.",
                    )
                }
                val updatedAt = expected.updatedAt
                    ?: return context.failure(
                        PersonalizationValidationFailure.Code.STALE_TARGET_REQUIRED,
                        "$operation requires the expected updatedAt value.",
                    )
                val snapshot = context.targetsById[id]
                    ?: return context.failure(
                        PersonalizationValidationFailure.Code.TARGET_NOT_FOUND,
                        "The target is absent from the request snapshot.",
                    )
                if (snapshot.targetStore != store) {
                    return context.failure(
                        PersonalizationValidationFailure.Code.TARGET_NOT_FOUND,
                        "The target does not belong to the proposed store.",
                    )
                }
                if (snapshot.updatedAt != updatedAt) {
                    return context.failure(
                        PersonalizationValidationFailure.Code.STALE_TARGET_MISMATCH,
                        "The expected target timestamp does not match the request snapshot.",
                    )
                }
                ExpectedTarget(id = id!!, updatedAt = updatedAt)
            }
        }

        return MappingResult.Success(
            PersonalizationMutation(
                proposalId = proposalId,
                targetStore = store,
                operation = operation,
                statement = statement,
                expectedTarget = expectedTarget,
                reason = normalizeOptional(dto.reason),
                evidenceRefs = evidenceRefs,
            ),
        )
    }

    private fun validProposalId(raw: String?, context: ValidationContext): String? {
        if (!isValidIdentifier(raw)) {
            context.setFailure(
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
                "A proposal-local ID is required.",
            )
            return null
        }
        if (!context.proposalIds.add(raw!!)) {
            context.setFailure(
                PersonalizationValidationFailure.Code.DUPLICATE_PROPOSAL_ID,
                "Proposal-local IDs must be unique within a response.",
            )
            return null
        }
        return raw
    }

    private fun normalizeAtomicStatement(raw: String?): String? {
        val normalized = normalize(raw)
        if (normalized.isEmpty() || normalized.none { it.isLetterOrDigit() }) return null
        val sentenceIterator = BreakIterator.getSentenceInstance(Locale.ROOT)
        sentenceIterator.setText(normalized)
        var start = sentenceIterator.first()
        var count = 0
        while (true) {
            val end = sentenceIterator.next()
            if (end == BreakIterator.DONE) break
            if (normalized.substring(start, end).any { it.isLetterOrDigit() }) count++
            start = end
        }
        return normalized.takeIf { count == 1 }
    }

    private fun normalize(raw: String?): String = raw.orEmpty().trim().replace(Regex("\\s+"), " ")

    private fun normalizeOptional(raw: String?): String? = normalize(raw).takeIf { it.isNotEmpty() }

    private fun isValidIdentifier(raw: String?): Boolean =
        raw != null && raw.isNotBlank() && raw == raw.trim() && raw.none(Char::isWhitespace)

    private fun changeSetSignature(set: PersonalizationChangeSet): String = set.mutations
        .map { mutation ->
            listOf(
                mutation.targetStore.name,
                mutation.operation.name,
                mutation.statement.orEmpty(),
                mutation.expectedTarget?.id.orEmpty(),
                mutation.expectedTarget?.updatedAt?.toString().orEmpty(),
            ).joinToString("|")
        }
        .sorted()
        .joinToString(";")

    private fun invalid(
        code: PersonalizationValidationFailure.Code,
        detail: String,
    ) = PersonalizationValidationResult.Invalid(PersonalizationValidationFailure(code, detail))

    private data class ValidationContext(
        val targetsById: Map<String, PersonalizationRecordSnapshot>,
        val evidenceIds: Set<String>?,
        val proposalIds: MutableSet<String> = mutableSetOf(),
        var lastFailure: PersonalizationValidationFailure = PersonalizationValidationFailure(
            PersonalizationValidationFailure.Code.INVALID_SHAPE,
            "Invalid response.",
        ),
    ) {
        fun setFailure(code: PersonalizationValidationFailure.Code, detail: String) {
            lastFailure = PersonalizationValidationFailure(code, detail)
        }

        fun <T> failure(
            code: PersonalizationValidationFailure.Code,
            detail: String,
        ): MappingResult<T> {
            setFailure(code, detail)
            return MappingResult.Failure(lastFailure)
        }
    }

    private sealed interface MappingResult<out T> {
        data class Success<T>(val value: T) : MappingResult<T>

        data class Failure(val failure: PersonalizationValidationFailure) : MappingResult<Nothing>
    }
}
