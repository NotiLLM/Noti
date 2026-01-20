package org.muilab.notigpt.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.database.room.ReminderListDao
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.repository.firestore.FirestoreSyncRepository

class ReminderRepository(
    private val reminderListDao: ReminderListDao,
    private val appContext: Context,
) {
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }

    fun observeAll(): Flow<List<ReminderUnit>> = reminderListDao.observeAll()
    fun observeTasks(): Flow<List<ReminderUnit>> = reminderListDao.observeTasks()
    fun observeMemos(): Flow<List<ReminderUnit>> = reminderListDao.observeMemos()
    fun observeCompletedTasks(): Flow<List<ReminderUnit>> = reminderListDao.observeCompletedTasks()

    suspend fun upsert(reminder: ReminderUnit) {
        reminderListDao.upsert(reminder)
        // Best-effort; never block core UX.
        withContext(Dispatchers.IO) {
            firestoreSync.syncReminder(reminder)
        }
    }

    suspend fun deleteById(reminderId: String, ts: Long) {
        // Persist deletedAtMs before flipping visibility.
        reminderListDao.setDeletedAt(reminderId, ts)
        reminderListDao.softDeleteById(reminderId, ts)

        val updated = reminderListDao.getById(reminderId) ?: return
        withContext(Dispatchers.IO) {
            firestoreSync.syncReminder(updated)
        }
    }

    suspend fun setCompleted(reminderId: String, completed: Boolean, ts: Long) {
        reminderListDao.setCompleted(reminderId, completed, ts)

        val updated = reminderListDao.getById(reminderId) ?: return
        withContext(Dispatchers.IO) {
            firestoreSync.syncReminder(updated)
        }
    }

    suspend fun getById(reminderId: String): ReminderUnit? = reminderListDao.getById(reminderId)
}
