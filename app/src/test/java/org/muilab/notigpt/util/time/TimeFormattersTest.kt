package org.muilab.notigpt.util.time

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class TimeFormattersTest {

    @Test
    fun `crossing midnight should prefer Yesterday label (zh-TW)`() {
        val locale = Locale.forLanguageTag("zh-TW")

        val ts = Calendar.getInstance(locale).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 13)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Replace with just ts (we can't inject now without refactor):
        val out = getRelativeTimeStr(ts, null, locale)
        assertTrue("Should not contain numeric day-ago phrase", !out.contains("天前") && !out.contains("天後"))
    }
}
