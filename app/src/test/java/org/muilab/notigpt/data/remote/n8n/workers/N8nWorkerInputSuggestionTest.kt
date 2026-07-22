package org.muilab.notigpt.data.remote.n8n.workers

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.util.Constants

class N8nWorkerInputSuggestionTest {
    @Test
    fun `parses suggestion refresh input`() {
        val parsed = N8nWorkerInput.from(
            Data.Builder()
                .putString("api_type", Constants.N8N_SUGGESTION_REFRESH)
                .putString("webhook_path", "webhook/suggest-g-shortlist")
                .build(),
        )

        assertTrue(parsed is N8nWorkerInput.SuggestionRefresh)
        parsed as N8nWorkerInput.SuggestionRefresh
        assertEquals("webhook/suggest-g-shortlist", parsed.webhookPath)
    }
}
