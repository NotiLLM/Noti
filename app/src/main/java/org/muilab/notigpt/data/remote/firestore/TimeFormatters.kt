package org.muilab.notigpt.data.remote.firestore

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Firestore-facing timestamp formatting helpers.
 *
 * Keep remote serialization formatting here. User-facing time strings belong in UI utility formatters so sync
 * payloads and display text can evolve independently.
 */
internal object TimeFormatters {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toLocalIso(ms: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return isoFormatter.format(Instant.ofEpochMilli(ms).atZone(zoneId))
    }
}

