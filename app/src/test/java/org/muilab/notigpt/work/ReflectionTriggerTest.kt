package org.muilab.notigpt.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionTriggerTest {
    @Test
    fun `daily interval triggers reflection`() {
        assertTrue(
            ReflectionTrigger.shouldEnqueue(
                now = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS,
                lastSuccess = 0L,
                lastAttempt = 0L,
                dirtyCount = 0,
            ),
        )
    }

    @Test
    fun `item changes restart one three minute trailing request`() {
        val first = ReflectionTrigger.changeDrivenRequest()
        val repeated = ReflectionTrigger.changeDrivenRequest()

        assertEquals(3 * 60_000L, first.delayMs)
        assertTrue(first.replaceExisting)
        assertEquals(first, repeated)
    }

    @Test
    fun `recent attempt cannot mask daily safety net`() {
        assertTrue(
            ReflectionTrigger.shouldEnqueue(
                now = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS,
                lastSuccess = 0L,
                lastAttempt = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS,
                dirtyCount = 0,
            ),
        )
    }

    @Test
    fun `daily safety net stays idle before twenty four hours`() {
        assertFalse(
            ReflectionTrigger.shouldEnqueue(
                now = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS - 1L,
                lastSuccess = 0L,
                lastAttempt = 0L,
                dirtyCount = 100,
            ),
        )
    }
}
