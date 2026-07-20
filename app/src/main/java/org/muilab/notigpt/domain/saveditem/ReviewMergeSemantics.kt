package org.muilab.notigpt.domain.saveditem

import org.muilab.notigpt.model.features.SavedSubItem
import java.util.Locale

/** Deterministic, survivor-first subtask union shared by review preview and merge persistence. */
object ReviewMergeSemantics {
    fun appendUnique(target: MutableList<SavedSubItem>, candidate: SavedSubItem): Boolean {
        val text = SavedSubItem.normalizeText(candidate.text)
        if (text.isBlank()) return false
        val key = text.lowercase(Locale.ROOT)
        if (target.any { SavedSubItem.normalizeText(it.text).lowercase(Locale.ROOT) == key }) return false
        target += candidate.copy(text = text, position = target.size)
        return true
    }
}
