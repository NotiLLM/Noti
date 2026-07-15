package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedSubItem

/**
 * Local access layer for subtasks nested under reminders.
 *
 * Subtasks are durable reminder children, not independent notification-derived reminders. If they
 * gain their own lifecycle or sync rules, consider a dedicated repository boundary before expanding this DAO.
 */
@Dao
interface SavedSubItemDao {

    @Upsert
    suspend fun upsert(subTask: SavedSubItem)

    @Upsert
    suspend fun upsertAll(subTasks: List<SavedSubItem>)

    @Query("SELECT * FROM saved_sub_item WHERE parentSavedItemId = :savedItemId AND isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByReminderId(savedItemId: String): Flow<List<SavedSubItem>>

    @Query("SELECT * FROM saved_sub_item WHERE isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAllVisible(): Flow<List<SavedSubItem>>

    @Query("SELECT * FROM saved_sub_item WHERE parentSavedItemId = :savedItemId AND isVisible = 1 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getByReminderId(savedItemId: String): List<SavedSubItem>

    @Query("SELECT * FROM saved_sub_item WHERE savedSubItemId = :savedSubItemId")
    suspend fun getById(savedSubItemId: String): SavedSubItem?

    @Query("UPDATE saved_sub_item SET isCompleted = :completed, lastUpdateTimestamp = :ts WHERE savedSubItemId = :savedSubItemId")
    suspend fun setCompleted(savedSubItemId: String, completed: Boolean, ts: Long)

    @Query("UPDATE saved_sub_item SET isVisible = 0, lastUpdateTimestamp = :ts WHERE savedSubItemId = :savedSubItemId")
    suspend fun softDeleteById(savedSubItemId: String, ts: Long)

    /** Cascade soft-delete all sub-tasks of a parent reminder. */
    @Query("UPDATE saved_sub_item SET isVisible = 0, lastUpdateTimestamp = :ts WHERE parentSavedItemId = :savedItemId")
    suspend fun softDeleteByParentId(savedItemId: String, ts: Long)

    /** Un-hides previously soft-deleted sub-tasks, e.g. restoring ones an LLM edit removed on revert. */
    @Query("UPDATE saved_sub_item SET isVisible = 1, lastUpdateTimestamp = :ts WHERE savedSubItemId IN (:ids)")
    suspend fun restoreByIds(ids: List<String>, ts: Long)

    /** Hard-delete all sub-tasks of a parent: rides parent hard deletes (the FK doesn't cascade). */
    @Query("DELETE FROM saved_sub_item WHERE parentSavedItemId = :savedItemId")
    suspend fun hardDeleteByParentId(savedItemId: String)

    /** Hard-delete specific sub-tasks, e.g. undoing an accepted op that inserted them. */
    @Query("DELETE FROM saved_sub_item WHERE savedSubItemId IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM saved_sub_item WHERE parentSavedItemId IN (:savedItemIds)")
    suspend fun hardDeleteByParentIds(savedItemIds: List<String>)

    /** Account-switch wipe. */
    @Query("DELETE FROM saved_sub_item")
    suspend fun deleteAllForAccountSwitch()

    /** Soft-delete visible sub-tasks omitted from a replacement backend response. */
    @Query("UPDATE saved_sub_item SET isVisible = 0, lastUpdateTimestamp = :ts WHERE parentSavedItemId = :savedItemId AND savedSubItemId NOT IN (:keptIds)")
    suspend fun softDeleteByParentIdExcept(savedItemId: String, keptIds: List<String>, ts: Long)
}
