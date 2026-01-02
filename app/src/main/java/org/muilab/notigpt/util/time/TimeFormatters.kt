package org.muilab.notigpt.util.time

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

fun getRelativeTimeStr(unixTime: Long, locale: Locale = Locale("zh", "TW")): String {
    val now = System.currentTimeMillis()
    val diffInMillis = now - unixTime
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))

    val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(abs(diffInMillis))
    val diffInHours = TimeUnit.MILLISECONDS.toHours(abs(diffInMillis))
    val diffInDays = TimeUnit.MILLISECONDS.toDays(abs(diffInMillis))

    return when {
        diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "現在"
        diffInMinutes < 60 -> formatter.format(
            diffInMinutes.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES
        ).toString()

        diffInHours < 3 -> formatter.format(
            diffInHours.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.HOURS
        ).toString()

        diffInHours < 24 -> {
            val calNow = Calendar.getInstance()
            val calInput = Calendar.getInstance().apply { timeInMillis = unixTime }
            val dateFormat = if (calNow.get(Calendar.DATE) - calInput.get(Calendar.DATE) == 1) {
                SimpleDateFormat("'昨天' HH:mm", locale)
            } else {
                SimpleDateFormat("HH:mm", locale)
            }
            dateFormat.format(Date(unixTime))
        }

        diffInDays == 1L -> "昨天"
        diffInDays < 7 -> SimpleDateFormat("EEEE", locale).format(Date(unixTime))
        else -> SimpleDateFormat("M'月' d'日'", Locale.getDefault()).format(Date(unixTime))
    }
}

fun getAbsoluteTimeStr(unixTime: Long, locale: Locale = Locale("zh", "TW")): String {
    val dateFormat = SimpleDateFormat("M'月' d'日' HH:mm", Locale.getDefault())
    return dateFormat.format(Date(unixTime))
}

