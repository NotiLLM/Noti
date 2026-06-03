package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity

object ReminderStatus {
    const val Scheduled = "scheduled"
    const val DueUnseen = "due_unseen"
    const val Seen = "seen"
    const val Cancelled = "cancelled"
}

object ReminderSourceType {
    const val SavedItem = "saved_item"
    const val NotiRecordSet = "noti_record_set"
}

/**
 * Active scheduled reminder/alarm. This is separate from SavedItem content.
 *
 * The screen can list all reminders by remindAtMs regardless of whether they came from a
 * task, keep item, or selected notification records. Reminder rows are kept after seen/cancelled
 * for now, but the UI hides those rows by default.
 */
@Entity(tableName = "reminder", primaryKeys = ["reminderId"])
data class Reminder(
    val reminderId: String,
    val remindAtMs: Long,
    val title: String = "",
    val content: String = "",
    @ColumnInfo(defaultValue = "'scheduled'")
    val status: String = ReminderStatus.Scheduled,
    @ColumnInfo(defaultValue = "'saved_item'")
    val sourceType: String = ReminderSourceType.SavedItem,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val seenAtMs: Long? = null,
    val cancelledAtMs: Long? = null,
)

@Entity(tableName = "reminder_saved_item_ref", primaryKeys = ["reminderId", "savedItemId"])
data class ReminderSavedItemRef(
    val reminderId: String,
    val savedItemId: String,
)

@Entity(tableName = "reminder_noti_record_ref", primaryKeys = ["reminderId", "notiRecordId"])
data class ReminderNotiRecordRef(
    val reminderId: String,
    val notiRecordId: String,
)
