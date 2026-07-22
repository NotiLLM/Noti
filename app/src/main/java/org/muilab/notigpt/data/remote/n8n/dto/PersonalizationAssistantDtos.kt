package org.muilab.notigpt.data.remote.n8n.dto

/** The three confirmed stores supplied to every personalization assistant flow. */
data class PersonalizationConfirmedStateDto(
    val generalPreferences: List<PersonalizationRecordSnapshotDto>,
    val extractionPreferences: List<PersonalizationRecordSnapshotDto>,
    val userKnowledge: List<PersonalizationRecordSnapshotDto>,
)

data class PersonalizationChatMessageDto(
    val role: String,
    val content: String,
)

/** Request for the shared, mode-free personalization chat. */
data class PersonalizationChatRequestDto(
    val uiLanguage: String,
    val confirmedState: PersonalizationConfirmedStateDto,
    val userText: String,
    val conversation: List<PersonalizationChatMessageDto> = emptyList(),
)

/** Explicit user-triggered action context for Quick Sync. */
data class PersonalizationQuickSyncTriggerDto(
    val entryPoint: String,
    val action: String,
    val subjectId: String? = null,
    val context: Map<String, String> = emptyMap(),
    val userReason: String,
)

data class PersonalizationQuickSyncRequestDto(
    val uiLanguage: String,
    val confirmedState: PersonalizationConfirmedStateDto,
    val triggerContext: PersonalizationQuickSyncTriggerDto,
)

/** Request-local, untrusted evidence available only to explicit discovery. */
data class PersonalizationDiscoveryEvidenceDto(
    val id: String,
    val source: String,
    val content: String,
)

data class PersonalizationDiscoveryRequestDto(
    val uiLanguage: String,
    val confirmedState: PersonalizationConfirmedStateDto,
    val evidence: List<PersonalizationDiscoveryEvidenceDto>,
) {
    val evidenceIds: Set<String>
        get() = evidence.mapTo(linkedSetOf()) { it.id }
}

fun PersonalizationConfirmedStateDto.allSnapshots(): List<PersonalizationRecordSnapshotDto> =
    generalPreferences + extractionPreferences + userKnowledge

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
