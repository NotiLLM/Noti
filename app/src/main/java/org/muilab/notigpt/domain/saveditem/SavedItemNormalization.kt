package org.muilab.notigpt.domain.saveditem

import org.json.JSONArray
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cross-boundary invariants for SavedItem type changes and legacy payload normalization. */
object SavedItemNormalization {
    data class Result(val item: SavedItem, val subItems: List<SavedSubItem>)

    fun normalize(item: SavedItem, subItems: List<SavedSubItem>): Result {
        val normalized = subItems.mapIndexedNotNull { index, child ->
            SavedSubItem.normalizeText(child.text).takeIf(String::isNotBlank)?.let { text ->
                child.copy(text = text, position = index)
            }
        }
        return if (item.isTask) Result(item, normalized) else {
            Result(
                item.copy(
                    content = appendSubtasks(item.content, normalized),
                    deadlineAtMs = 0L,
                ),
                emptyList(),
            )
        }
    }

    fun convertTaskToKeep(item: SavedItem, subItems: List<SavedSubItem>): Result {
        val withDeadline = appendDeadline(item.content, item.deadlineAtMs)
        return Result(
            item.copy(
                content = appendSubtasks(withDeadline, subItems),
                itemType = org.muilab.notigpt.model.features.SavedItemType.Keep,
                state = org.muilab.notigpt.model.features.SavedItemState.Saved,
                deadlineAtMs = 0L,
            ),
            emptyList(),
        )
    }

    fun mergeButtons(vararg buttonJson: String): String {
        val out = JSONArray()
        val seen = linkedSetOf<String>()
        buttonJson.forEach { raw ->
            val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            for (index in 0 until arr.length()) {
                val button = arr.optJSONObject(index) ?: continue
                val type = button.optString("type", "link")
                val intent = button.optString("intent", "")
                if (intent.isBlank() || !seen.add("$type\u0000$intent")) continue
                out.put(button)
            }
        }
        return out.toString()
    }

    private fun appendDeadline(content: String, deadlineAtMs: Long): String {
        if (deadlineAtMs <= 0L) return content
        val zh = Locale.getDefault().language.startsWith("zh")
        val heading = if (zh) "截止時間" else "Deadline"
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(deadlineAtMs))
        return appendBlock(content, "$heading: $formatted")
    }

    private fun appendSubtasks(content: String, subItems: List<SavedSubItem>): String {
        if (subItems.isEmpty()) return content
        val heading = if (Locale.getDefault().language.startsWith("zh")) "子任務" else "Subtasks"
        val lines = subItems.mapNotNull { child ->
            SavedSubItem.normalizeText(child.text).takeIf(String::isNotBlank)?.let { text ->
                (if (child.isCompleted) "☑ " else "☐ ") + text
            }
        }
        return if (lines.isEmpty()) content else appendBlock(content, "$heading:\n${lines.joinToString("\n")}")
    }

    private fun appendBlock(content: String, block: String): String =
        listOf(content.trim(), block.trim()).filter(String::isNotBlank).joinToString("\n\n")
}
