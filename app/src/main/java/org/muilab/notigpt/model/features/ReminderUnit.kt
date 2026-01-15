package org.muilab.notigpt.model.features

import androidx.room.Entity

/**
 * A Reminder is the superset concept; Tasks are reminders where [isTask] is true.
 *
 * Note: Reminders are hard-deleted on user delete.
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

    // Whether user has edited this reminder
    val userEdited: Boolean = false
)
