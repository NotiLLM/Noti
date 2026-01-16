package org.muilab.notigpt.repository.firestore

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object TimeFormatters {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toLocalIso(ms: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return isoFormatter.format(Instant.ofEpochMilli(ms).atZone(zoneId))
    }
}

