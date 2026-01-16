package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.ReminderUnit

@Dao
interface ReminderListDao {

    @Upsert
    suspend fun upsert(reminder: ReminderUnit)

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 ORDER BY lastUpdateTimestamp DESC")
    fun observeAll(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND isTask = 1 ORDER BY lastUpdateTimestamp DESC")
    fun observeTasks(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND isTask = 0 ORDER BY lastUpdateTimestamp DESC")
    fun observeMemos(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE reminderId = :reminderId")
    suspend fun getById(reminderId: String): ReminderUnit?

    @Query("UPDATE reminder_list SET deletedAtMs = :ts WHERE reminderId = :reminderId")
    suspend fun setDeletedAt(reminderId: String, ts: Long)

    @Query("UPDATE reminder_list SET isVisible = 0, lastUpdateTimestamp = :ts WHERE reminderId = :reminderId")
    suspend fun softDeleteById(reminderId: String, ts: Long)

    @Query("UPDATE reminder_list SET isCompleted = :completed, lastUpdateTimestamp = :ts WHERE reminderId = :reminderId")
    suspend fun setCompleted(reminderId: String, completed: Boolean, ts: Long)
}
