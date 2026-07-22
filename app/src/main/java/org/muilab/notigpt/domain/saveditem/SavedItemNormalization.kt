package org.muilab.notigpt.domain.saveditem

import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.TodoStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cross-boundary invariants for SavedItem type changes and legacy payload normalization. */
object SavedItemNormalization {
    data class Result(val item: SavedItem, val steps: List<TodoStep>)

    fun normalize(item: SavedItem, steps: List<TodoStep>): Result {
        val normalized = steps.mapIndexedNotNull { index, child ->
            TodoStep.normalizeText(child.text).takeIf(String::isNotBlank)?.let { text ->
                child.copy(text = text, position = index)
            }
        }
        return if (item.isTodo) Result(item, normalized) else {
            Result(
                item.copy(
                    content = appendSteps(item.content, normalized),
                    deadlineAtMs = 0L,
                ),
                emptyList(),
            )
        }
    }

    fun convertTodoToKeep(item: SavedItem, steps: List<TodoStep>): Result {
        val withDeadline = appendDeadline(item.content, item.deadlineAtMs)
        return Result(
            item.copy(
                content = appendSteps(withDeadline, steps),
                itemType = org.muilab.notigpt.model.features.SavedItemType.Keep,
                state = org.muilab.notigpt.model.features.SavedItemState.Saved,
                deadlineAtMs = 0L,
            ),
            emptyList(),
        )
    }

    fun mergeButtons(vararg buttonJson: String): String = SavedItemActionButtons.mergeJson(*buttonJson)

    private fun appendDeadline(content: String, deadlineAtMs: Long): String {
        if (deadlineAtMs <= 0L) return content
        val zh = Locale.getDefault().language.startsWith("zh")
        val heading = if (zh) "截止時間" else "Deadline"
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(deadlineAtMs))
        return appendBlock(content, "$heading: $formatted")
    }

    private fun appendSteps(content: String, steps: List<TodoStep>): String {
        if (steps.isEmpty()) return content
        val heading = if (Locale.getDefault().language.startsWith("zh")) "步驟" else "Steps"
        val lines = steps.mapNotNull { child ->
            TodoStep.normalizeText(child.text).takeIf(String::isNotBlank)?.let { text ->
                (if (child.isCompleted) "☑ " else "☐ ") + text
            }
        }
        return if (lines.isEmpty()) content else appendBlock(content, "$heading:\n${lines.joinToString("\n")}")
    }

    private fun appendBlock(content: String, block: String): String =
        listOf(content.trim(), block.trim()).filter(String::isNotBlank).joinToString("\n\n")
}
