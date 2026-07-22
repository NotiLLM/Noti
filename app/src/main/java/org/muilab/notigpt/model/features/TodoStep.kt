package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A single-line action step inside one todo completion unit. */
@Entity(
    tableName = "todo_step",
    primaryKeys = ["todoStepId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["parentSavedItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["parentSavedItemId"], name = "idx_todo_step_parent")],
)
data class TodoStep(
    val todoStepId: String,
    val parentSavedItemId: String,
    val text: String = "",
    val isCompleted: Boolean = false,
    val position: Int = 0,
) {
    companion object {
        /** One logical line; Compose may still wrap it visually. */
        fun normalizeText(value: String): String = value
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[\\t ]+"), " ")
            .trim()
    }
}
