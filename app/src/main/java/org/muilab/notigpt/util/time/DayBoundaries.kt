package org.muilab.notigpt.util.time

import java.util.Calendar

/** Local calendar-day boundaries, shared by planned-date smart filters and their counts. */
object DayBoundaries {

    /**
     * Local start-of-tomorrow in epoch millis: the exclusive upper bound of "today & earlier".
     * A planned date `< startOfTomorrow` is today or overdue; `>=` is upcoming.
     */
    fun startOfTomorrowMs(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}
