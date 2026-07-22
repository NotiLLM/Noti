package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore

object SavedItemType {
    const val Todo = "todo"
    const val Keep = "keep"
}

object SavedItemState {
    const val New = "new"
    const val Updated = "updated"
    const val Saved = "saved"
    const val Completed = "completed"
    const val Archived = "archived"

    fun isNewLike(state: String): Boolean = state == New || state == Updated
    fun isTodoListState(state: String): Boolean = state == Saved || state == Completed
    fun isKeepListState(state: String): Boolean = state == Saved || state == Archived
}

/**
 * Durable todo/keep content saved by the user or accepted from an LLM proposal.
 *
 * Calendar occurrences do not belong in this model. [deadlineAtMs] is the latest completion
 * boundary for a todo, while user-requested notification schedules live in [Reminder].
 */
@Entity(tableName = "saved_item", primaryKeys = ["savedItemId"])
data class SavedItem(
    val savedItemId: String,
    val title: String = "",
    val content: String = "",

    @ColumnInfo(defaultValue = "'todo'")
    val itemType: String = SavedItemType.Todo,
    @ColumnInfo(defaultValue = "'saved'")
    val state: String = SavedItemState.Saved,

    /** Meaningful content timestamp used by Recently updated. */
    val lastUpdateTimestamp: Long,

    /** Infrastructure-only conflict clock used by Firestore reconciliation. */
    @ColumnInfo(defaultValue = "0")
    val syncModifiedAt: Long = lastUpdateTimestamp,

    val deadlineAtMs: Long,

    /** manual, llm_manual_extraction, or llm_auto_extraction. */
    val origin: String = "manual",
    val humanEditCount: Int = 0,
    val userEdited: Boolean = false,

    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",
    @ColumnInfo(defaultValue = "1")
    val isViewed: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isStarred: Boolean = false,

    /** Newest generated change explicitly acknowledged by the user. */
    @ColumnInfo(defaultValue = "0")
    val lastViewedChangeAt: Long = 0L,
) {
    val isTodo: Boolean
        @Ignore get() = itemType == SavedItemType.Todo

    val isCompleted: Boolean
        @Ignore get() = state == SavedItemState.Completed

    val isArchived: Boolean
        @Ignore get() = state == SavedItemState.Archived

    val isNewLike: Boolean
        @Ignore get() = SavedItemState.isNewLike(state)
}
