package org.muilab.notigpt.domain.saveditem

import org.muilab.notigpt.model.features.SavedItem

/** Hard client-side merge invariants; semantic sameness remains an LLM decision. */
object SavedItemMergePolicy {
    data class PreservedUserState(
        val isStarred: Boolean,
        val userEdited: Boolean,
    )

    fun preservedUserState(items: List<SavedItem>): PreservedUserState? {
        if (items.isEmpty() || items.map(SavedItem::itemType).distinct().size != 1) return null
        return PreservedUserState(
            isStarred = items.any(SavedItem::isStarred),
            userEdited = items.any(SavedItem::userEdited),
        )
    }
}
