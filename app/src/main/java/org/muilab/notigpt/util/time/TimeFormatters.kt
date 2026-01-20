package org.muilab.notigpt.util.time

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import org.muilab.notigpt.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Relative time string for UI.
 *
 * - Uses system locale by default.
 * - Supports both past and future timestamps.
 */
fun getRelativeTimeStr(unixTime: Long, locale: Locale = Locale.getDefault()): String {
    // Backwards-compatible overload used in non-UI code paths.
    // For localized strings (now/tomorrow/today), prefer the Context overload.
    return getRelativeTimeStr(unixTime, null, locale)
}

fun getRelativeTimeStr(unixTime: Long, context: Context?, locale: Locale = Locale.getDefault()): String {
    val now = System.currentTimeMillis()
    val diffInMillis = unixTime - now

    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))

    val isFuture = diffInMillis > 0
    val direction = if (isFuture) {
        RelativeDateTimeFormatter.Direction.NEXT
    } else {
        RelativeDateTimeFormatter.Direction.LAST
    }

    val diffAbsMillis = abs(diffInMillis)
    val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffAbsMillis)
    val diffInHours = TimeUnit.MILLISECONDS.toHours(diffAbsMillis)

    // Use resource patterns when available; otherwise fall back to plain patterns.
    val timePattern = context?.getString(R.string.time_pattern_time) ?: "HH:mm"
    val monthDayPattern = context?.getString(R.string.time_pattern_month_day) ?: "M/d"

    val timeFormat = SimpleDateFormat(timePattern, locale)

    // Localized day labels:
    // - Prefer resources.
    // - If context is null, use hard fallback tokens (EN) because ICU's day formatting
    //   for +/-1 day often produces numeric forms like "1 day ago" / "1天前".
    //   We explicitly want "Yesterday" / "昨天" and "Tomorrow" / "明天".
    fun s(id: Int, fallback: String): String = context?.getString(id) ?: fallback

    fun sf(id: Int, vararg args: Any): String? = context?.getString(id, *args)

    // Accurate day-delta across midnight/year boundaries.
    fun dayDeltaOf(unixTimeMillis: Long): Int {
        val calNow = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calInput = Calendar.getInstance().apply {
            timeInMillis = unixTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((calInput.timeInMillis - calNow.timeInMillis) / TimeUnit.DAYS.toMillis(1)).toInt()
        return diffDays
    }

    return when {
        diffAbsMillis < TimeUnit.MINUTES.toMillis(1) -> {
            val soon = context?.getString(R.string.time_now)
                ?: formatter.format(1.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.MINUTES).toString()
            val nowStr = context?.getString(R.string.time_now) ?: "now"
            if (isFuture) soon else nowStr
        }

        diffInMinutes < 60 -> formatter.format(
            diffInMinutes.toDouble(),
            direction,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES
        ).toString()

        diffInHours < 3 -> formatter.format(
            diffInHours.toDouble(),
            direction,
            RelativeDateTimeFormatter.RelativeUnit.HOURS
        ).toString()

        diffAbsMillis < TimeUnit.DAYS.toMillis(2) -> {
            // Within about 48h: show Today/Yesterday/Tomorrow + time when applicable.
            val dayDelta = dayDeltaOf(unixTime)
            val t = timeFormat.format(Date(unixTime))

            when (dayDelta) {
                1 -> {
                    // Tomorrow
                    sf(R.string.time_tomorrow_hhmm, t) ?: (s(R.string.time_tomorrow, "Tomorrow") + " " + t)
                }

                -1 -> {
                    // Yesterday
                    sf(R.string.time_yesterday_hhmm, t) ?: (s(R.string.time_yesterday, "Yesterday") + " " + t)
                }

                0 -> {
                    // Today
                    sf(R.string.time_today_hhmm, t) ?: (s(R.string.time_today, "Today") + " " + t)
                }

                else -> t
            }
        }

        // Older than ~48 hours: keep previous behavior.
        abs(dayDeltaOf(unixTime)) < 7 -> SimpleDateFormat("EEEE", locale).format(Date(unixTime))

        else -> SimpleDateFormat(monthDayPattern, locale).format(Date(unixTime))
    }
}

fun getAbsoluteTimeStr(unixTime: Long, locale: Locale = Locale.getDefault()): String {
    return getAbsoluteTimeStr(unixTime, null, locale)
}

fun getAbsoluteTimeStr(unixTime: Long, context: Context?, locale: Locale = Locale.getDefault()): String {
    val pattern = context?.getString(R.string.time_pattern_month_day_time) ?: "M/d HH:mm"
    val dateFormat = SimpleDateFormat(pattern, locale)
    return dateFormat.format(Date(unixTime))
}
