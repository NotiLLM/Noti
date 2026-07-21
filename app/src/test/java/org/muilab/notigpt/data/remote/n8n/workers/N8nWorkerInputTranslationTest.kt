package org.muilab.notigpt.data.remote.n8n.workers

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.util.Constants

class N8nWorkerInputTranslationTest {
    @Test
    fun parsesReviewTranslationInput() {
        val parsed = N8nWorkerInput.from(
            Data.Builder()
                .putString("api_type", Constants.N8N_REVIEW_TRANSLATION)
                .putString("webhook_path", "webhook/extract-f-translation")
                .putString("review_key", "create_7")
                .build(),
        )

        assertTrue(parsed is N8nWorkerInput.ReviewTranslation)
        parsed as N8nWorkerInput.ReviewTranslation
        assertEquals("create_7", parsed.reviewKey)
        assertEquals("webhook/extract-f-translation", parsed.webhookPath)
    }
}
