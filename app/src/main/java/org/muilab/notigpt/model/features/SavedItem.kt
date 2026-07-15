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
 * task/keep content and completion state. Scheduled push notifications use [Reminder] and
 * link back to saved items or notification records. Rows only exist once the user approved
 * the item (or created it manually): LLM proposals stage as [PendingOp]s until reviewed,
 * and user deletion is a hard delete.
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

    /**
     * Analytics provenance label.
     * - "manual": user created from empty template
     * - "llm_manual_extraction": user explicitly requested extraction from a notification
     * - "llm_auto_extraction": system auto-triggered extraction
     */
    val origin: String = "manual",

    /** Number of human save events where title/content changed. */
    val humanEditCount: Int = 0,

    // Whether user has edited this saved item
    val userEdited: Boolean = false,

    // JSON array of button objects: [{buttonText, intent, type}].
    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",

    /** Whether the user has fully seen this generated item at least once since creation/regeneration. */
    @ColumnInfo(defaultValue = "1")
    val isViewed: Boolean = true,

    /** User-set star. User-owned: never touched by LLM flows. */
    @ColumnInfo(defaultValue = "0")
    val isStarred: Boolean = false,

    /**
     * User-set "do date" (ms since epoch) — when the user intends to work on the task, distinct
     * from [deadlineAtMs]. 0 = unset. User-owned: never touched by LLM flows.
     */
    @ColumnInfo(defaultValue = "0")
    val doAtMs: Long = 0L,

    /**
     * Timestamp of the newest change the user has explicitly acknowledged (via the review action).
     * Change-log rows newer than this render as "what's new".
     */
    @ColumnInfo(defaultValue = "0")
    val lastViewedChangeAt: Long = 0L,
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

    companion object {
        /**
         * Sentinel [doAtMs] meaning the user marked the task "Someday": intended eventually, but with
         * no committed date. Distinct from 0 (no planned date set at all). Chosen as [Long.MAX_VALUE]
         * so it naturally sorts after every real planned date and is trivially excluded from date-range
         * buckets. LLM flows never set [doAtMs], so only the user ever produces this value.
         */
        const val DO_AT_SOMEDAY = Long.MAX_VALUE

        /** True when [doAtMs] holds a concrete planned date (not unset, not the Someday sentinel). */
        fun hasPlannedDate(doAtMs: Long): Boolean = doAtMs > 0L && doAtMs != DO_AT_SOMEDAY

        fun isSomeday(doAtMs: Long): Boolean = doAtMs == DO_AT_SOMEDAY

        /**
         * Single source of truth for planned-date bucketing, shared by the home-screen smart-filter
         * counts (mirrored in SQL in `SavedItemDao.observeSmartFilterCounts`) and the in-memory list
         * filter in `ReminderViewModel`. [startOfTomorrowMs] is the local midnight boundary, so
         * "Today & earlier" absorbs overdue planned dates.
         *
         * Keeps can carry a real do-date (set from the reminder card's do-date affordance), and when
         * they do, they land in the same date-driven buckets as tasks. A keep with no do-date has
         * nothing to schedule by, so it defaults into [DoDateBucket.Someday] rather than
         * "Undetermined" — that bucket is task-only, where it reads as "尚未安排的任務" (unscheduled tasks).
         */
        fun plannedBucket(doAtMs: Long, startOfTomorrowMs: Long, isTask: Boolean = true): DoDateBucket = when {
            doAtMs == DO_AT_SOMEDAY -> DoDateBucket.Someday
            doAtMs <= 0L -> if (isTask) DoDateBucket.Undetermined else DoDateBucket.Someday
            doAtMs < startOfTomorrowMs -> DoDateBucket.TodayEarlier
            else -> DoDateBucket.Upcoming
        }
    }
}

/** Planned-date smart-filter buckets keyed on [SavedItem.doAtMs]. */
enum class DoDateBucket { TodayEarlier, Upcoming, Someday, Undetermined }
