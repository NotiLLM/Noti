package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class N8nOpParsingTest {

    @Test
    fun `rejects offset-less n8n timestamp`() {
        assertEquals(-1L, N8nOpParsing.isoToUnixMillis("2026-08-01T17:00:00"))
    }

    @Test
    fun `preserves explicit n8n timestamp offset`() {
        val expected = ZonedDateTime.parse("2026-08-01T17:00:00+08:00").toInstant().toEpochMilli()

        assertEquals(expected, N8nOpParsing.isoToUnixMillis("2026-08-01T17:00:00+08:00"))
    }
}
