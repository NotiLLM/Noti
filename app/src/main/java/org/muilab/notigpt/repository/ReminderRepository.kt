package org.muilab.notigpt.repository

import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.database.room.ReminderListDao
import org.muilab.notigpt.model.features.ReminderUnit

class ReminderRepository(
    private val reminderListDao: ReminderListDao,
) {
    fun observeAll(): Flow<List<ReminderUnit>> = reminderListDao.observeAll()
    fun observeTasks(): Flow<List<ReminderUnit>> = reminderListDao.observeTasks()
    fun observeMemos(): Flow<List<ReminderUnit>> = reminderListDao.observeMemos()

    suspend fun upsert(reminder: ReminderUnit) = reminderListDao.upsert(reminder)

    suspend fun deleteById(reminderId: String, ts: Long) = reminderListDao.softDeleteById(reminderId, ts)

    suspend fun setCompleted(reminderId: String, completed: Boolean, ts: Long) =
        reminderListDao.setCompleted(reminderId, completed, ts)

    suspend fun getById(reminderId: String): ReminderUnit? = reminderListDao.getById(reminderId)
}
