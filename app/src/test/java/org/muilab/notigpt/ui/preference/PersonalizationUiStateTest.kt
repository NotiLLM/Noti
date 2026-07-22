package org.muilab.notigpt.ui.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.muilab.notigpt.data.repository.personalization.PersonalizationApplyResult
import org.muilab.notigpt.domain.personalization.ExpectedTarget
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutation
import org.muilab.notigpt.domain.personalization.PersonalizationOperation
import org.muilab.notigpt.domain.personalization.PersonalizationPreflightFailure
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import org.muilab.notigpt.domain.personalization.QuestionTurn
import org.muilab.notigpt.ui.preference.model.PendingPersonalizationSuggestion
import org.muilab.notigpt.ui.preference.model.PersonalizationTranscriptItem
import org.muilab.notigpt.ui.preference.model.PersonalizationUiState
import org.muilab.notigpt.ui.preference.model.PersonalizationUiStateReducer

class PersonalizationUiStateTest {
    @Test
    fun clearConversation_preservesConfirmedListsAndDropsEveryTemporaryValue() {
        val confirmed = snapshots()
        val state = PersonalizationUiState.fromConfirmed(confirmed).copy(
            transcript = listOf(PersonalizationTranscriptItem.UserMessage("Please remember this.")),
            pendingSuggestions = listOf(PendingPersonalizationSuggestion(changeSet())),
            draftText = "unfinished",
            unansweredQuestion = QuestionTurn(
                uiLanguage = "en",
                question = "What matters?",
                rationale = "This helps Noti prioritize.",
            ),
            temporaryEvidence = mapOf("candidate-1" to listOf("notification-1")),
        )

        val cleared = PersonalizationUiStateReducer.clearConversation(state)

        assertEquals(state.generalPreferences, cleared.generalPreferences)
        assertEquals(state.extractionPreferences, cleared.extractionPreferences)
        assertEquals(state.userKnowledge, cleared.userKnowledge)
        assertEquals(emptyList<PersonalizationTranscriptItem>(), cleared.transcript)
        assertEquals(emptyList<PendingPersonalizationSuggestion>(), cleared.pendingSuggestions)
        assertEquals("", cleared.draftText)
        assertNull(cleared.unansweredQuestion)
        assertEquals(emptyMap<String, List<String>>(), cleared.temporaryEvidence)
    }

    @Test
    fun offlineMutationAndAssistantEvents_areNoOps() {
        val state = PersonalizationUiState.fromConfirmed(snapshots()).copy(
            transcript = listOf(PersonalizationTranscriptItem.UserMessage("Keep me")),
            pendingSuggestions = listOf(PendingPersonalizationSuggestion(changeSet())),
            draftText = "draft",
            isReadOnly = true,
        )

        assertSame(state, PersonalizationUiStateReducer.clearConversation(state))
        assertSame(state, PersonalizationUiStateReducer.updateDraft(state, "changed"))
        assertSame(state, PersonalizationUiStateReducer.dismissSuggestion(state, "set-1"))
        val confirmation = PersonalizationUiStateReducer.beginConfirmation(state, "set-1")
        assertSame(state, confirmation.state)
        assertNull(confirmation.changeSet)
    }

    @Test
    fun confirmSelection_returnsOneWholeChangeSetAndLocksItAgainstDuplicateApply() {
        val changeSet = changeSet()
        val state = PersonalizationUiState.fromConfirmed(snapshots()).copy(
            pendingSuggestions = listOf(PendingPersonalizationSuggestion(changeSet)),
        )

        val first = PersonalizationUiStateReducer.beginConfirmation(state, changeSet.proposalId)
        val second = PersonalizationUiStateReducer.beginConfirmation(first.state, changeSet.proposalId)

        assertSame(changeSet, first.changeSet)
        assertEquals(2, first.changeSet?.mutations?.size)
        assertEquals(changeSet.proposalId, first.state.applyingSuggestionId)
        assertNull(second.changeSet)
    }

    @Test
    fun staleApply_keepsConfirmedRowsAndExposesExactRefreshCopy() {
        val state = PersonalizationUiState.fromConfirmed(snapshots()).copy(
            pendingSuggestions = listOf(PendingPersonalizationSuggestion(changeSet())),
            applyingSuggestionId = "set-1",
        )

        val result = PersonalizationUiStateReducer.completeConfirmation(
            state = state,
            result = PersonalizationApplyResult.Rejected(
                PersonalizationPreflightFailure(
                    code = PersonalizationPreflightFailure.Code.STALE_TARGET,
                    detail = "target changed",
                ),
            ),
        )

        assertEquals(state.generalPreferences, result.generalPreferences)
        assertEquals(state.extractionPreferences, result.extractionPreferences)
        assertEquals(state.userKnowledge, result.userKnowledge)
        assertEquals(
            "This suggestion is out of date. Refresh it to use your latest preferences.",
            result.errorMessage,
        )
        assertEquals("Refresh suggestion", result.errorActionLabel)
        assertEquals("set-1", result.staleSuggestionId)
    }

    private fun snapshots() = listOf(
        snapshot(PersonalizationStore.GENERAL_PREFERENCE, "general-1", "Family messages matter."),
        snapshot(PersonalizationStore.EXTRACTION_PREFERENCE, "extract-1", "Create Todos for deadlines."),
        snapshot(PersonalizationStore.USER_KNOWLEDGE, "knowledge-1", "I coordinate the launch."),
    )

    private fun snapshot(
        store: PersonalizationStore,
        id: String,
        statement: String,
    ) = PersonalizationRecordSnapshot(
        targetStore = store,
        id = id,
        statement = statement,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun changeSet() = PersonalizationChangeSet(
        proposalId = "set-1",
        resultingBehavior = "Prioritize family messages and create Todos for explicit deadlines.",
        mutations = listOf(
            PersonalizationMutation(
                proposalId = "create-general",
                targetStore = PersonalizationStore.GENERAL_PREFERENCE,
                operation = PersonalizationOperation.ADD,
                statement = "Family messages deserve immediate attention.",
            ),
            PersonalizationMutation(
                proposalId = "update-extraction",
                targetStore = PersonalizationStore.EXTRACTION_PREFERENCE,
                operation = PersonalizationOperation.UPDATE,
                statement = "Create Todos only for notifications with explicit deadlines.",
                expectedTarget = ExpectedTarget("extract-1", 2),
            ),
        ),
    )
}
