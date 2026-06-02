package org.muilab.notigpt.data.remote.n8n.mapper

import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr

/**
 * Mapping helpers from local notification models to n8n request payload fragments.
 *
 * Keep payload shape decisions here so worker enqueue/handler code does not duplicate notification serialization
 * rules. Returns null from record serialization when a notification has no content context.
 */
fun toN8nNotiRecords(notiUnit: NotiUnit, notiRecords: List<NotiRecord>): Map<String, Any>? {

    val currentBody = notiRecords
        .map {
            mapOf<String, Any>(
                "title" to it.getDisplayedTitle(notiUnit.isPeople),
                "abs_time" to getAbsoluteTimeStr(it.time),
                "rel_time" to getRelativeTimeStr(it.time),
                "content" to it.content
            )
        }

    return mapOf<String, Any>(
        "noti-key" to notiUnit.notiKey,
        "title" to notiRecords.last().getDisplayedTitle(notiUnit.isPeople),
        "app_name" to notiUnit.appName,
        "body" to currentBody,
    )
}

fun toN8nNotiActions(notiActions: List<NotiAction>): List<Map<String, Any>> {
    return notiActions
        .map {
            mapOf<String, Any>(
                "abs_time" to getAbsoluteTimeStr(it.time),
                "rel_time" to getRelativeTimeStr(it.time),
                "action_type" to it.actionType,
            )
        }
}