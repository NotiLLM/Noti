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

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'task' AND state IN ('new', 'updated', 'saved', 'completed') ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'keep' AND state IN ('new', 'updated', 'saved', 'archived') ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeMemos(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'keep' AND state IN ('new', 'updated', 'saved') ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeActiveKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'keep' AND state = 'archived' ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeArchivedKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND itemType = 'task' AND state = 'completed' ORDER BY isStarred DESC, sortScore DESC, lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeCompletedTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE isVisible = 1 AND state IN ('new', 'updated') ORDER BY lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeNewItems(): Flow<List<SavedItem>>

    /** Count of new/updated items of a given type, for the status notification summary. */
    @Query("SELECT COUNT(*) FROM saved_item WHERE isVisible = 1 AND itemType = :itemType AND state IN ('new', 'updated')")
    suspend fun countNewByType(itemType: String): Int

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

    @Query("UPDATE saved_item SET isStarred = :starred, lastUpdateTimestamp = :ts WHERE savedItemId = :savedItemId")
    suspend fun setStarred(savedItemId: String, starred: Boolean, ts: Long)

    @Query("UPDATE saved_item SET doAtMs = :doAtMs, lastUpdateTimestamp = :ts WHERE savedItemId = :savedItemId")
    suspend fun setDoDate(savedItemId: String, doAtMs: Long, ts: Long)

    /** Explicit user acknowledgment of a New/Updated item: exits review state and moves the change cursor. */
    @Query("UPDATE saved_item SET state = 'saved', lastViewedChangeAt = :ts, lastUpdateTimestamp = :ts WHERE savedItemId = :savedItemId")
    suspend fun acknowledgeReview(savedItemId: String, ts: Long)

    @Query("UPDATE saved_item SET sortScore = :sortScore, reRankHistory = :reRankHistory WHERE savedItemId = :savedItemId")
    suspend fun updateSortScoreAndHistory(savedItemId: String, sortScore: Float, reRankHistory: String)

    @Query("UPDATE saved_item SET buttons = :buttons WHERE savedItemId = :savedItemId")
    suspend fun updateButtons(savedItemId: String, buttons: String)

    /** Account-switch wipe: hard-deletes all saved items (cascades links + change log). */
    @Query("DELETE FROM saved_item")
    suspend fun deleteAllForAccountSwitch()
}
