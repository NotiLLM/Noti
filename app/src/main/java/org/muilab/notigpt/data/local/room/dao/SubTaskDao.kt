package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedSubItem

/**
 * Local access layer for subtasks nested under task SavedItems.
 *
 * Subtasks are durable SavedItem children, not independent notification-derived items. If they
 * gain their own lifecycle or sync rules, consider a dedicated repository boundary before expanding this DAO.
 */
@Dao
interface SavedSubItemDao {

    @Upsert
    suspend fun upsert(subTask: SavedSubItem)

    @Upsert
    suspend fun upsertAll(subTasks: List<SavedSubItem>)

    @Query("SELECT * FROM saved_sub_item WHERE parentSavedItemId = :savedItemId ORDER BY position ASC, savedSubItemId ASC")
    fun observeBySavedItemId(savedItemId: String): Flow<List<SavedSubItem>>

    @Query("SELECT * FROM saved_sub_item ORDER BY parentSavedItemId ASC, position ASC, savedSubItemId ASC")
    fun observeAllVisible(): Flow<List<SavedSubItem>>

    @Query("SELECT * FROM saved_sub_item WHERE parentSavedItemId = :savedItemId ORDER BY position ASC, savedSubItemId ASC")
    suspend fun getBySavedItemId(savedItemId: String): List<SavedSubItem>

    @Query("SELECT * FROM saved_sub_item WHERE savedSubItemId = :savedSubItemId")
    suspend fun getById(savedSubItemId: String): SavedSubItem?

    @Query("UPDATE saved_sub_item SET isCompleted = :completed WHERE savedSubItemId = :savedSubItemId")
    suspend fun setCompleted(savedSubItemId: String, completed: Boolean)

    @Query("DELETE FROM saved_sub_item WHERE savedSubItemId = :savedSubItemId")
    suspend fun hardDeleteById(savedSubItemId: String)

    /** Explicit helper for replacement flows; parent deletion also cascades through the FK. */
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

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM saved_sub_item WHERE parentSavedItemId = :savedItemId")
    suspend fun nextPosition(savedItemId: String): Int
}
