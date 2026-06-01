package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity for a child task under a reminder.
 *
 * Keep this model focused on nested checklist/event structure. It carries its own title, timing, buttons,
 * and completion state while deletion mirrors the parent through [isVisible]. If subtasks need notification
 * provenance or extraction snapshots, add that through an explicit relationship instead of copying ReminderUnit fields.
 */
@Entity(
    tableName = "sub_tasks",
    primaryKeys = ["subTaskId"],
    foreignKeys = [
        ForeignKey(
            entity = ReminderUnit::class,
            parentColumns = ["reminderId"],
            childColumns = ["parentReminderId"],
            onDelete = ForeignKey.NO_ACTION, // cascade handled in repository
        )
    ],
    indices = [
        Index(value = ["parentReminderId"], name = "idx_subtask_parent"),
    ],
)
data class SubTask(
    val subTaskId: String,
    val parentReminderId: String,

    // Content
    val title: String = "",
    val description: String = "",

    // Type / state
    @ColumnInfo(defaultValue = "1")
    val isTask: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isEvent: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isCompleted: Boolean = false,

    // Timestamps
    val deadlineTimestamp: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val startTime: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val endTime: Long = 0L,

    // LLM-generated action buttons – same JSON format as ReminderUnit.buttons
    @ColumnInfo(defaultValue = "[]")
    val buttons: String = "[]",

    // Ordering within the parent reminder
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,

    // Audit
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdateTimestamp: Long = System.currentTimeMillis(),

    // Soft-delete
    @ColumnInfo(defaultValue = "1")
    val isVisible: Boolean = true,
)
