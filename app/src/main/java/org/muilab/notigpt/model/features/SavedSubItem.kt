package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index

/**
 * Room entity for a child checklist/content item under a SavedItem.
 */
@Entity(
    tableName = "saved_sub_item",
    primaryKeys = ["savedSubItemId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["parentSavedItemId"],
            onDelete = ForeignKey.NO_ACTION, // cascade handled in repository
        )
    ],
    indices = [
        Index(value = ["parentSavedItemId"], name = "idx_saved_sub_item_parent"),
    ],
)
data class SavedSubItem(
    val savedSubItemId: String,
    val parentSavedItemId: String,

    // Content
    val title: String = "",
    val description: String = "",

    // Type / state
    @ColumnInfo(defaultValue = "'task'")
    val itemType: String = SavedItemType.Task,
    @ColumnInfo(defaultValue = "0")
    val isCompleted: Boolean = false,

    // Timestamps
    val deadlineAtMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val startAtMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val endAtMs: Long = 0L,

    // LLM-generated action buttons – same JSON format as SavedItem.buttons
    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",

    // Ordering within the parent saved item
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,

    // Audit
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdateTimestamp: Long = System.currentTimeMillis(),

    // Soft-delete
    @ColumnInfo(defaultValue = "1")
    val isVisible: Boolean = true,
) {
    val isTask: Boolean
        @Ignore get() = itemType == SavedItemType.Task

    val isEvent: Boolean
        @Ignore get() = false
}
