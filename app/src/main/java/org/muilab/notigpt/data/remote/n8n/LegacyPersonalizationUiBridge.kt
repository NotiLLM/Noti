package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nContextDiscoverRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nQuickSyncRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nQuickSyncResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatMessageDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationConfirmedStateDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryEvidenceDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncTriggerDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationRecordSnapshotDto
import org.muilab.notigpt.domain.personalization.AlternativeSetTurn
import org.muilab.notigpt.domain.personalization.KnowledgeCandidatesTurn
import org.muilab.notigpt.domain.personalization.MessageTurn
import org.muilab.notigpt.domain.personalization.PersonalizationAssistantTurn
import org.muilab.notigpt.domain.personalization.QuestionTurn

/**
 * Temporary compile bridge for the pre-04-05 screen.
 *
 * It submits only the new flow-scoped requests and discards mutations instead of translating them into the old
 * durable auto-apply path. Every displayed response has already passed strict typed validation.
 */
internal object LegacyPersonalizationUiBridge {
    suspend fun chat(request: N8nChatInteractRequestDto): N8nChatInteractResponseDto? {
        val confirmed = confirmedState(request.currentPreferences, request.userContexts.orEmpty())
        val typed = PersonalizationChatRequestDto(
            uiLanguage = request.language,
            confirmedState = confirmed,
            userText = request.chatHistory.lastOrNull { it.role == "user" }?.content.orEmpty(),
            conversation = request.chatHistory.map { PersonalizationChatMessageDto(it.role, it.content) },
        )
        return when (val result = PreferenceChatClient.interact(typed)) {
            is PersonalizationClientResult.Success -> N8nChatInteractResponseDto(
                assistantMessage = result.turn.displayText(),
                proposedActions = emptyList(),
            )
            is PersonalizationClientResult.Failure -> null
        }
    }

    suspend fun quickSync(request: N8nQuickSyncRequestDto): N8nQuickSyncResponseDto? {
        val typed = PersonalizationQuickSyncRequestDto(
            uiLanguage = request.language,
            confirmedState = confirmedState(request.currentPreferences, request.userContexts.orEmpty()),
            triggerContext = PersonalizationQuickSyncTriggerDto(
                entryPoint = request.entryPoint,
                action = request.entryPoint,
                context = request.contextData.mapValues { it.value?.toString().orEmpty() },
                userReason = listOfNotNull(
                    request.userSelections.reason,
                    request.userSelections.subReason,
                ).joinToString(" ").ifBlank { request.userSelections.scope.orEmpty() },
            ),
        )
        return when (val result = PreferenceQuickSyncClient.sync(typed)) {
            is PersonalizationClientResult.Success -> N8nQuickSyncResponseDto(
                status = "proposal_ready",
                createdRules = emptyList(),
                updatedRules = emptyList(),
                deletedRuleIds = emptyList(),
                toastMessage = result.turn.displayText(),
            )
            is PersonalizationClientResult.Failure -> null
        }
    }

    suspend fun discovery(request: N8nContextDiscoverRequestDto): N8nChatInteractResponseDto? {
        val evidence = buildList {
            request.notificationSummary.forEachIndexed { index, item ->
                add(
                    PersonalizationDiscoveryEvidenceDto(
                        id = "notification-$index",
                        source = "ACTIVE_NOTIFICATION",
                        content = item.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    ),
                )
            }
            request.currentSavedItems.forEachIndexed { index, item ->
                add(
                    PersonalizationDiscoveryEvidenceDto(
                        id = "item-$index",
                        source = "ACTIVE_ITEM",
                        content = item.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    ),
                )
            }
        }
        val typed = PersonalizationDiscoveryRequestDto(
            uiLanguage = request.language,
            confirmedState = confirmedState(emptyList(), request.existingUserContexts),
            evidence = evidence,
        )
        return when (val result = PreferenceContextDiscoverClient.discover(typed)) {
            is PersonalizationClientResult.Success -> N8nChatInteractResponseDto(
                assistantMessage = result.turn.displayText(),
                proposedActions = emptyList(),
            )
            is PersonalizationClientResult.Failure -> null
        }
    }

    private fun confirmedState(
        preferences: List<Map<String, String>>,
        knowledge: List<Map<String, String>>,
    ) = PersonalizationConfirmedStateDto(
        generalPreferences = emptyList(),
        extractionPreferences = preferences.mapNotNull { it.toSnapshot("EXTRACTION_PREFERENCE") },
        userKnowledge = knowledge.mapNotNull { it.toSnapshot("USER_KNOWLEDGE") },
    )

    private fun Map<String, String>.toSnapshot(store: String): PersonalizationRecordSnapshotDto? {
        val id = get("id")?.takeIf(String::isNotBlank) ?: return null
        val statement = get("statement")?.takeIf(String::isNotBlank) ?: return null
        return PersonalizationRecordSnapshotDto(
            targetStore = store,
            id = id,
            statement = statement,
            createdAt = 0,
            updatedAt = 0,
        )
    }

    private fun PersonalizationAssistantTurn.displayText(): String = when (this) {
        is QuestionTurn -> "$question\n\n$rationale"
        is AlternativeSetTurn -> buildString {
            append(decisionQuestion)
            alternatives.forEachIndexed { index, option -> append("\n${index + 1}. ${option.resultingBehavior}") }
        }
        is KnowledgeCandidatesTurn -> candidates.joinToString("\n") { candidate ->
            listOfNotNull(candidate.statement, candidate.reason).joinToString(" — ")
        }
        is MessageTurn -> message
    }
}
