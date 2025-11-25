package org.muilab.notigpt.repository

import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.room.TaskListDao
import org.muilab.notigpt.model.features.TaskUnit

/**
 * Simple repository implemented like NotiRepository: directly delegates to the DAO.
 * Construct with the AppDatabase and TaskListDao (keeps future options to use the DB for transactions).
 */
class TaskRepository(
    private val db: AppDatabase,
    private val taskListDao: TaskListDao
) {

    fun observeAllTasks(): Flow<List<TaskUnit>> = taskListDao.getAllTasks()

    fun observeVisibleTasks(): Flow<List<TaskUnit>> = taskListDao.getVisibleTasks()

    fun observeUnfinishedCount(): Flow<Int> = taskListDao.getUnfinishedCount()

    suspend fun upsert(taskUnit: TaskUnit) {
        taskListDao.insert(taskUnit)
    }

    suspend fun setCompleted(taskId: String, completed: Boolean) {
        taskListDao.markTaskAsCompleted(taskId, completed)
    }

    suspend fun deleteById(taskId: String) {
        taskListDao.deleteById(taskId)
    }

    suspend fun clearCompleted() {
        taskListDao.deleteCompleted()
    }
}
