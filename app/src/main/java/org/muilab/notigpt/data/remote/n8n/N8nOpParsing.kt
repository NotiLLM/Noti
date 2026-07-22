package org.muilab.notigpt.data.remote.n8n

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.features.TodoStep
import java.time.OffsetDateTime

/**
 * Field-level parsing shared by everything that reads pipeline op payloads: the extraction
 * pipeline handler (staging), the pending-op apply path, and the single-item regeneration
 * handler. Keeps timestamp sentinels in one place.
 */
object N8nOpParsing {

    fun savedItemIdFrom(obj: JSONObject): String = obj.optString("savedItemId")

    fun titleFrom(obj: JSONObject, fallback: String = ""): String = obj.optString("title", fallback)

    fun contentFrom(obj: JSONObject, fallback: String = ""): String = obj.optString("content", fallback)

    /**
     * Converts an offset-qualified deadline into epoch millis.
     *
     * Keep the no-deadline sentinel handling here so response parsing does not scatter timestamp
     * edge cases across SavedItem construction code.
     */
    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1" || iso.isBlank()) return -1L // no-deadline sentinel from the n8n response schema

        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Builds sub-item rows from an op's `steps`/`addedSteps` array. Ids are generated
     * locally when the payload doesn't carry one — creates never name their own ids.
     */
    fun parseSteps(arr: JSONArray?, parentSavedItemId: String, ts: Long, baseSortOrder: Int): List<TodoStep> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONObject(i) ?: continue
                val id = sub.optString("todoStepId")
                    .ifBlank { "st_" + java.util.UUID.randomUUID().toString().take(8) }
                val text = TodoStep.normalizeText(sub.optString("text"))
                if (text.isNotBlank()) {
                    add(
                        TodoStep(
                            todoStepId = id,
                            parentSavedItemId = parentSavedItemId,
                            text = text,
                            isCompleted = sub.optBoolean("isCompleted", false),
                            position = sub.optInt("position", baseSortOrder + i),
                        )
                    )
                }
            }
        }
    }

    /** Child actions cannot own buttons; this remains a defensive empty extractor. */
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
