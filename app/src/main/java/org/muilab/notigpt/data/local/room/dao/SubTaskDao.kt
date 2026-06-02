package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SubTask

/**
 * Local access layer for subtasks nested under reminders.
 *
 * Subtasks are durable reminder children, not independent notification-derived reminders. If they
 * gain their own lifecycle or sync rules, consider a dedicated repository boundary before expanding this DAO.
 */
@Dao
interface SubTaskDao {

    @Upsert
    suspend fun upsert(subTask: SubTask)

    @Upsert
    suspend fun upsertAll(subTasks: List<SubTask>)

    @Query("SELECT * FROM sub_tasks WHERE parentReminderId = :reminderId AND isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByReminderId(reminderId: String): Flow<List<SubTask>>

    @Query("SELECT * FROM sub_tasks WHERE isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAllVisible(): Flow<List<SubTask>>

    @Query("SELECT * FROM sub_tasks WHERE parentReminderId = :reminderId AND isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getByReminderId(reminderId: String): List<SubTask>

    @Query("SELECT * FROM sub_tasks WHERE subTaskId = :subTaskId")
    suspend fun getById(subTaskId: String): SubTask?

    @Query("UPDATE sub_tasks SET isCompleted = :completed, lastUpdateTimestamp = :ts WHERE subTaskId = :subTaskId")
    suspend fun setCompleted(subTaskId: String, completed: Boolean, ts: Long)

    @Query("UPDATE sub_tasks SET isVisible = 0, lastUpdateTimestamp = :ts WHERE subTaskId = :subTaskId")
    suspend fun softDeleteById(subTaskId: String, ts: Long)

    /** Cascade soft-delete all sub-tasks of a parent reminder. */
    @Query("UPDATE sub_tasks SET isVisible = 0, lastUpdateTimestamp = :ts WHERE parentReminderId = :reminderId")
    suspend fun softDeleteByParentId(reminderId: String, ts: Long)
}
