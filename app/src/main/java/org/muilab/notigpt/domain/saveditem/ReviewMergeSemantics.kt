package org.muilab.notigpt.domain.saveditem

import org.muilab.notigpt.model.features.TodoStep
import java.util.Locale

/** Deterministic, survivor-first step union shared by review preview and merge persistence. */
object ReviewMergeSemantics {
    fun appendUnique(target: MutableList<TodoStep>, candidate: TodoStep): Boolean {
        val text = TodoStep.normalizeText(candidate.text)
        if (text.isBlank()) return false
        val key = text.lowercase(Locale.ROOT)
        if (target.any { TodoStep.normalizeText(it.text).lowercase(Locale.ROOT) == key }) return false
        target += candidate.copy(text = text, position = target.size)
        return true
    }
}
