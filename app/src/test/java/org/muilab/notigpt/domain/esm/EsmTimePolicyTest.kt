package org.muilab.notigpt.domain.esm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EsmTimePolicyTest {

    @Test
    fun `anchoredDayKey uses previous day when before boundary`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 14)
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val key = EsmTimePolicy.anchoredDayKey(cal.timeInMillis, startHour = 9, startMinute = 0)
        // 2026-01-14 01:00 belongs to anchored day starting 2026-01-13 09:00
        assertEquals("20260113", key)
    }

    @Test
    fun `anchoredDayKey uses same day when after boundary`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 14)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val key = EsmTimePolicy.anchoredDayKey(cal.timeInMillis, startHour = 9, startMinute = 0)
        assertEquals("20260114", key)
    }

    @Test
    fun `day window validates min span across midnight`() {
        val w = EsmTimePolicy.DayWindow(9, 0, 2, 0)
        assertTrue(w.validateMinSpanHours(12))
    }
}

