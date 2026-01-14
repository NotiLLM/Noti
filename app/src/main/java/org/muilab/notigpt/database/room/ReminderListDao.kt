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

    @Query("SELECT * FROM reminder_list ORDER BY lastUpdateTimestamp DESC")
    fun observeAll(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isTask = 1 ORDER BY lastUpdateTimestamp DESC")
    fun observeTasks(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isTask = 0 ORDER BY lastUpdateTimestamp DESC")
    fun observeMemos(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE reminderId = :reminderId")
    suspend fun getById(reminderId: String): ReminderUnit?

    @Query("DELETE FROM reminder_list WHERE reminderId = :reminderId")
    suspend fun deleteById(reminderId: String)

    @Query("UPDATE reminder_list SET isCompleted = :completed, lastUpdateTimestamp = :ts WHERE reminderId = :reminderId")
    suspend fun setCompleted(reminderId: String, completed: Boolean, ts: Long)
}

