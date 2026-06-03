package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState

/**
 * Local access layer for saved task/keep content and its inbox lifecycle state.
 */
@Dao
interface SavedItemDao {

    @Upsert
    suspend fun upsert(reminder: SavedItem)

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 ORDER BY isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'task' AND state IN ('saved', 'completed') ORDER BY isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'keep' AND state IN ('saved', 'archived') ORDER BY isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeMemos(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'task' AND state = 'completed' ORDER BY isPinned DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeCompletedTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND state IN ('new', 'updated') ORDER BY lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeNewItems(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE savedItemId = :savedItemId")
    suspend fun getById(savedItemId: String): SavedItem?

    @Query("SELECT * FROM saved_item WHERE isVisible = 1")
    suspend fun getAllVisible(): List<SavedItem>

    @Query("UPDATE saved_item SET deletedAtMs = :ts WHERE savedItemId = :savedItemId")
    suspend fun setDeletedAt(savedItemId: String, ts: Long)

    @Query("UPDATE saved_item SET isVisible = 0, deletedAtMs = :ts, lastUpdateTimestamp = :ts WHERE savedItemId = :savedItemId")
    suspend fun softDeleteById(savedItemId: String, ts: Long)

    @Query("UPDATE saved_item SET isVisible = 0, deletedAtMs = :ts, lastUpdateTimestamp = :ts WHERE savedItemId IN (:savedItemIds)")
    suspend fun softDeleteByIds(savedItemIds: List<String>, ts: Long)

    @Query("UPDATE saved_item SET state = :state, lastUpdateTimestamp = :ts WHERE savedItemId = :savedItemId")
    suspend fun setState(savedItemId: String, state: String, ts: Long)

    @Query("UPDATE saved_item SET state = 'saved', lastUpdateTimestamp = :ts WHERE savedItemId IN (:savedItemIds)")
    suspend fun markSavedByIds(savedItemIds: List<String>, ts: Long)

    suspend fun setCompleted(savedItemId: String, completed: Boolean, ts: Long) {
        setState(savedItemId, if (completed) SavedItemState.Completed else SavedItemState.Saved, ts)
    }

    @Query("UPDATE saved_item SET state = 'saved' WHERE savedItemId = :savedItemId")
    suspend fun setViewed(savedItemId: String)

    @Query("UPDATE saved_item SET isPinned = :pinned WHERE savedItemId = :savedItemId")
    suspend fun setPinned(savedItemId: String, pinned: Boolean)

    @Query("UPDATE saved_item SET sortScore = :sortScore, reRankHistory = :reRankHistory WHERE savedItemId = :savedItemId")
    suspend fun updateSortScoreAndHistory(savedItemId: String, sortScore: Float, reRankHistory: String)

    @Query("UPDATE saved_item SET buttons = :buttons WHERE savedItemId = :savedItemId")
    suspend fun updateButtons(savedItemId: String, buttons: String)
}
