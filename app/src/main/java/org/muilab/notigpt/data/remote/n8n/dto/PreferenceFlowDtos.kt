package org.muilab.notigpt.data.remote.n8n.dto

import org.muilab.notigpt.ui.preference.model.ChatMessage

/**
 * n8n wire models for HITL preference learning flows.
 *
 * Keep these DTOs in the remote n8n layer. If the UI needs state, map these into ui/preference/model instead of
 * exposing backend payload details directly to composables.
 */
data class N8nUserSelectionsDto(
    val scope: String? = null,
    val reason: String? = null,
    val subReason: String? = null,
)

data class N8nQuickSyncRequestDto(
    val userId: String,
    val language: String,
    val entryPoint: String,
    val contextData: Map<String, Any?>,
    val userSelections: N8nUserSelectionsDto,
    val currentPreferences: List<Map<String, String>>,
    val userContexts: List<Map<String, String>>? = null,
)

/**
 * Explicit-diff quick-sync response (contract v2): the backend proposes creations, updates, and
 * deletions separately. Omission never deletes — only ids in [deletedRuleIds] are deletion proposals,
 * and those are confirmed by the user before anything is removed locally.
 */
data class N8nQuickSyncResponseDto(
    val status: String,
    val createdRules: List<N8nPreferencePlainDto> = emptyList(),
    val updatedRules: List<N8nPreferencePlainDto> = emptyList(),
    val deletedRuleIds: List<String> = emptyList(),
    val toastMessage: String?,
    val conflicts: List<N8nConflictDto> = emptyList(),
)

data class N8nPreferencePlainDto(
    val id: String,
    val statement: String,
    val type: String,
)

data class N8nChatInteractRequestDto(
    val userId: String,
    val language: String,
    val chatHistory: List<ChatMessage>,
    val contextData: Map<String, Any?>?,
    val currentPreferences: List<Map<String, String>>,
    val userContexts: List<Map<String, String>>? = null,
    /** "RULES" or "ABOUT_ME" — tells n8n which system prompt to use. */
    val chatMode: String = "RULES",
)

/**
 * Request to the context-discover n8n endpoint.
 * Sends a curated summary of notifications and SavedItems so the LLM can infer factual statements about the user.
 */
data class N8nContextDiscoverRequestDto(
    val userId: String,
    val language: String,
    val notificationSummary: List<Map<String, String>>,
    val currentSavedItems: List<Map<String, String>>,
    val existingUserContexts: List<Map<String, String>>,
)

data class N8nChatInteractResponseDto(
    val assistantMessage: String,
    val proposedActions: List<N8nProposedActionDto>,
    val conflicts: List<N8nConflictDto> = emptyList(),
)

data class N8nProposedActionDto(
    val actionId: String,
    val type: String,
    val targetPreferenceId: String?,
    val newStatement: String?,
    val newPreferenceType: String?,
    /** "RULE" or "CONTEXT" — indicates whether this action targets a preference or a user context. */
    val targetType: String? = null,
)

data class N8nConflictDto(
    val conflictId: String,
    val description: String,
    val involvedPreferenceIds: List<String>,
)
