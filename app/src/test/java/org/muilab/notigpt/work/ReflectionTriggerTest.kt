package org.muilab.notigpt.work

import org.junit.Assert.assertFalse
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
    fun `five dirty items trigger after minimum interval`() {
        assertTrue(
            ReflectionTrigger.shouldEnqueue(
                now = ReflectionTrigger.REFLECTION_MIN_INTERVAL_MS,
                lastSuccess = 0L,
                lastAttempt = 0L,
                dirtyCount = ReflectionTrigger.REFLECTION_DIRTY_ITEM_THRESHOLD,
            ),
        )
    }

    @Test
    fun `minimum interval debounces both trigger paths`() {
        assertFalse(
            ReflectionTrigger.shouldEnqueue(
                now = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS,
                lastSuccess = 0L,
                lastAttempt = ReflectionTrigger.REFLECTION_MAX_INTERVAL_MS - 1L,
                dirtyCount = ReflectionTrigger.REFLECTION_DIRTY_ITEM_THRESHOLD,
            ),
        )
    }
}
