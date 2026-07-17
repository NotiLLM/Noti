package org.muilab.notigpt.data.remote.n8n

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.features.SavedSubItem
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Field-level parsing shared by everything that reads pipeline op payloads: the extraction
 * pipeline handler (staging), the pending-op apply path, and the single-item regeneration
 * handler. Keeps timestamp sentinels and legacy field aliases in one place.
 */
object N8nOpParsing {

    /** Reads SavedItem IDs from the current schema and legacy n8n aliases. */
    fun savedItemIdFrom(obj: JSONObject): String = obj.optString(
        "savedItemId",
        obj.optString("reminderId", obj.optString("taskId")),
    )

    fun titleFrom(obj: JSONObject, fallback: String = ""): String = obj.optString(
        "title",
        obj.optString("reminderTitle", fallback),
    )

    fun contentFrom(obj: JSONObject, fallback: String = ""): String = obj.optString(
        "content",
        obj.optString("reminderContent", obj.optString("taskDescription", fallback)),
    )

    fun startAtMsFrom(obj: JSONObject): Long = isoToUnixMillis(
        obj.optString("startAtMsString", obj.optString("startTimeString", "-1")),
    ).let { v -> if (v == -1L) 0L else v }

    fun endAtMsFrom(obj: JSONObject): Long = isoToUnixMillis(
        obj.optString("endAtMsString", obj.optString("endTimeString", "-1")),
    ).let { v -> if (v == -1L) 0L else v }

    /**
     * Converts n8n deadline/start/end timestamps into local epoch millis.
     *
     * Keep the no-deadline sentinel handling here so response parsing does not scatter timestamp
     * edge cases across SavedItem construction code.
     */
    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1" || iso.isBlank()) return -1L // no-deadline sentinel from the n8n response schema

        // Most common: has an offset like +08:00 or ends with Z
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                // Some n8n responses omit the offset (for example, 2026-08-01T17:00:00).
                // Treat those values as local device time, matching the timezone used when
                // Android serializes timestamps for n8n.
                LocalDateTime.parse(iso)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                // Fallback: sometimes it's a full zone format or slightly different ISO variant
                ZonedDateTime.parse(iso).toInstant().toEpochMilli()
            }
        }
    }

    /**
     * Builds sub-item rows from an op's `subTasks`/`addedSubTasks` array. Ids are generated
     * locally when the payload doesn't carry one — creates never name their own ids.
     */
    fun parseSubTasks(arr: JSONArray?, parentSavedItemId: String, ts: Long, baseSortOrder: Int): List<SavedSubItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONObject(i) ?: continue
                val id = sub.optString("savedSubItemId", sub.optString("subTaskId"))
                    .ifBlank { "st_" + java.util.UUID.randomUUID().toString().take(8) }
                val title = sub.optString("text", sub.optString("title", ""))
                val description = sub.optString("description", sub.optString("content", ""))
                val text = SavedSubItem.normalizeText(
                    listOf(title, description).filter(String::isNotBlank).distinct().joinToString(" — ")
                )
                if (text.isNotBlank()) {
                    add(
                        SavedSubItem(
                            savedSubItemId = id,
                            parentSavedItemId = parentSavedItemId,
                            text = text,
                            isCompleted = sub.optBoolean("isCompleted", false),
                            position = sub.optInt("position", sub.optInt("sortOrder", baseSortOrder + i)),
                        )
                    )
                }
            }
        }
    }

    /** Legacy child actions are parent-owned in the current contract. */
    fun childButtons(arr: JSONArray?): String {
        if (arr == null) return "[]"
        val merged = JSONArray()
        for (index in 0 until arr.length()) {
            val buttons = arr.optJSONObject(index)?.optJSONArray("buttons") ?: continue
            for (buttonIndex in 0 until buttons.length()) {
                buttons.optJSONObject(buttonIndex)?.let(merged::put)
            }
        }
        return merged.toString()
    }
}
