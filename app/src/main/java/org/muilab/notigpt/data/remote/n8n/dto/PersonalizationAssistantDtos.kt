package org.muilab.notigpt.data.remote.n8n.dto

/** Request for the shared personalization assistant. */
data class PersonalizationAssistantRequestDto(
    val uiLanguage: String,
    val userText: String? = null,
    val targetSnapshots: List<PersonalizationRecordSnapshotDto> = emptyList(),
    val evidenceIds: List<String> = emptyList(),
    /** Optional input scaffolding only. Hints never represent mutations. */
    val requestHints: List<String> = emptyList(),
)

data class PersonalizationRecordSnapshotDto(
    val targetStore: String,
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Raw Gson response shape. Discriminators remain strings until strict boundary validation succeeds.
 * Fields not belonging to the selected [turnType] make the response invalid.
 */
data class PersonalizationAssistantResponseDto(
    val turnType: String?,
    val uiLanguage: String?,
    val question: String? = null,
    val rationale: String? = null,
    val answerStarters: List<String>? = null,
    val decisionQuestion: String? = null,
    val variationAxis: String? = null,
    val alternatives: List<PersonalizationChangeSetDto>? = null,
    val knowledgeCandidates: List<PersonalizationMutationDto>? = null,
    val message: String? = null,
)

data class PersonalizationChangeSetDto(
    val proposalId: String?,
    /** User-facing copy of the exact state/behavior that selecting this complete set produces. */
    val resultingBehavior: String?,
    val mutations: List<PersonalizationMutationDto>?,
    val recommended: Boolean? = null,
    val recommendationReason: String? = null,
    val reason: String? = null,
    val consequence: String? = null,
)

data class PersonalizationMutationDto(
    val proposalId: String?,
    val targetStore: String?,
    val operation: String?,
    val statement: String? = null,
    val expectedTarget: ExpectedTargetDto? = null,
    val reason: String? = null,
    val evidenceRefs: List<String>? = null,
)

data class ExpectedTargetDto(
    val id: String?,
    val updatedAt: Long?,
)
