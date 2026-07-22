package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.ReminderDao
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderSourceType
import org.muilab.notigpt.model.features.ReminderStatus
import org.muilab.notigpt.model.features.SavedItem
import java.util.UUID

class ScheduledReminderRepository(
    private val context: Context,
    private val reminderDao: ReminderDao,
) {
    fun observeActive(): Flow<List<Reminder>> = reminderDao.observeActive()
    fun observeDueUnseenCount(): Flow<Int> = reminderDao.observeDueUnseenCount()

    suspend fun refreshDueStates(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        reminderDao.markDueUnseen(now)
    }

    suspend fun scheduleExistingFutureReminders() = withContext(Dispatchers.IO) {
        reminderDao.getFutureScheduled().forEach { reminder ->
            ReminderScheduler.schedule(context, reminder.reminderId, reminder.remindAtMs)
        }
        reminderDao.getPastScheduled().forEach { reminder ->
            reminderDao.markDueUnseen(reminder.reminderId)
        }
    }

    suspend fun createForSavedItem(savedItem: SavedItem, remindAtMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            reminderId = "rem_" + UUID.randomUUID().toString().take(12),
            remindAtMs = remindAtMs,
            title = savedItem.title.ifBlank { if (savedItem.isTodo) "Todo reminder" else "Keep reminder" },
            content = savedItem.content,
            status = if (remindAtMs <= now) ReminderStatus.DueUnseen else ReminderStatus.Scheduled,
            sourceType = ReminderSourceType.SavedItem,
            createdAtMs = now,
            updatedAtMs = now,
        )
        reminderDao.upsertForSavedItem(reminder, savedItem.savedItemId)
        if (reminder.status == ReminderStatus.Scheduled) {
            ReminderScheduler.schedule(context, reminder.reminderId, reminder.remindAtMs)
        }
    }

    suspend fun createForNotiRecords(title: String, content: String, notiRecordIds: List<String>, remindAtMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            reminderId = "rem_" + UUID.randomUUID().toString().take(12),
            remindAtMs = remindAtMs,
            title = title,
            content = content,
            status = if (remindAtMs <= now) ReminderStatus.DueUnseen else ReminderStatus.Scheduled,
            sourceType = ReminderSourceType.NotiRecordSet,
            createdAtMs = now,
            updatedAtMs = now,
        )
        reminderDao.upsertForNotiRecords(reminder, notiRecordIds)
        if (reminder.status == ReminderStatus.Scheduled) {
            ReminderScheduler.schedule(context, reminder.reminderId, reminder.remindAtMs)
        }
    }

    suspend fun markSeen(reminderId: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        reminderDao.setSeen(reminderId = reminderId, seenAtMs = now, updatedAtMs = now)
        ReminderScheduler.cancel(context, reminderId)
    }

    suspend fun reschedule(reminderId: String, remindAtMs: Long, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        reminderDao.reschedule(reminderId = reminderId, remindAtMs = remindAtMs, updatedAtMs = now)
        ReminderScheduler.cancel(context, reminderId)
        if (remindAtMs > now) {
            ReminderScheduler.schedule(context, reminderId, remindAtMs)
        } else {
            reminderDao.markDueUnseen(reminderId = reminderId, updatedAtMs = now)
        }
    }

    suspend fun cancel(reminderId: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        reminderDao.cancel(reminderId = reminderId, cancelledAtMs = now, updatedAtMs = now)
        ReminderScheduler.cancel(context, reminderId)
    }
}
