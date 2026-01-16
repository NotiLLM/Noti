package org.muilab.notigpt.domain.esm

import java.util.Calendar
import kotlin.math.abs

/**
 * ESM timing policy helpers.
 *
 * Current defaults (per your spec):
 * - delay: 10 min
 * - expiry: 1 hour
 * - cooldown: answered=1 hour, unanswered=30 min (tracked later)
 * - cap: 8 per anchored day (tracked later)
 *
 * Anchored day: day boundary is [startHour:startMinute]. If the current wall clock time is before
 * that boundary, it belongs to the previous anchored day.
 */
object EsmTimePolicy {

    data class DayWindow(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
    ) {
        init {
            require(startHour in 0..23)
            require(endHour in 0..23)
            require(startMinute in 0..59)
            require(endMinute in 0..59)
        }

        /** Must span at least [minHours] hours. Allows crossing midnight. */
        fun validateMinSpanHours(minHours: Int = 12): Boolean {
            val start = startHour * 60 + startMinute
            val end = endHour * 60 + endMinute
            val span = if (end >= start) end - start else (24 * 60 - start) + end
            return span >= minHours * 60
        }

        fun contains(cal: Calendar): Boolean {
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val start = startHour * 60 + startMinute
            val end = endHour * 60 + endMinute
            return if (end >= start) {
                m in start until end
            } else {
                // crosses midnight
                m >= start || m < end
            }
        }
    }

    /**
     * Returns an anchored day key like YYYYMMDD for the day starting at [startHour:startMinute].
     */
    fun anchoredDayKey(nowMs: Long, startHour: Int, startMinute: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val boundary = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If now is before today's boundary, shift to yesterday.
        val anchored = if (cal.before(boundary)) {
            Calendar.getInstance().apply {
                timeInMillis = boundary.timeInMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            boundary
        }

        val y = anchored.get(Calendar.YEAR)
        val m = anchored.get(Calendar.MONTH) + 1
        val d = anchored.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d%02d%02d", y, m, d)
    }

    data class AnchoredDayWindowMs(val startMs: Long, val endMs: Long)

    /**
     * Returns the [startMs, endMs] window of the anchored day containing [nowMs].
     * The window starts at [startHour:startMinute] and spans 24 hours.
     */
    fun anchoredDayWindowMs(nowMs: Long, startHour: Int, startMinute: Int): AnchoredDayWindowMs {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val boundary = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = if (cal.before(boundary)) {
            Calendar.getInstance().apply {
                timeInMillis = boundary.timeInMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }
        } else boundary

        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }

        return AnchoredDayWindowMs(startMs = start.timeInMillis, endMs = end.timeInMillis)
    }
}
