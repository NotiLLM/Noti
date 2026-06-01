package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore

/**
 * A Reminder is the superset concept; Tasks are reminders where [isTask] is true.
 *
 * Delete behavior:
 * - reminders are soft-deleted (kept in DB, hidden from list queries; see [isVisible]).
 */
@Entity(tableName = "reminder_list", primaryKeys = ["reminderId"])
data class ReminderUnit(
    val reminderId: String,

    // Content
    val reminderTitle: String = "",
    val reminderContent: String = "",

    // Type/state
    val isTask: Boolean,
    val isCompleted: Boolean = false,

    // Whether this reminder represents a calendar event
    @ColumnInfo(defaultValue = "0")
    val isEvent: Boolean = false,

    // Timestamps
    val lastUpdateTimestamp: Long,
    val deadlineTimestamp: Long,

    // Event start/end times (unix ms, 0 = not set)
    @ColumnInfo(defaultValue = "0")
    val startTime: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val endTime: Long = 0L,

    // Estimated completion time in minutes
    val estimatedCompletionTime: Long,

    /**
     * Associated notification record IDs (notiRecordId format: "notiKey_postTime").
     * More granular than notiKeys — identifies specific messages, not entire conversations.
     *
     * DB column keeps the legacy name "associatedNotis" to avoid a destructive migration.
     */
    @ColumnInfo(name = "associatedNotis")
    val associatedNotiRecords: Set<String> = emptySet(),

    /**
     * Snapshot ID captured at the moment this reminder was extracted.
     *
     * This lets the reminder UI render "associated notifications" from the extraction snapshot.
     */
    val extractionSnapshotId: String? = null,

    /**
     * Analytics provenance label.
     *
     * - "manual": user created from empty template in the reminders screen
     * - "llm_manual_extraction": user explicitly requested extraction from a notification
     * - "llm_auto_extraction": system auto-triggered extraction
     */
    val origin: String = "manual",

    /**
     * Number of human "save events" where title/content changed.
     * (Edits are defined by user save when content changed.)
     */
    val humanEditCount: Int = 0,

    /**
     * Soft-delete timestamp (ms since epoch). Null means not deleted.
     */
    val deletedAtMs: Long? = null,

    // Whether user has edited this reminder
    val userEdited: Boolean = false,

    /**
     * Soft-delete flag.
     * If false, the reminder remains in DB but is hidden from list queries.
     */
    val isVisible: Boolean = true,

    // ── LLM-generated action buttons ──
    /**
     * JSON array of button objects: [{buttonText, intent, type}].
     * type is "copy" (copy intent text to clipboard) or "link" (open intent as URL).
     */
    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",

    // ── Sorting / ranking columns ──

    /**
     * Whether the user has fully seen this reminder at least once since its latest
     * creation or regeneration. Default true for existing rows (migration).
     */
    @ColumnInfo(defaultValue = "1")
    val isViewed: Boolean = true,

    /**
     * Whether the reminder is pinned by the user.
     */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,

    /**
     * Sort score (0f–100f). Higher = higher position within the scored section.
     */
    @ColumnInfo(defaultValue = "50.0")
    val sortScore: Float = 50f,

    /**
     * JSON array of rerank history records. Each record:
     * {rankedAt, trigger, newScore, scoreExplanation}.
     */
    @ColumnInfo(defaultValue = "[]")
    val reRankHistory: String = "[]",
) {
    /**
     * Derive the set of notification keys from the record IDs.
     * notiRecordId format: "notiKey_postTime" — we strip the last "_postTime" segment.
     */
    @get:Ignore
    val associatedNotiKeys: Set<String>
        get() = associatedNotiRecords.mapTo(mutableSetOf()) { it.substringBeforeLast("_") }
}
