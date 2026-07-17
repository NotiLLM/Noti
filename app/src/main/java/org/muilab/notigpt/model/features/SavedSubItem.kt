package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A single-line checklist entry under a task SavedItem.
 *
 * Subtasks deliberately carry no independent description, type, dates, or action buttons. Their
 * position is structural persistence only; it is assigned by the app and is not user-editable.
 */
@Entity(
    tableName = "saved_sub_item",
    primaryKeys = ["savedSubItemId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["parentSavedItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["parentSavedItemId"], name = "idx_saved_sub_item_parent"),
    ],
)
data class SavedSubItem(
    val savedSubItemId: String,
    val parentSavedItemId: String,
    val text: String = "",
    val isCompleted: Boolean = false,
    val position: Int = 0,
) {
    companion object {
        /** One logical line; Compose may still wrap it across visual lines. */
        fun normalizeText(value: String): String = value
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[\\t ]+"), " ")
            .trim()
    }
}
