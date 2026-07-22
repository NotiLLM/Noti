package org.muilab.notigpt.ui.preference.model

import org.muilab.notigpt.data.repository.personalization.PersonalizationApplyResult
import org.muilab.notigpt.domain.personalization.PersonalizationAssistantTurn
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightFailure
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import org.muilab.notigpt.domain.personalization.QuestionTurn

const val STALE_SUGGESTION_MESSAGE =
    "This suggestion is out of date. Refresh it to use your latest preferences."
const val REFRESH_SUGGESTION_LABEL = "Refresh suggestion"
const val NETWORK_ASSISTANT_MESSAGE =
    "Noti couldn't reach the assistant. Check your connection and try again."
const val ACTIVATION_PENDING_MESSAGE = "Personalization changes are temporarily unavailable."

sealed interface PersonalizationTranscriptItem {
    data class UserMessage(val text: String) : PersonalizationTranscriptItem

    data class AssistantTurn(val turn: PersonalizationAssistantTurn) : PersonalizationTranscriptItem

    data class Receipt(
        val destinationNames: List<String>,
        val message: String,
    ) : PersonalizationTranscriptItem
}

data class PendingPersonalizationSuggestion(
    val changeSet: PersonalizationChangeSet,
    val evidenceRefs: List<String> = changeSet.mutations.flatMap { it.evidenceRefs }.distinct(),
)

data class QuickSyncFeedbackState(
    val entryPoint: PreferenceEntryPoint,
    val action: String,
    val subjectId: String?,
    val context: Map<String, String>,
    val reasonStarters: List<String>,
    val draftText: String = "",
)

data class PersonalizationUiState(
    val generalPreferences: List<PersonalizationRecordSnapshot> = emptyList(),
    val extractionPreferences: List<PersonalizationRecordSnapshot> = emptyList(),
    val userKnowledge: List<PersonalizationRecordSnapshot> = emptyList(),
    val transcript: List<PersonalizationTranscriptItem> = emptyList(),
    val pendingSuggestions: List<PendingPersonalizationSuggestion> = emptyList(),
    val quickSyncFeedback: QuickSyncFeedbackState? = null,
    val draftText: String = "",
    val unansweredQuestion: QuestionTurn? = null,
    val temporaryEvidence: Map<String, List<String>> = emptyMap(),
    val isReadOnly: Boolean = false,
    val isAssistantLoading: Boolean = false,
    val applyingSuggestionId: String? = null,
    val staleSuggestionId: String? = null,
    val errorMessage: String? = null,
    val errorActionLabel: String? = null,
) {
    val hasTemporaryConversation: Boolean
        get() = transcript.isNotEmpty() ||
            pendingSuggestions.isNotEmpty() ||
            draftText.isNotBlank() ||
            unansweredQuestion != null ||
            temporaryEvidence.isNotEmpty() ||
            quickSyncFeedback != null

    val pendingSuggestionCount: Int
        get() = pendingSuggestions.size

    companion object {
        fun fromConfirmed(snapshots: List<PersonalizationRecordSnapshot>): PersonalizationUiState =
            PersonalizationUiState().withConfirmed(snapshots)
    }

    fun withConfirmed(snapshots: List<PersonalizationRecordSnapshot>): PersonalizationUiState = copy(
        generalPreferences = snapshots.filterStore(PersonalizationStore.GENERAL_PREFERENCE),
        extractionPreferences = snapshots.filterStore(PersonalizationStore.EXTRACTION_PREFERENCE),
        userKnowledge = snapshots.filterStore(PersonalizationStore.USER_KNOWLEDGE),
    )
}

data class PersonalizationConfirmationTransition(
    val state: PersonalizationUiState,
    val changeSet: PersonalizationChangeSet?,
)

object PersonalizationUiStateReducer {
    fun clearConversation(state: PersonalizationUiState): PersonalizationUiState {
        if (state.isReadOnly) return state
        return state.copy(
            transcript = emptyList(),
            pendingSuggestions = emptyList(),
            draftText = "",
            unansweredQuestion = null,
            temporaryEvidence = emptyMap(),
            quickSyncFeedback = null,
            applyingSuggestionId = null,
            staleSuggestionId = null,
            errorMessage = null,
            errorActionLabel = null,
        )
    }

    fun updateDraft(
        state: PersonalizationUiState,
        draftText: String,
    ): PersonalizationUiState = if (state.isReadOnly) state else state.copy(draftText = draftText)

    fun dismissSuggestion(
        state: PersonalizationUiState,
        suggestionId: String,
    ): PersonalizationUiState {
        if (state.isReadOnly || state.applyingSuggestionId != null) return state
        return state.copy(
            pendingSuggestions = state.pendingSuggestions.filterNot {
                it.changeSet.proposalId == suggestionId
            },
            staleSuggestionId = state.staleSuggestionId.takeUnless { it == suggestionId },
            errorMessage = null,
            errorActionLabel = null,
        )
    }

    fun beginConfirmation(
        state: PersonalizationUiState,
        suggestionId: String,
    ): PersonalizationConfirmationTransition {
        if (state.isReadOnly || state.applyingSuggestionId != null) {
            return PersonalizationConfirmationTransition(state, null)
        }
        val changeSet = state.pendingSuggestions
            .firstOrNull { it.changeSet.proposalId == suggestionId }
            ?.changeSet
            ?: return PersonalizationConfirmationTransition(state, null)
        return PersonalizationConfirmationTransition(
            state = state.copy(
                applyingSuggestionId = suggestionId,
                staleSuggestionId = null,
                errorMessage = null,
                errorActionLabel = null,
            ),
            changeSet = changeSet,
        )
    }

    fun completeConfirmation(
        state: PersonalizationUiState,
        result: PersonalizationApplyResult,
    ): PersonalizationUiState {
        val suggestionId = state.applyingSuggestionId ?: return state
        return when (result) {
            is PersonalizationApplyResult.Applied -> {
                val changeSet = state.pendingSuggestions
                    .firstOrNull { it.changeSet.proposalId == suggestionId }
                    ?.changeSet
                val destinations = changeSet
                    ?.mutations
                    ?.map { it.targetStore.plainLanguageName() }
                    ?.distinct()
                    .orEmpty()
                val receipt = PersonalizationTranscriptItem.Receipt(
                    destinationNames = destinations,
                    message = receiptMessage(destinations),
                )
                state.copy(
                    transcript = state.transcript + receipt,
                    pendingSuggestions = state.pendingSuggestions.filterNot {
                        it.changeSet.proposalId == suggestionId
                    },
                    applyingSuggestionId = null,
                    errorMessage = null,
                    errorActionLabel = null,
                )
            }

            is PersonalizationApplyResult.Rejected -> {
                if (result.failure.code == PersonalizationPreflightFailure.Code.STALE_TARGET) {
                    state.copy(
                        applyingSuggestionId = null,
                        staleSuggestionId = suggestionId,
                        errorMessage = STALE_SUGGESTION_MESSAGE,
                        errorActionLabel = REFRESH_SUGGESTION_LABEL,
                    )
                } else {
                    state.copy(
                        applyingSuggestionId = null,
                        errorMessage = result.failure.detail,
                        errorActionLabel = null,
                    )
                }
            }

            PersonalizationApplyResult.ActivationPending -> state.copy(
                applyingSuggestionId = null,
                errorMessage = ACTIVATION_PENDING_MESSAGE,
                errorActionLabel = null,
            )
        }
    }

    private fun receiptMessage(destinations: List<String>): String = when (destinations.size) {
        0 -> "Saved your personalization."
        1 -> "Saved to ${destinations.single()}."
        else -> "Saved to ${destinations.dropLast(1).joinToString(", ")} and ${destinations.last()}."
    }
}

fun PersonalizationStore.plainLanguageName(): String = when (this) {
    PersonalizationStore.GENERAL_PREFERENCE -> "What deserves your attention"
    PersonalizationStore.EXTRACTION_PREFERENCE -> "How Noti creates Todos & Keeps"
    PersonalizationStore.USER_KNOWLEDGE -> "About you"
}

private fun List<PersonalizationRecordSnapshot>.filterStore(
    store: PersonalizationStore,
): List<PersonalizationRecordSnapshot> = filter { it.targetStore == store }
