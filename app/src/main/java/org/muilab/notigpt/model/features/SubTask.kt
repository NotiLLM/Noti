package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A sub-task belongs to a [ReminderUnit]. Each reminder can have many sub-tasks.
 *
 * Sub-tasks carry their own title, description, deadline/event times, action buttons,
 * and completion state — like items inside a Google Tasks list.
 *
 * Delete behaviour mirrors the parent: soft-delete via [isVisible].
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
