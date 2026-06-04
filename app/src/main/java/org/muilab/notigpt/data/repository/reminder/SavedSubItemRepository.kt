package org.muilab.notigpt.data.repository.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.SavedSubItemDao
import org.muilab.notigpt.model.features.SavedSubItem

/**
 * Repository for reminder sub-task persistence and grouped observation.
 *
 * Keep sub-task writes here so ReminderViewModel can coordinate parent reminders without knowing DAO details or
 * soft-delete mechanics.
 */
class SavedSubItemRepository(private val subTaskDao: SavedSubItemDao) {

    /** All visible sub-tasks grouped by their parent reminder ID. */
    fun observeAllByReminder(): Flow<Map<String, List<SavedSubItem>>> =
        subTaskDao.observeAllVisible().map { list ->
            list.groupBy { it.parentSavedItemId }
        }

    suspend fun upsert(subTask: SavedSubItem) = withContext(Dispatchers.IO) {
        subTaskDao.upsert(subTask)
    }

    suspend fun upsertAll(subTasks: List<SavedSubItem>) = withContext(Dispatchers.IO) {
        subTaskDao.upsertAll(subTasks)
    }

    /**
     * Replaces the visible generated sub-task set for a parent reminder.
     *
     * n8n returns `subTasks` as a complete child list for the saved item, so omitted existing rows should no longer
     * appear. Rows are soft-deleted instead of removed to preserve local audit/debug history.
     */
    suspend fun replaceVisibleForParent(savedItemId: String, subTasks: List<SavedSubItem>, ts: Long) = withContext(Dispatchers.IO) {
        if (subTasks.isEmpty()) {
            subTaskDao.softDeleteByParentId(savedItemId, ts)
        } else {
            subTaskDao.upsertAll(subTasks)
            subTaskDao.softDeleteByParentIdExcept(savedItemId, subTasks.map { it.savedSubItemId }, ts)
        }
    }

    suspend fun setCompleted(savedSubItemId: String, completed: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        subTaskDao.setCompleted(savedSubItemId, completed, ts)
    }

    suspend fun softDeleteById(savedSubItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        subTaskDao.softDeleteById(savedSubItemId, ts)
    }

    /** Cascade soft-delete when parent reminder is deleted. */
    suspend fun softDeleteByParentId(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        subTaskDao.softDeleteByParentId(savedItemId, ts)
    }

    suspend fun getById(savedSubItemId: String): SavedSubItem? = withContext(Dispatchers.IO) {
        subTaskDao.getById(savedSubItemId)
    }
}

