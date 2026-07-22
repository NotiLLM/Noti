package org.muilab.notigpt.util.time

import java.util.Calendar

/** Product-level time windows shared by the home tiles and their item lists. */
object SmartFilterWindows {
    const val DUE_SOON_DAYS = 7
    const val RECENTLY_UPDATED_WINDOW_MS = 24L * 60L * 60L * 1_000L

    /** Exclusive end of today plus the next [DUE_SOON_DAYS] local calendar days. */
    fun dueSoonEndExclusiveMs(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, DUE_SOON_DAYS + 1)
        }.timeInMillis
}
