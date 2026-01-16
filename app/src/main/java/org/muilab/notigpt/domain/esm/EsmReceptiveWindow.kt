package org.muilab.notigpt.domain.esm

import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.Calendar

object EsmReceptiveWindow {

    /**
     * Returns true if the current local time-of-day is within the user's receptive window.
     *
     * Window definition:
     * - wake <= now < bed when wake < bed.
     * - if wake > bed, window crosses midnight and means: now >= wake OR now < bed.
     */
    fun isNowReceptive(nowMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isMinuteReceptive(nowMin)
    }

    fun isMinuteReceptive(nowMin: Int): Boolean {
        val wake = SharedPreferencesManager.esmWakeupMinutes.coerceIn(0, 24 * 60 - 1)
        val bed = SharedPreferencesManager.esmBedtimeMinutes.coerceIn(0, 24 * 60 - 1)

        return if (wake == bed) {
            // Treat as always receptive.
            true
        } else if (wake < bed) {
            nowMin in wake until bed
        } else {
            // Crosses midnight.
            nowMin >= wake || nowMin < bed
        }
    }

    /**
     * If not receptive now, returns delayMs until the next wake time. If receptive, returns 0.
     */
    fun delayUntilNextReceptive(nowMs: Long = System.currentTimeMillis()): Long {
        if (isNowReceptive(nowMs)) return 0L

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wake = SharedPreferencesManager.esmWakeupMinutes.coerceIn(0, 24 * 60 - 1)

        val minutesUntilWake = if (nowMin <= wake) {
            wake - nowMin
        } else {
            (24 * 60 - nowMin) + wake
        }

        return minutesUntilWake.toLong() * 60_000L
    }
}

