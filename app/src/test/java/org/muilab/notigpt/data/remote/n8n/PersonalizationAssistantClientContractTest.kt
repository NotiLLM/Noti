package org.muilab.notigpt.data.remote.n8n

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationChatRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationConfirmedStateDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryEvidenceDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationDiscoveryRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationQuickSyncTriggerDto
import org.muilab.notigpt.data.remote.n8n.dto.PersonalizationRecordSnapshotDto
import org.muilab.notigpt.domain.personalization.MessageTurn

class PersonalizationAssistantClientContractTest {
    private val gson = Gson()
    private val confirmedState = PersonalizationConfirmedStateDto(
        generalPreferences = listOf(snapshot("GENERAL_PREFERENCE", "general-1")),
        extractionPreferences = listOf(snapshot("EXTRACTION_PREFERENCE", "extract-1")),
        userKnowledge = listOf(snapshot("USER_KNOWLEDGE", "knowledge-1")),
    )

    @Test
    fun `chat sends all confirmed stores without mode or attached evidence`() {
        val json = gson.toJson(
            PersonalizationChatRequestDto(
                uiLanguage = "en",
                confirmedState = confirmedState,
                userText = "Help me tune Noti.",
            ),
        )

        assertContainsConfirmedStores(json)
        assertFalse(json.contains("chatMode"))
        assertFalse(json.contains("triggerContext"))
        assertFalse(json.contains("evidence"))
    }

    @Test
    fun `quick sync sends trigger context but no discovery evidence`() {
        val json = gson.toJson(
            PersonalizationQuickSyncRequestDto(
                uiLanguage = "en",
                confirmedState = confirmedState,
                triggerContext = PersonalizationQuickSyncTriggerDto(
                    entryPoint = "ITEM_EDIT",
                    action = "EDIT",
                    subjectId = "item-7",
                    userReason = "The result was too broad.",
                ),
            ),
        )

        assertContainsConfirmedStores(json)
        assertTrue(json.contains("triggerContext"))
        assertFalse(json.contains("evidence"))
        assertFalse(json.contains("chatMode"))
    }

    @Test
    fun `discovery sends request local evidence and no trigger context`() {
        val request = PersonalizationDiscoveryRequestDto(
            uiLanguage = "en",
            confirmedState = confirmedState,
            evidence = listOf(
                PersonalizationDiscoveryEvidenceDto(
                    id = "item-9",
                    source = "ACTIVE_ITEM",
                    content = "Current project milestone",
                ),
            ),
        )
        val json = gson.toJson(request)

        assertContainsConfirmedStores(json)
        assertTrue(json.contains("item-9"))
        assertTrue(request.evidenceIds == setOf("item-9"))
        assertFalse(json.contains("triggerContext"))
        assertFalse(json.contains("chatMode"))
    }

    @Test
    fun `decoder returns only a validated typed turn`() {
        val result = PersonalizationAssistantResponseDecoder.decodeAndValidate(
            rawJson = """{"turnType":"MESSAGE","uiLanguage":"en","message":"No changes are needed."}""",
            targetSnapshots = emptyList(),
        )

        assertTrue(result is PersonalizationValidationResult.Valid)
        assertTrue((result as PersonalizationValidationResult.Valid).turn is MessageTurn)
    }

    @Test
    fun `decoder rejects legacy and unknown response fields`() {
        val result = PersonalizationAssistantResponseDecoder.decodeAndValidate(
            rawJson = """{"turnType":"MESSAGE","uiLanguage":"en","message":"Done.","assistantMessage":"legacy"}""",
            targetSnapshots = emptyList(),
        )

        assertTrue(result is PersonalizationValidationResult.Invalid)
        assertTrue(
            (result as PersonalizationValidationResult.Invalid).failure.code ==
                PersonalizationValidationFailure.Code.INVALID_SHAPE,
        )
    }

    private fun assertContainsConfirmedStores(json: String) {
        assertTrue(json.contains("generalPreferences"))
        assertTrue(json.contains("extractionPreferences"))
        assertTrue(json.contains("userKnowledge"))
    }

    private fun snapshot(store: String, id: String) = PersonalizationRecordSnapshotDto(
        targetStore = store,
        id = id,
        statement = "A confirmed atomic statement.",
        createdAt = 10,
        updatedAt = 20,
    )
}
