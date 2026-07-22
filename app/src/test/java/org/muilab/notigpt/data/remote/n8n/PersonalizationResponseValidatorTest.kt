package org.muilab.notigpt.data.remote.n8n

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.data.remote.n8n.dto.ExpectedTargetDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationAssistantResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChangeSetDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationMutationDto
import org.muilab.notigpt.domain.personalization.AlternativeSetTurn
import org.muilab.notigpt.domain.personalization.KnowledgeCandidatesTurn
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore

class PersonalizationResponseValidatorTest {
    private val snapshots = listOf(
        PersonalizationRecordSnapshot(
            targetStore = PersonalizationStore.GENERAL_PREFERENCE,
            id = "attention-1",
            statement = "Messages about project launches deserve prompt attention.",
            createdAt = 10,
            updatedAt = 100,
        ),
        PersonalizationRecordSnapshot(
            targetStore = PersonalizationStore.EXTRACTION_PREFERENCE,
            id = "extract-1",
            statement = "Create a Todo when a bill includes a due date.",
            createdAt = 20,
            updatedAt = 200,
        ),
        PersonalizationRecordSnapshot(
            targetStore = PersonalizationStore.USER_KNOWLEDGE,
            id = "fact-1",
            statement = "I coordinate the Orion release.",
            createdAt = 30,
            updatedAt = 300,
        ),
    )

    @Test
    fun `rejects unknown turn discriminator`() {
        assertInvalid(
            validAlternative().copy(turnType = "OPTIONS"),
            PersonalizationValidationFailure.Code.UNKNOWN_TURN,
        )
    }

    @Test
    fun `rejects unknown store discriminator without defaulting`() {
        val response = validAlternativeWithMutation(
            addMutation().copy(targetStore = "PREFERENCES"),
        )

        assertInvalid(response, PersonalizationValidationFailure.Code.UNKNOWN_STORE)
    }

    @Test
    fun `rejects unknown operation discriminator without defaulting`() {
        val response = validAlternativeWithMutation(
            addMutation().copy(operation = "CREATE"),
        )

        assertInvalid(response, PersonalizationValidationFailure.Code.UNKNOWN_OPERATION)
    }

    @Test
    fun `rejects more than three alternatives`() {
        val alternatives = (1..4).map { index ->
            changeSet(
                setId = "set-$index",
                mutation = addMutation(
                    proposalId = "mutation-$index",
                    statement = "Keep travel option $index available for later review.",
                ),
            )
        }

        assertInvalid(
            validAlternative().copy(alternatives = alternatives),
            PersonalizationValidationFailure.Code.TOO_MANY_ALTERNATIVES,
        )
    }

    @Test
    fun `rejects empty alternatives and empty change sets`() {
        assertInvalid(
            validAlternative().copy(alternatives = emptyList()),
            PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET,
        )
        assertInvalid(
            validAlternative().copy(
                alternatives = listOf(changeSet("set-empty", addMutation()).copy(mutations = emptyList())),
            ),
            PersonalizationValidationFailure.Code.EMPTY_CHANGE_SET,
        )
    }

    @Test
    fun `rejects filler alternatives with identical normalized mutations`() {
        val first = changeSet(
            setId = "set-1",
            mutation = addMutation(proposalId = "mutation-1", statement = "Mute routine build reports."),
        )
        val second = changeSet(
            setId = "set-2",
            mutation = addMutation(proposalId = "mutation-2", statement = "  Mute   routine build reports.  "),
        )

        assertInvalid(
            validAlternative().copy(alternatives = listOf(first, second)),
            PersonalizationValidationFailure.Code.DUPLICATE_ALTERNATIVE,
        )
    }

    @Test
    fun `rejects missing target ids`() {
        val mutation = updateMutation().copy(
            expectedTarget = ExpectedTargetDto(id = null, updatedAt = 100),
        )

        assertInvalid(
            validAlternativeWithMutation(mutation),
            PersonalizationValidationFailure.Code.TARGET_REQUIRED,
        )
    }

    @Test
    fun `rejects duplicate target ids inside an atomic set`() {
        val first = updateMutation(proposalId = "mutation-1")
        val second = updateMutation(proposalId = "mutation-2", statement = "Only urgent launch messages deserve alerts.")
        val set = changeSet("set-1", first).copy(mutations = listOf(first, second))

        assertInvalid(
            validAlternative().copy(alternatives = listOf(set)),
            PersonalizationValidationFailure.Code.DUPLICATE_TARGET_ID,
        )
    }

    @Test
    fun `rejects update targets absent from the request snapshot`() {
        val mutation = updateMutation().copy(
            expectedTarget = ExpectedTargetDto(id = "missing", updatedAt = 100),
        )

        assertInvalid(
            validAlternativeWithMutation(mutation),
            PersonalizationValidationFailure.Code.TARGET_NOT_FOUND,
        )
    }

    @Test
    fun `rejects missing and mismatched expected timestamps`() {
        assertInvalid(
            validAlternativeWithMutation(
                updateMutation().copy(expectedTarget = ExpectedTargetDto("attention-1", null)),
            ),
            PersonalizationValidationFailure.Code.STALE_TARGET_REQUIRED,
        )
        assertInvalid(
            validAlternativeWithMutation(
                updateMutation().copy(expectedTarget = ExpectedTargetDto("attention-1", 99)),
            ),
            PersonalizationValidationFailure.Code.STALE_TARGET_MISMATCH,
        )
    }

    @Test
    fun `rejects empty and multi-sentence mutation text`() {
        assertInvalid(
            validAlternativeWithMutation(addMutation(statement = "  \n  ")),
            PersonalizationValidationFailure.Code.INVALID_STATEMENT,
        )
        assertInvalid(
            validAlternativeWithMutation(
                addMutation(statement = "Save flight changes. Ignore hotel changes."),
            ),
            PersonalizationValidationFailure.Code.INVALID_STATEMENT,
        )
    }

    @Test
    fun `rejects duplicate proposal ids`() {
        val first = addMutation(proposalId = "same", statement = "Save museum tickets for later.")
        val second = addMutation(proposalId = "same", statement = "Save train tickets for later.")
        val set = changeSet("set-1", first).copy(mutations = listOf(first, second))

        assertInvalid(
            validAlternative().copy(alternatives = listOf(set)),
            PersonalizationValidationFailure.Code.DUPLICATE_PROPOSAL_ID,
        )
    }

    @Test
    fun `rejects partial sets when any mutation is malformed`() {
        val valid = addMutation(proposalId = "valid", statement = "Keep deployment summaries for later.")
        val invalid = addMutation(proposalId = "invalid", statement = "")
        val set = changeSet("set-1", valid).copy(mutations = listOf(valid, invalid))

        assertInvalid(
            validAlternative().copy(alternatives = listOf(set)),
            PersonalizationValidationFailure.Code.INVALID_STATEMENT,
        )
    }

    @Test
    fun `rejects evidence references outside the request`() {
        val response = knowledgeResponse(
            candidates = listOf(
                addMutation(
                    proposalId = "candidate-1",
                    store = "USER_KNOWLEDGE",
                    statement = "I am preparing for the Harbor certification.",
                    evidenceRefs = listOf("notification-missing"),
                ),
            ),
        )

        assertInvalid(
            response,
            PersonalizationValidationFailure.Code.INVALID_EVIDENCE_REF,
            evidenceIds = setOf("item-7"),
        )
    }

    @Test
    fun `rejects ambiguous turn shapes carrying fields from another variant`() {
        val response = validAlternative().copy(message = "Choose one option.")

        assertInvalid(response, PersonalizationValidationFailure.Code.INVALID_SHAPE)
    }

    @Test
    fun `accepts a clear one-option fast path and normalizes whitespace only`() {
        val result = PersonalizationResponseValidator.validate(
            response = validAlternativeWithMutation(
                addMutation(statement = "  Keep   package delivery updates for later.  "),
            ),
            targetSnapshots = snapshots,
        )

        assertTrue(result is PersonalizationValidationResult.Valid)
        val turn = (result as PersonalizationValidationResult.Valid).turn as AlternativeSetTurn
        assertEquals("Keep package delivery updates for later.", turn.alternatives.single().mutations.single().statement)
    }

    @Test
    fun `accepts independently confirmable knowledge candidates from multi-domain evidence`() {
        val fixture = """{
          "turnType":"KNOWLEDGE_CANDIDATES",
          "uiLanguage":"en",
          "knowledgeCandidates":[
            {
              "proposalId":"candidate-course",
              "targetStore":"USER_KNOWLEDGE",
              "operation":"ADD",
              "statement":"I am taking an evening ceramics course.",
              "reason":"Several current class reminders identify the same course.",
              "evidenceRefs":["notification-class","item-supplies"]
            },
            {
              "proposalId":"candidate-release",
              "targetStore":"USER_KNOWLEDGE",
              "operation":"UPDATE",
              "statement":"I lead the Orion release through September.",
              "expectedTarget":{"id":"fact-1","updatedAt":300},
              "reason":"A current project item gives the newer end date.",
              "evidenceRefs":["item-orion"]
            }
          ]
        }"""
        val response = Gson().fromJson(fixture, PersonalizationAssistantResponseDto::class.java)

        val result = PersonalizationResponseValidator.validate(
            response = response,
            targetSnapshots = snapshots,
            evidenceIds = setOf("notification-class", "item-supplies", "item-orion"),
        )

        assertTrue(result is PersonalizationValidationResult.Valid)
        val turn = (result as PersonalizationValidationResult.Valid).turn as KnowledgeCandidatesTurn
        assertEquals(2, turn.candidates.size)
    }

    private fun assertInvalid(
        response: PersonalizationAssistantResponseDto,
        code: PersonalizationValidationFailure.Code,
        evidenceIds: Set<String>? = null,
    ) {
        val result = PersonalizationResponseValidator.validate(response, snapshots, evidenceIds)
        assertTrue("Expected Invalid but was $result", result is PersonalizationValidationResult.Invalid)
        assertEquals(code, (result as PersonalizationValidationResult.Invalid).failure.code)
    }

    private fun validAlternativeWithMutation(
        mutation: PersonalizationMutationDto,
    ): PersonalizationAssistantResponseDto = validAlternative().copy(
        alternatives = listOf(changeSet("set-1", mutation)),
    )

    private fun validAlternative(): PersonalizationAssistantResponseDto = PersonalizationAssistantResponseDto(
        turnType = "ALTERNATIVE_SET",
        uiLanguage = "en",
        decisionQuestion = "Which behavior should Noti use?",
        variationAxis = "scope",
        alternatives = listOf(changeSet("set-1", addMutation())),
    )

    private fun knowledgeResponse(
        candidates: List<PersonalizationMutationDto>,
    ) = PersonalizationAssistantResponseDto(
        turnType = "KNOWLEDGE_CANDIDATES",
        uiLanguage = "en",
        knowledgeCandidates = candidates,
    )

    private fun changeSet(
        setId: String,
        mutation: PersonalizationMutationDto,
    ) = PersonalizationChangeSetDto(
        proposalId = setId,
        resultingBehavior = "The selected behavior will apply exactly as written.",
        mutations = listOf(mutation),
    )

    private fun addMutation(
        proposalId: String = "mutation-1",
        store: String = "GENERAL_PREFERENCE",
        statement: String = "Keep package delivery updates for later.",
        evidenceRefs: List<String>? = null,
    ) = PersonalizationMutationDto(
        proposalId = proposalId,
        targetStore = store,
        operation = "ADD",
        statement = statement,
        evidenceRefs = evidenceRefs,
    )

    private fun updateMutation(
        proposalId: String = "mutation-update",
        statement: String = "Only urgent project launch messages deserve prompt attention.",
    ) = PersonalizationMutationDto(
        proposalId = proposalId,
        targetStore = "GENERAL_PREFERENCE",
        operation = "UPDATE",
        statement = statement,
        expectedTarget = ExpectedTargetDto(id = "attention-1", updatedAt = 100),
    )
}
