package org.muilab.notigpt.model.features

import androidx.room.Entity

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

    // Timestamps
    val lastUpdateTimestamp: Long,
    val deadlineTimestamp: Long,

    // Estimated completion time in minutes
    val estimatedCompletionTime: Long,

    // Associated notification keys
    val associatedNotis: Set<String> = emptySet(),

    /**
     * Snapshot ID captured at the moment this reminder was extracted.
     *
     * This lets the reminder UI render "associated notifications" even if no ESM exists.
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
)
