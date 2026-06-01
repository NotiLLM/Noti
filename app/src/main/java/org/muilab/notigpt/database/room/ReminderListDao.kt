package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.ReminderUnit

/**
 * Local access layer for reminders and their visible lifecycle state.
 *
 * This DAO owns durable reminder rows. Keep notification provenance, subtasks, and external sync
 * concerns as adjacent tables/adapters unless they need to be queried as reminder columns.
 */
@Dao
interface ReminderListDao {

    @Upsert
    suspend fun upsert(reminder: ReminderUnit)

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND (isTask = 0 OR isCompleted = 0) ORDER BY isViewed ASC, isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, reminderId DESC")
    fun observeAll(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND isTask = 1 AND isCompleted = 0 ORDER BY isViewed ASC, isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, reminderId DESC")
    fun observeTasks(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND isTask = 0 ORDER BY isViewed ASC, isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, reminderId DESC")
    fun observeMemos(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND isTask = 1 AND isCompleted = 1 ORDER BY isViewed ASC, isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, reminderId DESC")
    fun observeCompletedTasks(): Flow<List<ReminderUnit>>

    @Query("SELECT * FROM reminder_list WHERE reminderId = :reminderId")
    suspend fun getById(reminderId: String): ReminderUnit?

    @Query("SELECT * FROM reminder_list WHERE isVisible = 1 AND (isTask = 0 OR isCompleted = 0)")
    suspend fun getAllVisible(): List<ReminderUnit>

    @Query("UPDATE reminder_list SET deletedAtMs = :ts WHERE reminderId = :reminderId")
    suspend fun setDeletedAt(reminderId: String, ts: Long)

    @Query("UPDATE reminder_list SET isVisible = 0, lastUpdateTimestamp = :ts WHERE reminderId = :reminderId")
    suspend fun softDeleteById(reminderId: String, ts: Long)

    @Query("UPDATE reminder_list SET isCompleted = :completed, lastUpdateTimestamp = :ts WHERE reminderId = :reminderId")
    suspend fun setCompleted(reminderId: String, completed: Boolean, ts: Long)

    @Query("UPDATE reminder_list SET isViewed = 1 WHERE reminderId = :reminderId")
    suspend fun setViewed(reminderId: String)

    @Query("UPDATE reminder_list SET isPinned = :pinned WHERE reminderId = :reminderId")
    suspend fun setPinned(reminderId: String, pinned: Boolean)

    @Query("UPDATE reminder_list SET sortScore = :sortScore, reRankHistory = :reRankHistory WHERE reminderId = :reminderId")
    suspend fun updateSortScoreAndHistory(reminderId: String, sortScore: Float, reRankHistory: String)

    @Query("UPDATE reminder_list SET buttons = :buttons WHERE reminderId = :reminderId")
    suspend fun updateButtons(reminderId: String, buttons: String)
}
