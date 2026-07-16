package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderNotiRecordRef
import org.muilab.notigpt.model.features.ReminderSavedItemRef
import org.muilab.notigpt.model.features.ReminderStatus

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(reminder: Reminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedItemRefs(refs: List<ReminderSavedItemRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotiRecordRefs(refs: List<ReminderNotiRecordRef>)

    @Query("DELETE FROM reminder_saved_item_ref WHERE reminderId = :reminderId")
    suspend fun deleteSavedItemRefs(reminderId: String)

    @Query("DELETE FROM reminder_noti_record_ref WHERE reminderId = :reminderId")
    suspend fun deleteNotiRecordRefs(reminderId: String)

    @Query("DELETE FROM reminder_noti_record_ref")
    suspend fun deleteAllNotiRecordRefs()

    /** Removes alarms whose source item belongs to the generated/account-owned data set. */
    @Query("DELETE FROM reminder WHERE sourceType = 'saved_item'")
    suspend fun deleteAllSavedItemReminders()

    @Query("DELETE FROM reminder_saved_item_ref")
    suspend fun deleteAllSavedItemRefs()

    @Query("SELECT * FROM reminder WHERE status IN ('scheduled', 'due_unseen') ORDER BY remindAtMs ASC, createdAtMs ASC")
    fun observeActive(): Flow<List<Reminder>>

    @Query("SELECT COUNT(*) FROM reminder WHERE status = 'due_unseen'")
    fun observeDueUnseenCount(): Flow<Int>

    @Query("SELECT * FROM reminder WHERE reminderId = :reminderId")
    suspend fun getById(reminderId: String): Reminder?

    @Query("UPDATE reminder SET status = :status, updatedAtMs = :updatedAtMs, seenAtMs = :seenAtMs WHERE reminderId = :reminderId")
    suspend fun setSeen(reminderId: String, status: String = ReminderStatus.Seen, seenAtMs: Long, updatedAtMs: Long = seenAtMs)

    @Query("UPDATE reminder SET status = :status, updatedAtMs = :updatedAtMs WHERE reminderId = :reminderId")
    suspend fun markDueUnseen(reminderId: String, status: String = ReminderStatus.DueUnseen, updatedAtMs: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminder WHERE status = :status AND remindAtMs > :now ORDER BY remindAtMs ASC")
    suspend fun getFutureScheduled(status: String = ReminderStatus.Scheduled, now: Long = System.currentTimeMillis()): List<Reminder>

    @Query("SELECT * FROM reminder WHERE status = :status AND remindAtMs <= :now ORDER BY remindAtMs ASC")
    suspend fun getPastScheduled(status: String = ReminderStatus.Scheduled, now: Long = System.currentTimeMillis()): List<Reminder>

    @Query("UPDATE reminder SET status = :status, remindAtMs = :remindAtMs, updatedAtMs = :updatedAtMs, seenAtMs = NULL, cancelledAtMs = NULL WHERE reminderId = :reminderId")
    suspend fun reschedule(reminderId: String, remindAtMs: Long, status: String = ReminderStatus.Scheduled, updatedAtMs: Long = System.currentTimeMillis())

    @Query("UPDATE reminder SET status = :status, updatedAtMs = :updatedAtMs, cancelledAtMs = :cancelledAtMs WHERE reminderId = :reminderId")
    suspend fun cancel(reminderId: String, status: String = ReminderStatus.Cancelled, cancelledAtMs: Long, updatedAtMs: Long = cancelledAtMs)

    @Query("UPDATE reminder SET status = 'due_unseen', updatedAtMs = :now WHERE status = 'scheduled' AND remindAtMs <= :now")
    suspend fun markDueUnseen(now: Long)

    @Transaction
    suspend fun upsertForSavedItem(reminder: Reminder, savedItemId: String) {
        upsert(reminder)
        deleteSavedItemRefs(reminder.reminderId)
        deleteNotiRecordRefs(reminder.reminderId)
        insertSavedItemRefs(listOf(ReminderSavedItemRef(reminder.reminderId, savedItemId)))
    }

    @Transaction
    suspend fun upsertForNotiRecords(reminder: Reminder, notiRecordIds: List<String>) {
        upsert(reminder)
        deleteSavedItemRefs(reminder.reminderId)
        deleteNotiRecordRefs(reminder.reminderId)
        insertNotiRecordRefs(notiRecordIds.map { ReminderNotiRecordRef(reminder.reminderId, it) })
    }
}
