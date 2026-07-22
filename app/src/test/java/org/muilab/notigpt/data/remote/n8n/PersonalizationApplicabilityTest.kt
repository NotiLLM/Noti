package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot
import org.muilab.notigpt.domain.personalization.PersonalizationStore
import java.io.File

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

    @Test
    fun `handlers route every pipeline call through its named stage envelope`() {
        assertRoutes("ExtractionPipelineHandler.kt", "stageAEnvelope", "stageBEnvelope", "stageCEnvelope", "stageD1Envelope", "stageE1Envelope")
        assertRoutes("ReflectionPipelineHandler.kt", "stageD2Envelope", "stageE2Envelope")
        assertRoutes("SuggestionRefreshHandler.kt", "stageGEnvelope", "stageHEnvelope")
        assertRoutes("SavedItemRegenerationHandler.kt", "regenerateEnvelope")
        assertRoutes("ReviewTranslationHandler.kt", "stageFEnvelope")
    }

    @Test
    fun `worker payload construction cannot reach legacy personalization stores`() {
        val handlerSources = HANDLER_FILES.joinToString("\n") { source(HANDLER_DIR + it) }
        listOf(
            "getExtractionPreferencesPayload",
            "getUserContextsPayload",
            "preferenceType",
            "userContextDao",
            "extractionPreferenceDao",
        ).forEach { forbidden ->
            assertFalse("handler sources must not contain $forbidden", handlerSources.contains(forbidden))
        }

        val contextSource = source("context/N8nWorkerContext.kt")
        assertTrue(contextSource.contains("PersonalizationRepository"))
        assertTrue(contextSource.contains("getConfirmedSnapshots"))
        assertTrue(contextSource.contains("PersonalizationPayloadBuilder"))
        assertFalse(contextSource.contains("userContextDao"))
        assertFalse(contextSource.contains("extractionPreferenceDao"))

        val supportSource = source(HANDLER_DIR + "ExtractionStageSupport.kt")
        assertFalse(supportSource.contains("\"language\""))
        assertFalse(supportSource.contains("targetExtractionLanguage"))
    }

    private fun assertRoutes(fileName: String, vararg envelopeMethods: String) {
        val handler = source(HANDLER_DIR + fileName)
        envelopeMethods.forEach { method ->
            assertTrue("$fileName must route through $method", handler.contains(method))
        }
    }

    private fun source(relativePath: String): String = File(MAIN_N8N_DIR, relativePath).readText()

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

    private companion object {
        const val MAIN_N8N_DIR = "src/main/java/org/muilab/notigpt/data/remote/n8n"
        const val HANDLER_DIR = "workers/handlers/"
        val HANDLER_FILES = listOf(
            "ExtractionPipelineHandler.kt",
            "ReflectionPipelineHandler.kt",
            "SuggestionRefreshHandler.kt",
            "SavedItemRegenerationHandler.kt",
            "ReviewTranslationHandler.kt",
            "PreferenceQuickSyncHandler.kt",
        )
    }
}
