package org.muilab.notigpt.util

import android.content.Context
import org.muilab.notigpt.util.time.getAbsoluteTimeStr as getAbsoluteTimeStrImpl
import org.muilab.notigpt.util.time.getRelativeTimeStr as getRelativeTimeStrImpl

/**
 * User-facing relative and absolute time formatting helpers.
 *
 * Keep display strings here and remote/database timestamp serialization elsewhere so UI language choices do not
 * leak into sync payloads.
 */
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

