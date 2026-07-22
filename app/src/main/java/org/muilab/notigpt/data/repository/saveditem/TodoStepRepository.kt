package org.muilab.notigpt.data.repository.saveditem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.TodoStepDao
import org.muilab.notigpt.model.features.TodoStep

/**
 * Repository for SavedItem step persistence and grouped observation.
 *
 * Keep step writes here so SavedItemsViewModel can coordinate parent SavedItems without knowing DAO details or
 * ordering mechanics.
 */
class TodoStepRepository(private val todoStepDao: TodoStepDao) {

    /** All steps grouped by their parent SavedItem ID. */
    fun observeAllBySavedItem(): Flow<Map<String, List<TodoStep>>> =
        todoStepDao.observeAllVisible().map { list ->
            list.groupBy { it.parentSavedItemId }
        }

    suspend fun upsert(step: TodoStep) = withContext(Dispatchers.IO) {
        todoStepDao.upsert(step)
    }

    suspend fun upsertAll(steps: List<TodoStep>) = withContext(Dispatchers.IO) {
        todoStepDao.upsertAll(steps)
    }

    /** Replaces the complete child list returned by regeneration. */
    suspend fun replaceForParent(savedItemId: String, steps: List<TodoStep>) = withContext(Dispatchers.IO) {
        todoStepDao.hardDeleteByParentId(savedItemId)
        if (steps.isNotEmpty()) todoStepDao.upsertAll(steps)
    }

    suspend fun deleteSteps(todoStepIds: List<String>) = withContext(Dispatchers.IO) {
        if (todoStepIds.isNotEmpty()) todoStepDao.hardDeleteByIds(todoStepIds)
    }

    suspend fun setCompleted(todoStepId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        todoStepDao.setCompleted(todoStepId, completed)
    }

    suspend fun hardDeleteById(todoStepId: String) = withContext(Dispatchers.IO) {
        todoStepDao.hardDeleteById(todoStepId)
    }

    /** Explicit cleanup helper; the foreign key also cascades parent deletion. */
    suspend fun hardDeleteByParentId(savedItemId: String) = withContext(Dispatchers.IO) {
        todoStepDao.hardDeleteByParentId(savedItemId)
    }

    suspend fun getById(todoStepId: String): TodoStep? = withContext(Dispatchers.IO) {
        todoStepDao.getById(todoStepId)
    }

    suspend fun nextPosition(savedItemId: String): Int = withContext(Dispatchers.IO) {
        todoStepDao.nextPosition(savedItemId)
    }
}
