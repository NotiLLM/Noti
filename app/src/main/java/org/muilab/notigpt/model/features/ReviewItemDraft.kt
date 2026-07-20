package org.muilab.notigpt.model.features

/** Complete user-editable state of an item while its generated proposal is under review. */
data class ReviewItemDraft(
    val item: SavedItem,
    val subItems: List<SavedSubItem>,
)
