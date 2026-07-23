package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageLoggerTest {

    @Test
    fun `parses every field from a well-formed header`() {
        val fields = UsageLogger.parseHeader(
            "model=gemini-2.5-flash;input=120;output=30;thinking=0;total=150",
        )
        assertEquals("gemini-2.5-flash", fields.model)
        assertEquals(120L, fields.inputTokens)
        assertEquals(30L, fields.outputTokens)
        assertEquals(0L, fields.thinkingTokens)
        assertEquals(150L, fields.totalTokens)
    }

    @Test
    fun `missing fields default to zero and model defaults to unknown`() {
        val fields = UsageLogger.parseHeader("")
        assertEquals("unknown", fields.model)
        assertEquals(0L, fields.inputTokens)
        assertEquals(0L, fields.outputTokens)
        assertEquals(0L, fields.thinkingTokens)
        assertEquals(0L, fields.totalTokens)
    }

    @Test
    fun `non-numeric token values default to zero rather than crashing`() {
        val fields = UsageLogger.parseHeader("model=gemini-2.5-flash;input=not-a-number;output=30")
        assertEquals(0L, fields.inputTokens)
        assertEquals(30L, fields.outputTokens)
    }
}
