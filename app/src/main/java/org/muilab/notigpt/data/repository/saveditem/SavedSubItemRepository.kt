package org.muilab.notigpt.data.repository.saveditem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.SavedSubItemDao
import org.muilab.notigpt.model.features.SavedSubItem

/**
 * Repository for SavedItem subtask persistence and grouped observation.
 *
 * Keep subtask writes here so SavedItemsViewModel can coordinate parent SavedItems without knowing DAO details or
 * ordering mechanics.
 */
class SavedSubItemRepository(private val subTaskDao: SavedSubItemDao) {

    /** All subtasks grouped by their parent SavedItem ID. */
    fun observeAllBySavedItem(): Flow<Map<String, List<SavedSubItem>>> =
        subTaskDao.observeAllVisible().map { list ->
            list.groupBy { it.parentSavedItemId }
        }

    suspend fun upsert(subTask: SavedSubItem) = withContext(Dispatchers.IO) {
        subTaskDao.upsert(subTask)
    }

    suspend fun upsertAll(subTasks: List<SavedSubItem>) = withContext(Dispatchers.IO) {
        subTaskDao.upsertAll(subTasks)
    }

    /** Replaces the complete child list returned by regeneration. */
    suspend fun replaceForParent(savedItemId: String, subTasks: List<SavedSubItem>) = withContext(Dispatchers.IO) {
        subTaskDao.hardDeleteByParentId(savedItemId)
        if (subTasks.isNotEmpty()) subTaskDao.upsertAll(subTasks)
    }

    suspend fun deleteSubTasks(savedSubItemIds: List<String>) = withContext(Dispatchers.IO) {
        if (savedSubItemIds.isNotEmpty()) subTaskDao.hardDeleteByIds(savedSubItemIds)
    }

    suspend fun setCompleted(savedSubItemId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        subTaskDao.setCompleted(savedSubItemId, completed)
    }

    suspend fun hardDeleteById(savedSubItemId: String) = withContext(Dispatchers.IO) {
        subTaskDao.hardDeleteById(savedSubItemId)
    }

    /** Explicit cleanup helper; the foreign key also cascades parent deletion. */
    suspend fun hardDeleteByParentId(savedItemId: String) = withContext(Dispatchers.IO) {
        subTaskDao.hardDeleteByParentId(savedItemId)
    }

    suspend fun getById(savedSubItemId: String): SavedSubItem? = withContext(Dispatchers.IO) {
        subTaskDao.getById(savedSubItemId)
    }

    suspend fun nextPosition(savedItemId: String): Int = withContext(Dispatchers.IO) {
        subTaskDao.nextPosition(savedItemId)
    }
}
