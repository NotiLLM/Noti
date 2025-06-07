package org.muilab.notigpt.model.notifications

import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr

class NotiDisplayUnit (
    val notiUnit: NotiUnit,
    val notiRecords: List<NotiRecord>,
) {
    val notiKey: String
        get() = notiUnit.notiKey
    val title: String
        get() {
            return notiRecords.lastOrNull()?.title ?: ""
        }
    val category: String
        get() = notiUnit.category
    val sortScore: Double
        get() = notiUnit.sortScore
    val lastUpdateTime: Long
        get() = notiRecords.last().time ?: 0L
    val latestUpdateRelTimeStr: String
        get() = getRelativeTimeStr(lastUpdateTime)

    fun toDifyNoti(timeDiff: Long = System.currentTimeMillis()): Map<String, Any>? {

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime > timeDiff)
            return null

        val currentBody = notiRecords
            .filter { it.isVisible }
            .map {
                mapOf<String, Any>(
                    "title" to it.getDisplayedTitle(notiUnit.isPeople),
                    "abs_time" to getAbsoluteTimeStr(it.time),
                    "rel_time" to getRelativeTimeStr(it.time),
                    "content" to it.content
                )
            }

        val prevBody = notiRecords
            .filter { !it.isVisible }
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
            "title" to title,
            "app_name" to notiUnit.appName,
            "body" to currentBody,
            "history_body" to prevBody,
        )
    }
}