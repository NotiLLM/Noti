package org.muilab.notigpt.data.remote.n8n.formatter

import org.muilab.notigpt.model.notifications.NotiRecord

/**
 * Formatter for serializing NotiRecord context into n8n request payloads.
 *
 * Keep backend-facing field names and text selection here so SavedItem/update handlers do not each invent their
 * own record schema. Model parsing stays in NotiRecord and NotiMetadata.
 */
internal object N8nRecordFormatter {

    fun format(record: NotiRecord, isPeople: Boolean): Map<String, Any> {
        val absTime = N8nTimeFormatter.isoTime(record.time)
        val title = record.getDisplayedTitle(isPeople)
        val content = record.content
        val rel = N8nTimeFormatter.relativeTime(record.time)
        return mapOf(
            "record_id" to record.notiRecordId,
            "abs_time" to absTime,
            "title" to title,
            "content" to content,
            "rel_time" to rel
        )
    }
}
