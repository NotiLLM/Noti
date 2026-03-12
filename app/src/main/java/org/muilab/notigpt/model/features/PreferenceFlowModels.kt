package org.muilab.notigpt.model.features

/**
 * Shared domain models used across the HITL preference learning flows (Flows 1-4).
 */

// ── Entry point enum ────────────────────────────────────────────────

enum class PreferenceEntryPoint(val wire: String) {
    EDIT("EDIT"),
    DELETE("DELETE"),
    MANUAL_EXTRACT("MANUAL_EXTRACT"),
}

// ── Button selections gathered during progressive disclosure ────────

data class UserSelections(
    val scope: String? = null,
    val reason: String? = null,
    val subReason: String? = null,
)

// ── Chat models ─────────────────────────────────────────────────────

/**
 * Context carried into the Chat screen when redirected from a Delete / Edit /
 * Manual-Extract flow.  Shown as a context card at the top of the chat and
 * sent to n8n so the LLM knows what triggered the conversation.
 */
data class ChatFlowContext(
    val entryPoint: PreferenceEntryPoint,
    val reminderTitle: String? = null,
    val reminderContent: String? = null,
    val reminderBeforeTitle: String? = null,
    val reminderBeforeContent: String? = null,
    val notiKey: String? = null,
)

data class ChatMessage(
    val role: String,       // "user" | "assistant" | "system"
    val content: String,
)

data class ProposedAction(
    val actionId: String,
    val type: ProposedActionType,
    val targetPreferenceId: String? = null,
    val newStatement: String? = null,
    val newPreferenceType: String? = null,
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
)

enum class ProposedActionType { ADD, MODIFY, DELETE }

// ── Network DTOs ────────────────────────────────────────────────────

data class QuickSyncRequest(
    val userId: String,
    val language: String,
    val entryPoint: String,
    val contextData: Map<String, Any?>,
    val userSelections: UserSelections,
    val currentPreferences: List<Map<String, String>>,
)

data class QuickSyncResponse(
    val status: String,
    val updatedPreferences: List<PreferencePlain>,
    val toastMessage: String?,
    val conflicts: List<ConflictDto> = emptyList(),
)

data class PreferencePlain(
    val id: String,
    val statement: String,
    val type: String,
)

data class ChatInteractRequest(
    val userId: String,
    val language: String,
    val chatHistory: List<ChatMessage>,
    val contextData: Map<String, Any?>?,
    val currentPreferences: List<Map<String, String>>,
)

data class ChatInteractResponse(
    val assistantMessage: String,
    val proposedActions: List<ProposedActionDto>,
    val conflicts: List<ConflictDto> = emptyList(),
)

data class ProposedActionDto(
    val actionId: String,
    val type: String,
    val targetPreferenceId: String?,
    val newStatement: String?,
    val newPreferenceType: String?,
)

// ── Conflict DTO (returned by both Quick-Sync and Chat-Interact) ────

data class ConflictDto(
    val conflictId: String,
    val description: String,
    val involvedPreferenceIds: List<String>,
)



