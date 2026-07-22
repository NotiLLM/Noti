package org.muilab.notigpt.ui.preference.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.PersonalizationClientResult
import org.muilab.notigpt.data.remote.n8n.PreferenceChatClient
import org.muilab.notigpt.data.remote.n8n.PreferenceContextDiscoverClient
import org.muilab.notigpt.data.remote.n8n.PreferenceQuickSyncClient
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatMessageDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationConfirmedStateDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncTriggerDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationRecordSnapshotDto
import org.muilab.notigpt.data.repository.personalization.PersonalizationRepository
import org.muilab.notigpt.data.repository.personalization.StoreBackedPersonalizationRepository
import org.muilab.notigpt.data.repository.personalization.V54PersonalizationGateway
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
import org.muilab.notigpt.domain.personalization.QuestionTurn
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.AndroidPersonalizationConnectivity
import org.muilab.notigpt.ui.preference.model.PendingPersonalizationSuggestion
import org.muilab.notigpt.ui.preference.model.NETWORK_ASSISTANT_MESSAGE
import org.muilab.notigpt.ui.preference.model.PersonalizationTranscriptItem
import org.muilab.notigpt.ui.preference.model.PersonalizationUiState
import org.muilab.notigpt.ui.preference.model.PersonalizationUiStateReducer
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.preference.model.QuickSyncFeedbackState

/** Owns confirmed repository flows and process-local personalization assistant state. */
class PreferenceViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository: PersonalizationRepository
    private val _uiState = MutableStateFlow(
        PersonalizationUiState(
            draftText = savedStateHandle.get<String>(KEY_DRAFT).orEmpty(),
        ),
    )
    val uiState: StateFlow<PersonalizationUiState> = _uiState.asStateFlow()

    private val _snackbarEvent = MutableStateFlow<SnackbarEvent?>(null)
    val snackbarEvent: StateFlow<SnackbarEvent?> = _snackbarEvent.asStateFlow()

    private val _navigateToPersonalization = MutableStateFlow(false)
    val navigateToPersonalization: StateFlow<Boolean> = _navigateToPersonalization.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = StoreBackedPersonalizationRepository(
            V54PersonalizationGateway(
                extractionPreferenceDao = database.extractionPreferenceDao(),
                userContextDao = database.userContextDao(),
            ),
        )
        viewModelScope.launch {
            repository.observeConfirmedSnapshots().collect { snapshots ->
                updateState { it.withConfirmed(snapshots) }
            }
        }
        viewModelScope.launch {
            AndroidPersonalizationConnectivity(application).observeOnline().collect { isOnline ->
                updateState { it.copy(isReadOnly = !isOnline) }
            }
        }
    }

    data class SnackbarEvent(
        val entryPoint: PreferenceEntryPoint,
        val item: SavedItem?,
        val savedItemBefore: SavedItem?,
        val contextData: Map<String, Any?>,
    )

    fun updateDraft(text: String) {
        val updated = PersonalizationUiStateReducer.updateDraft(_uiState.value, text)
        if (updated === _uiState.value) return
        _uiState.value = updated
        savedStateHandle[KEY_DRAFT] = updated.draftText
    }

    fun clearConversation() {
        val updated = PersonalizationUiStateReducer.clearConversation(_uiState.value)
        if (updated === _uiState.value) return
        _uiState.value = updated
        savedStateHandle[KEY_DRAFT] = ""
    }

    fun dismissSuggestion(suggestionId: String) {
        _uiState.value = PersonalizationUiStateReducer.dismissSuggestion(
            state = _uiState.value,
            suggestionId = suggestionId,
        )
    }

    fun confirmSuggestion(suggestionId: String) {
        val transition = PersonalizationUiStateReducer.beginConfirmation(
            state = _uiState.value,
            suggestionId = suggestionId,
        )
        _uiState.value = transition.state
        val changeSet = transition.changeSet ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.apply(changeSet)
            withContext(Dispatchers.Main) {
                _uiState.value = PersonalizationUiStateReducer.completeConfirmation(
                    state = _uiState.value,
                    result = result,
                )
            }
        }
    }

    fun refreshSuggestion(suggestionId: String) {
        if (isMutationBlocked()) return
        val suggestion = _uiState.value.pendingSuggestions
            .firstOrNull { it.changeSet.proposalId == suggestionId }
            ?: return
        _uiState.value = _uiState.value.copy(
            staleSuggestionId = null,
            errorMessage = null,
            errorActionLabel = null,
        )
        sendMessage("Refresh this suggestion using my latest preferences: ${suggestion.changeSet.resultingBehavior}")
    }

    fun sendMessage(text: String = _uiState.value.draftText) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || isMutationBlocked() || _uiState.value.isAssistantLoading) return
        val stateWithMessage = _uiState.value.copy(
            transcript = _uiState.value.transcript + PersonalizationTranscriptItem.UserMessage(trimmed),
            draftText = "",
            unansweredQuestion = null,
            isAssistantLoading = true,
            errorMessage = null,
            errorActionLabel = null,
        )
        _uiState.value = stateWithMessage
        savedStateHandle[KEY_DRAFT] = ""
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                PreferenceChatClient.interact(
                    PersonalizationChatRequestDto(
                        uiLanguage = Locale.getDefault().toLanguageTag(),
                        confirmedState = stateWithMessage.toConfirmedStateDto(),
                        userText = trimmed,
                        conversation = stateWithMessage.transcript.toConversationDtos(),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Assistant request failed", error)
                PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.NETWORK)
            }
            withContext(Dispatchers.Main) { acceptClientResult(result) }
        }
    }

    fun answerWith(starter: String) = sendMessage(starter)

    fun deleteConfirmed(snapshot: PersonalizationRecordSnapshot) {
        if (isMutationBlocked()) return
        val changeSet = PersonalizationChangeSet(
            proposalId = "delete-${snapshot.id}",
            resultingBehavior = "Remove: ${snapshot.statement}",
            mutations = listOf(
                PersonalizationMutation(
                    proposalId = "delete-${snapshot.id}",
                    targetStore = snapshot.targetStore,
                    operation = PersonalizationOperation.DELETE,
                    expectedTarget = ExpectedTarget(snapshot.id, snapshot.updatedAt),
                ),
            ),
        )
        applyDirectChangeSet(changeSet)
    }

    fun discoverAboutMe() {
        if (isMutationBlocked() || _uiState.value.isAssistantLoading) return
        updateState { it.copy(isAssistantLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                PreferenceContextDiscoverClient.discover(
                    PersonalizationDiscoveryRequestDto(
                        uiLanguage = Locale.getDefault().toLanguageTag(),
                        confirmedState = _uiState.value.toConfirmedStateDto(),
                        evidence = emptyList(),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Discovery request failed", error)
                PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.NETWORK)
            }
            withContext(Dispatchers.Main) { acceptClientResult(result) }
        }
    }

    fun startFlow(
        entryPoint: PreferenceEntryPoint,
        item: SavedItem?,
        savedItemBefore: SavedItem? = null,
        contextData: Map<String, Any?> = emptyMap(),
    ) {
        if (isMutationBlocked()) return
        _snackbarEvent.value = SnackbarEvent(
            entryPoint = entryPoint,
            item = item,
            savedItemBefore = savedItemBefore,
            contextData = contextData,
        )
    }

    fun startFlowSheet(
        entryPoint: PreferenceEntryPoint,
        item: SavedItem?,
        savedItemBefore: SavedItem? = null,
        contextData: Map<String, Any?> = emptyMap(),
    ) {
        if (isMutationBlocked()) return
        openFeedbackComposer(SnackbarEvent(entryPoint, item, savedItemBefore, contextData))
    }

    fun dismissSnackbar() {
        _snackbarEvent.value = null
    }

    fun promoteSnackbarToFlow(event: SnackbarEvent) {
        if (isMutationBlocked()) return
        _snackbarEvent.value = null
        openFeedbackComposer(event)
    }

    fun onPersonalizationNavigated() {
        _navigateToPersonalization.value = false
    }

    fun updateQuickSyncDraft(text: String) {
        if (isMutationBlocked()) return
        updateState { state ->
            state.copy(
                quickSyncFeedback = state.quickSyncFeedback?.copy(draftText = text),
            )
        }
    }

    fun dismissQuickSyncFeedback() {
        if (isMutationBlocked()) return
        updateState { it.copy(quickSyncFeedback = null) }
    }

    fun submitQuickSyncFeedback(reason: String) {
        val feedback = _uiState.value.quickSyncFeedback ?: return
        val trimmed = reason.trim()
        if (trimmed.isBlank() || isMutationBlocked() || _uiState.value.isAssistantLoading) return
        updateState {
            it.copy(
                quickSyncFeedback = null,
                isAssistantLoading = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                PreferenceQuickSyncClient.sync(
                    PersonalizationQuickSyncRequestDto(
                        uiLanguage = Locale.getDefault().toLanguageTag(),
                        confirmedState = _uiState.value.toConfirmedStateDto(),
                        triggerContext = PersonalizationQuickSyncTriggerDto(
                            entryPoint = feedback.entryPoint.wire,
                            action = feedback.action,
                            subjectId = feedback.subjectId,
                            context = feedback.context,
                            userReason = trimmed,
                        ),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Quick Sync request failed", error)
                PersonalizationClientResult.Failure(PersonalizationClientResult.Kind.NETWORK)
            }
            withContext(Dispatchers.Main) {
                acceptClientResult(result)
                if (result is PersonalizationClientResult.Success) {
                    _navigateToPersonalization.value = true
                }
            }
        }
    }

    private fun openFeedbackComposer(event: SnackbarEvent) {
        val subjectId = event.item?.savedItemId
            ?: event.savedItemBefore?.savedItemId
            ?: event.contextData["notiKey"]?.toString()
        val starters = when (event.entryPoint) {
            PreferenceEntryPoint.EDIT -> listOf(
                R.string.personalization_reason_wording,
                R.string.personalization_reason_urgency,
                R.string.personalization_reason_detail,
            )
            PreferenceEntryPoint.DELETE -> listOf(
                R.string.personalization_reason_not_useful,
                R.string.personalization_reason_too_broad,
                R.string.personalization_reason_not_item,
            )
            PreferenceEntryPoint.MANUAL_EXTRACT -> listOf(
                R.string.personalization_reason_sender,
                R.string.personalization_reason_topic,
                R.string.personalization_reason_action,
            )
        }.map { getApplication<Application>().getString(it) }
        updateState { state ->
            state.copy(
                quickSyncFeedback = QuickSyncFeedbackState(
                    entryPoint = event.entryPoint,
                    action = event.entryPoint.wire,
                    subjectId = subjectId,
                    context = event.contextData.mapValues { it.value?.toString().orEmpty() },
                    reasonStarters = starters,
                ),
            )
        }
    }

    private fun applyDirectChangeSet(changeSet: PersonalizationChangeSet) {
        val suggestion = PendingPersonalizationSuggestion(changeSet)
        updateState { state ->
            state.copy(pendingSuggestions = state.pendingSuggestions + suggestion)
        }
        confirmSuggestion(changeSet.proposalId)
    }

    private fun acceptClientResult(result: PersonalizationClientResult) {
        when (result) {
            is PersonalizationClientResult.Success -> acceptTurn(result.turn)
            is PersonalizationClientResult.Failure -> updateState { state ->
                state.copy(
                    isAssistantLoading = false,
                    errorMessage = NETWORK_ASSISTANT_MESSAGE,
                    errorActionLabel = null,
                )
            }
        }
    }

    private fun acceptTurn(turn: PersonalizationAssistantTurn) {
        updateState { state ->
            val suggestions = when (turn) {
                is AlternativeSetTurn -> turn.alternatives.map(::PendingPersonalizationSuggestion)
                is KnowledgeCandidatesTurn -> turn.candidates.map { mutation ->
                    PendingPersonalizationSuggestion(
                        PersonalizationChangeSet(
                            proposalId = mutation.proposalId,
                            resultingBehavior = mutation.statement ?: mutation.reason.orEmpty(),
                            mutations = listOf(mutation),
                            reason = mutation.reason,
                        ),
                    )
                }
                is MessageTurn, is QuestionTurn -> emptyList()
            }
            val evidence = suggestions.associate { suggestion ->
                suggestion.changeSet.proposalId to suggestion.evidenceRefs
            }.filterValues { it.isNotEmpty() }
            state.copy(
                transcript = state.transcript + PersonalizationTranscriptItem.AssistantTurn(turn),
                pendingSuggestions = state.pendingSuggestions + suggestions,
                unansweredQuestion = turn as? QuestionTurn,
                temporaryEvidence = state.temporaryEvidence + evidence,
                isAssistantLoading = false,
            )
        }
    }

    private fun isMutationBlocked(): Boolean = _uiState.value.isReadOnly

    private fun updateState(transform: (PersonalizationUiState) -> PersonalizationUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun PersonalizationUiState.toConfirmedStateDto(): PersonalizationConfirmedStateDto =
        PersonalizationConfirmedStateDto(
            generalPreferences = generalPreferences.map { it.toDto() },
            extractionPreferences = extractionPreferences.map { it.toDto() },
            userKnowledge = userKnowledge.map { it.toDto() },
        )

    private fun PersonalizationRecordSnapshot.toDto() = PersonalizationRecordSnapshotDto(
        targetStore = targetStore.name,
        id = id,
        statement = statement,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun List<PersonalizationTranscriptItem>.toConversationDtos(): List<PersonalizationChatMessageDto> =
        map { item ->
            when (item) {
                is PersonalizationTranscriptItem.UserMessage ->
                    PersonalizationChatMessageDto(role = "user", content = item.text)
                is PersonalizationTranscriptItem.AssistantTurn ->
                    PersonalizationChatMessageDto(role = "assistant", content = item.turn.displayText())
                is PersonalizationTranscriptItem.Receipt ->
                    PersonalizationChatMessageDto(role = "assistant", content = item.message)
            }
        }

    private fun PersonalizationAssistantTurn.displayText(): String = when (this) {
        is QuestionTurn -> "$question\n\n$rationale"
        is AlternativeSetTurn -> decisionQuestion
        is KnowledgeCandidatesTurn -> candidates.joinToString("\n") { it.statement.orEmpty() }
        is MessageTurn -> message
    }

    companion object {
        private const val TAG = "PreferenceViewModel"
        private const val KEY_DRAFT = "personalizationDraft"
    }
}
