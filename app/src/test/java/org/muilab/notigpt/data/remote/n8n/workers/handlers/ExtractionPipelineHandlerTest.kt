package org.muilab.notigpt.data.remote.n8n.workers.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.muilab.notigpt.data.repository.notification.NotiActionsRepository
import org.muilab.notigpt.util.SharedPreferencesManager

class ExtractionPipelineHandlerTest {

    @Test
    fun `capture waits two minutes below ten records and fires immediately at ten`() {
        assertEquals(120_000L, NotiActionsRepository.captureDelayMs(accumulatedRecords = 9))
        assertEquals(0L, NotiActionsRepository.captureDelayMs(accumulatedRecords = 10))
    }

    @Test
    fun `legacy stored timing variants cannot change capture policy`() {
        SharedPreferencesManager.waitSecondsBeforeNotiUnitSync = 999
        SharedPreferencesManager.maxRecordsBeforeNotiSync = 999

        assertEquals(120, SharedPreferencesManager.waitSecondsBeforeNotiUnitSync)
        assertEquals(10, SharedPreferencesManager.maxRecordsBeforeNotiSync)
    }

    @Test
    fun `automatic Stage B remains gated for five minutes`() {
        assertFalse(
            ExtractionPipelineHandler.canRunAutomaticStageB(
                now = 299_999L,
                lastItemExtractionAt = 0L,
            ),
        )
        assertTrue(
            ExtractionPipelineHandler.canRunAutomaticStageB(
                now = 300_000L,
                lastItemExtractionAt = 0L,
            ),
        )
    }

    @Test
    fun `A rejection compacts at fifty records but not forty nine`() {
        assertFalse(
            ExtractionPipelineHandler.shouldCompact(
                recordCount = 49,
                newestPostTime = 1_000L,
                now = 1_000L,
                quietWindowMinutes = 720,
            ),
        )
        assertTrue(
            ExtractionPipelineHandler.shouldCompact(
                recordCount = 50,
                newestPostTime = 1_000L,
                now = 1_000L,
                quietWindowMinutes = 720,
            ),
        )
    }

    @Test
    fun `quiet fallback still compacts below fifty records`() {
        assertTrue(
            ExtractionPipelineHandler.shouldCompact(
                recordCount = 1,
                newestPostTime = 0L,
                now = 12 * 60_000L,
                quietWindowMinutes = 12,
            ),
        )
        assertFalse(
            ExtractionPipelineHandler.shouldCompact(
                recordCount = 49,
                newestPostTime = 0L,
                now = Long.MAX_VALUE,
                quietWindowMinutes = -1,
            ),
        )
    }
}
