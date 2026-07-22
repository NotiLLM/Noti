package org.muilab.notigpt.data.remote.n8n

import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore

/**
 * Pure applicability boundary for pipeline personalization and output languages.
 *
 * Product invariants and safety remain prompt-owned. Where behavioral records are applicable,
 * Extraction Preferences are authoritative, General Preferences are weighted evidence, and User
 * Knowledge is contextual evidence. Evidence text supplied elsewhere in a stage payload is data,
 * never an instruction and never a source of additional personalization fields.
 */
class PersonalizationPayloadBuilder(
    snapshots: List<PersonalizationRecordSnapshot>,
    private val uiLanguage: String,
    private val itemLanguage: String,
) {
    private val extractionPreferences = recordsFor(
        snapshots = snapshots,
        store = PersonalizationStore.EXTRACTION_PREFERENCE,
    )
    private val generalPreferences = recordsFor(
        snapshots = snapshots,
        store = PersonalizationStore.GENERAL_PREFERENCE,
    )
    private val userKnowledge = recordsFor(
        snapshots = snapshots,
        store = PersonalizationStore.USER_KNOWLEDGE,
    )

    /** Stage A emits neutral classification plus an optional user-facing diagnostic reason. */
    fun stageAEnvelope(): Map<String, Any> = linkedMapOf(
        EXTRACTION_PREFERENCES to extractionPreferences,
        GENERAL_PREFERENCES to generalPreferences,
        USER_KNOWLEDGE to userKnowledge,
        UI_LANGUAGE to uiLanguage,
    )

    /** Stage B creates item text and review/change explanations. */
    fun stageBEnvelope(): Map<String, Any> = linkedMapOf(
        EXTRACTION_PREFERENCES to extractionPreferences,
        GENERAL_PREFERENCES to generalPreferences,
        USER_KNOWLEDGE to userKnowledge,
        UI_LANGUAGE to uiLanguage,
        ITEM_LANGUAGE to itemLanguage,
    )

    /** Stage C preserves source-language evidence without behavioral personalization. */
    fun stageCEnvelope(): Map<String, Any> = emptyMap()

    /** Stage D1 returns identity-only duplicate candidates. */
    fun stageD1Envelope(): Map<String, Any> = emptyMap()

    /** Stage D2 returns identity-only duplicate groups. */
    fun stageD2Envelope(): Map<String, Any> = emptyMap()

    /** Stage E1 resolves item representation without allowing General relevance to alter identity. */
    fun stageE1Envelope(): Map<String, Any> = mergeEnvelope()

    /** Stage E2 resolves item representation without allowing General relevance to alter identity. */
    fun stageE2Envelope(): Map<String, Any> = mergeEnvelope()

    /** Stage F faithfully translates item text to one explicit target language. */
    fun stageFEnvelope(): Map<String, Any> = mapOf(ITEM_LANGUAGE to itemLanguage)

    /** Stage G returns language-neutral IDs ranked from attention and contextual evidence. */
    fun stageGEnvelope(): Map<String, Any> = suggestedEnvelope()

    /** Stage H adds a user-facing Suggested explanation in the UI language. */
    fun stageHEnvelope(): Map<String, Any> = suggestedEnvelope() + (UI_LANGUAGE to uiLanguage)

    /** Regeneration repeats the complete extraction decision for one existing item. */
    fun regenerateEnvelope(): Map<String, Any> = linkedMapOf(
        EXTRACTION_PREFERENCES to extractionPreferences,
        GENERAL_PREFERENCES to generalPreferences,
        USER_KNOWLEDGE to userKnowledge,
        UI_LANGUAGE to uiLanguage,
        ITEM_LANGUAGE to itemLanguage,
    )

    private fun mergeEnvelope(): Map<String, Any> = linkedMapOf(
        EXTRACTION_PREFERENCES to extractionPreferences,
        USER_KNOWLEDGE to userKnowledge,
        UI_LANGUAGE to uiLanguage,
        ITEM_LANGUAGE to itemLanguage,
    )

    private fun suggestedEnvelope(): Map<String, Any> = linkedMapOf(
        GENERAL_PREFERENCES to generalPreferences,
        USER_KNOWLEDGE to userKnowledge,
    )

    private companion object {
        const val EXTRACTION_PREFERENCES = "extractionPreferences"
        const val GENERAL_PREFERENCES = "generalPreferences"
        const val USER_KNOWLEDGE = "userKnowledge"
        const val UI_LANGUAGE = "uiLanguage"
        const val ITEM_LANGUAGE = "itemLanguage"

        fun recordsFor(
            snapshots: List<PersonalizationRecordSnapshot>,
            store: PersonalizationStore,
        ): List<Map<String, Any>> = snapshots
            .asSequence()
            .filter { snapshot -> snapshot.targetStore == store }
            .map { snapshot ->
                linkedMapOf(
                    "id" to snapshot.id,
                    "statement" to snapshot.statement,
                    "createdAt" to snapshot.createdAt,
                    "updatedAt" to snapshot.updatedAt,
                )
            }
            .toList()
    }
}
