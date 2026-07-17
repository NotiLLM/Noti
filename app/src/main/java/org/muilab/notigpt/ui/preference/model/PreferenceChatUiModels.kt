package org.muilab.notigpt.ui.preference.model

/**
 * App-side models for the preference learning UI.
 *
 * These models represent screen state and user-facing actions. They are intentionally separate from n8n wire DTOs
 * so UI code does not depend on backend payload shapes.
 */
enum class PreferenceEntryPoint(val wire: String) {
    EDIT("EDIT"),
    DELETE("DELETE"),
    MANUAL_EXTRACT("MANUAL_EXTRACT"),
}

/**
 * Context carried into the preference chat screen when redirected from a Delete, Edit, or Manual-Extract flow.
 */
data class ChatFlowContext(
    val entryPoint: PreferenceEntryPoint,
    val title: String? = null,
    val content: String? = null,
    val savedItemBeforeTitle: String? = null,
    val savedItemBeforeContent: String? = null,
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
    /** "RULE" or "CONTEXT" — indicates whether this action targets a preference or a user context. */
    val targetType: String? = null,
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
)

enum class ProposedActionType { ADD, MODIFY, DELETE }
