package org.muilab.notigpt.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.muilab.notigpt.database.room.SubTaskDao
import org.muilab.notigpt.model.features.SubTask

/**
 * Repository for reminder sub-task persistence and grouped observation.
 *
 * Keep sub-task writes here so ReminderViewModel can coordinate parent reminders without knowing DAO details or
 * soft-delete mechanics.
 */
class SubTaskRepository(private val subTaskDao: SubTaskDao) {

    /** All visible sub-tasks grouped by their parent reminder ID. */
    fun observeAllByReminder(): Flow<Map<String, List<SubTask>>> =
        subTaskDao.observeAllVisible().map { list ->
            list.groupBy { it.parentReminderId }
        }

    suspend fun upsert(subTask: SubTask) = subTaskDao.upsert(subTask)

    suspend fun upsertAll(subTasks: List<SubTask>) = subTaskDao.upsertAll(subTasks)

    suspend fun setCompleted(subTaskId: String, completed: Boolean, ts: Long) =
        subTaskDao.setCompleted(subTaskId, completed, ts)

    suspend fun softDeleteById(subTaskId: String, ts: Long) =
        subTaskDao.softDeleteById(subTaskId, ts)

    /** Cascade soft-delete when parent reminder is deleted. */
    suspend fun softDeleteByParentId(reminderId: String, ts: Long) =
        subTaskDao.softDeleteByParentId(reminderId, ts)

    suspend fun getById(subTaskId: String): SubTask? = subTaskDao.getById(subTaskId)
}

