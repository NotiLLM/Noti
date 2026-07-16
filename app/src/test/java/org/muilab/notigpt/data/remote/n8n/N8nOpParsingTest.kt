package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class N8nOpParsingTest {

    @Test
    fun `parses offset-less n8n timestamp in device timezone`() {
        val originalTimezone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))

            val expected = ZonedDateTime.of(
                2026, 8, 1, 17, 0, 0, 0, ZoneId.of("Asia/Taipei")
            ).toInstant().toEpochMilli()

            assertEquals(expected, N8nOpParsing.isoToUnixMillis("2026-08-01T17:00:00"))
        } finally {
            TimeZone.setDefault(originalTimezone)
        }
    }

    @Test
    fun `preserves explicit n8n timestamp offset`() {
        val expected = ZonedDateTime.parse("2026-08-01T17:00:00+08:00").toInstant().toEpochMilli()

        assertEquals(expected, N8nOpParsing.isoToUnixMillis("2026-08-01T17:00:00+08:00"))
    }
}
