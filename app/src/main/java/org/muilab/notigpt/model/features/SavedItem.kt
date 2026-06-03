package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore

object SavedItemType {
    const val Task = "task"
    const val Keep = "keep"
}

object SavedItemState {
    const val New = "new"
    const val Updated = "updated"
    const val Saved = "saved"
    const val Completed = "completed"
    const val Archived = "archived"

    fun isNewLike(state: String): Boolean = state == New || state == Updated
    fun isTaskListState(state: String): Boolean = state == Saved || state == Completed
    fun isKeepListState(state: String): Boolean = state == Saved || state == Archived
}

/**
 * Room entity for durable content saved by the user or extracted by the LLM.
 *
 * A SavedItem is not an active scheduled reminder. It is the local source of truth for
 * task/keep content, visibility, completion state, and ranking metadata. Scheduled push
 * notifications use [Reminder] and link back to saved items or notification records.
 */
@Entity(tableName = "saved_item", primaryKeys = ["savedItemId"])
data class SavedItem(
    val savedItemId: String,

    // Content
    val title: String = "",
    val content: String = "",

    // Type/state
    @ColumnInfo(defaultValue = "'task'")
    val itemType: String = SavedItemType.Task,
    @ColumnInfo(defaultValue = "'saved'")
    val state: String = SavedItemState.Saved,

    // Timestamps
    val lastUpdateTimestamp: Long,
    val deadlineAtMs: Long,

    // Event-shaped fields kept for backward compatibility with old n8n/local data, but not presented as Event.
    @ColumnInfo(defaultValue = "0")
    val startAtMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val endAtMs: Long = 0L,

    // Estimated completion time in minutes
    val estimatedCompletionTime: Long,

    /**
     * Associated notification record IDs (notiRecordId format: "notiKey_postTime").
     * Room column name remains "associatedNotis" so migrated databases keep reading the same field.
     */
    @ColumnInfo(name = "associatedNotis")
    val sourceNotiRecordIds: Set<String> = emptySet(),

    /** Snapshot ID captured at the moment this saved item was extracted. */
    val sourceExtractionSnapshotId: String? = null,

    /**
     * Analytics provenance label.
     * - "manual": user created from empty template
     * - "llm_manual_extraction": user explicitly requested extraction from a notification
     * - "llm_auto_extraction": system auto-triggered extraction
     */
    val origin: String = "manual",

    /** Number of human save events where title/content changed. */
    val humanEditCount: Int = 0,

    /** Soft-delete timestamp (ms since epoch). Null means not deleted. */
    val deletedAtMs: Long? = null,

    // Whether user has edited this saved item
    val userEdited: Boolean = false,

    /** Soft-delete flag. If false, the row remains in DB but is hidden from list queries. */
    val isVisible: Boolean = true,

    // JSON array of button objects: [{buttonText, intent, type}].
    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",

    /** Whether the user has fully seen this generated item at least once since creation/regeneration. */
    @ColumnInfo(defaultValue = "1")
    val isViewed: Boolean = true,

    /** Whether the saved item is pinned by the user. */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,

    /** Sort score (0f–100f). Higher = higher position within the scored section. */
    @ColumnInfo(defaultValue = "50.0")
    val sortScore: Float = 50f,

    /** JSON array of rerank history records. */
    @ColumnInfo(defaultValue = "[]")
    val reRankHistory: String = "[]",
) {
    val isTask: Boolean
        @Ignore get() = itemType == SavedItemType.Task

    val isCompleted: Boolean
        @Ignore get() = state == SavedItemState.Completed

    val isArchived: Boolean
        @Ignore get() = state == SavedItemState.Archived

    val isNewLike: Boolean
        @Ignore get() = SavedItemState.isNewLike(state)

    val isEvent: Boolean
        @Ignore get() = false

    @get:Ignore
    val associatedNotiKeys: Set<String>
        get() = sourceNotiRecordIds.mapTo(mutableSetOf()) { it.substringBeforeLast("_") }
}
