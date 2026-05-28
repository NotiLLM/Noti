package org.muilab.notigpt.util

import android.content.Context
import org.muilab.notigpt.util.time.getAbsoluteTimeStr as getAbsoluteTimeStrImpl
import org.muilab.notigpt.util.time.getRelativeTimeStr as getRelativeTimeStrImpl

fun getRelativeTimeStr(unixTime: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    getRelativeTimeStrImpl(unixTime, locale)

fun getAbsoluteTimeStr(unixTime: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    getAbsoluteTimeStrImpl(unixTime, locale)

fun getRelativeTimeStr(
    unixTime: Long,
    context: Context,
    locale: java.util.Locale = java.util.Locale.getDefault()
): String = getRelativeTimeStrImpl(unixTime, context, locale)

fun getAbsoluteTimeStr(
    unixTime: Long,
    context: Context,
    locale: java.util.Locale = java.util.Locale.getDefault()
): String = getAbsoluteTimeStrImpl(unixTime, context, locale)

