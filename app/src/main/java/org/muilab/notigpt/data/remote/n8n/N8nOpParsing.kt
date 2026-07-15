package org.muilab.notigpt.data.remote.n8n

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Field-level parsing shared by everything that reads pipeline op payloads: the extraction
 * pipeline handler (staging), the pending-op apply path, and the single-item regeneration
 * handler. Keeps timestamp sentinels and legacy field aliases in one place.
 */
object N8nOpParsing {

    /** Reads reminder IDs from both the current app schema and older n8n schemas. */
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
     * edge cases across reminder construction code.
     */
    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1" || iso.isBlank()) return -1L // no-deadline sentinel from the n8n response schema

        // Most common: has an offset like +08:00 or ends with Z
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            // Fallback: sometimes it's a full zone format or slightly different ISO variant
            ZonedDateTime.parse(iso).toInstant().toEpochMilli()
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
                add(
                    SavedSubItem(
                        savedSubItemId = id,
                        parentSavedItemId = parentSavedItemId,
                        title = sub.optString("title", ""),
                        description = sub.optString("description", sub.optString("content", "")),
                        itemType = if (sub.optBoolean("isTask", true)) SavedItemType.Task else SavedItemType.Keep,
                        isCompleted = sub.optBoolean("isCompleted", false),
                        deadlineAtMs = isoToUnixMillis(sub.optString("deadlineTimeString", "-1")),
                        startAtMs = startAtMsFrom(sub),
                        endAtMs = endAtMsFrom(sub),
                        buttons = sub.optJSONArray("buttons")?.toString() ?: "[]",
                        sortOrder = sub.optInt("sortOrder", baseSortOrder + i),
                        createdAt = ts,
                        lastUpdateTimestamp = ts,
                        isVisible = true,
                    )
                )
            }
        }
    }
}
