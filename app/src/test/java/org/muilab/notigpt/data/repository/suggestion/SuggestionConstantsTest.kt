package org.muilab.notigpt.data.repository.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionConstantsTest {
    @Test
    fun `product limits stay explicit and centralized`() {
        assertEquals(6L * 60L * 60L * 1_000L, SuggestionConstants.REFRESH_INTERVAL_MS)
        assertEquals(25, SuggestionConstants.G_SKIP_AT_OR_BELOW_ITEM_COUNT)
        assertEquals(25, SuggestionConstants.G_MAX_CANDIDATES)
        assertEquals(10, SuggestionConstants.H_MAX_SUGGESTIONS)
    }
}
