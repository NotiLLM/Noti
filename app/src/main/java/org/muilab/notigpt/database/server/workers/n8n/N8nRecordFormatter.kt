package org.muilab.notigpt.database.server.workers.n8n

import org.muilab.notigpt.model.notifications.NotiRecord

internal object N8nRecordFormatter {

    fun format(record: NotiRecord, isPeople: Boolean): Map<String, Any> {
        val absTime = N8nTimeUtils.isoTime(record.time)
        val title = record.getDisplayedTitle(isPeople)
        val content = record.content
        val rel = N8nTimeUtils.relativeTime(record.time)
        return mapOf(
            "abs_time" to absTime,
            "title" to title,
            "content" to content,
            "rel_time" to rel
        )
    }
}

