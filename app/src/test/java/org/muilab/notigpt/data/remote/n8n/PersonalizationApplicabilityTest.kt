package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore

class PersonalizationApplicabilityTest {
    private val snapshots = listOf(
        snapshot(PersonalizationStore.GENERAL_PREFERENCE, "general-1"),
        snapshot(PersonalizationStore.EXTRACTION_PREFERENCE, "extraction-1"),
        snapshot(PersonalizationStore.USER_KNOWLEDGE, "knowledge-1"),
    )
    private val builder = PersonalizationPayloadBuilder(
        snapshots = snapshots,
        uiLanguage = "zh-TW",
        itemLanguage = "original",
    )

    @Test
    fun `each pipeline stage exposes exactly its approved top-level keys`() {
        assertKeys(
            builder.stageAEnvelope(),
            "extractionPreferences",
            "generalPreferences",
            "userKnowledge",
            "uiLanguage",
        )
        assertKeys(
            builder.stageBEnvelope(),
            "extractionPreferences",
            "generalPreferences",
            "itemLanguage",
            "uiLanguage",
            "userKnowledge",
        )
        assertKeys(builder.stageCEnvelope())
        assertKeys(builder.stageD1Envelope())
        assertKeys(builder.stageD2Envelope())
        assertKeys(
            builder.stageE1Envelope(),
            "extractionPreferences",
            "itemLanguage",
            "uiLanguage",
            "userKnowledge",
        )
        assertKeys(
            builder.stageE2Envelope(),
            "extractionPreferences",
            "itemLanguage",
            "uiLanguage",
            "userKnowledge",
        )
        assertKeys(builder.stageFEnvelope(), "itemLanguage")
        assertKeys(
            builder.stageGEnvelope(),
            "generalPreferences",
            "userKnowledge",
        )
        assertKeys(
            builder.stageHEnvelope(),
            "generalPreferences",
            "uiLanguage",
            "userKnowledge",
        )
        assertKeys(
            builder.regenerateEnvelope(),
            "extractionPreferences",
            "generalPreferences",
            "itemLanguage",
            "uiLanguage",
            "userKnowledge",
        )
    }

    @Test
    fun `store payloads contain only canonical field-free records`() {
        val envelopes = listOf(
            builder.stageAEnvelope(),
            builder.stageBEnvelope(),
            builder.stageE1Envelope(),
            builder.stageE2Envelope(),
            builder.stageGEnvelope(),
            builder.stageHEnvelope(),
            builder.regenerateEnvelope(),
        )

        envelopes.forEach { envelope ->
            envelope.values.filterIsInstance<List<*>>().forEach { records ->
                records.filterIsInstance<Map<*, *>>().forEach { record ->
                    assertEquals(
                        setOf("id", "statement", "createdAt", "updatedAt"),
                        record.keys,
                    )
                    assertFalse(record.containsKey("preferenceType"))
                    assertFalse(record.containsKey("type"))
                    assertFalse(record.containsKey("category"))
                }
            }
        }
    }

    @Test
    fun `language fields stay distinct and neutral stages receive neither`() {
        assertEquals("zh-TW", builder.stageBEnvelope()["uiLanguage"])
        assertEquals("original", builder.stageBEnvelope()["itemLanguage"])
        assertEquals("original", builder.stageFEnvelope()["itemLanguage"])

        listOf(
            builder.stageCEnvelope(),
            builder.stageD1Envelope(),
            builder.stageD2Envelope(),
            builder.stageGEnvelope(),
        ).forEach { envelope ->
            assertFalse(envelope.containsKey("uiLanguage"))
            assertFalse(envelope.containsKey("itemLanguage"))
            assertFalse(envelope.containsKey("language"))
        }
    }

    private fun assertKeys(envelope: Map<String, Any>, vararg expected: String) {
        assertEquals(expected.toSet(), envelope.keys)
        assertFalse(envelope.containsKey("language"))
        assertFalse(envelope.containsKey("targetExtractionLanguage"))
        assertFalse(envelope.containsKey("userContexts"))
    }

    private fun snapshot(
        store: PersonalizationStore,
        id: String,
    ) = PersonalizationRecordSnapshot(
        targetStore = store,
        id = id,
        statement = "$id statement",
        createdAt = 100L,
        updatedAt = 200L,
    )
}
