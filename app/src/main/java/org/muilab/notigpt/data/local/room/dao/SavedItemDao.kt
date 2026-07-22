package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState

data class BucketCount(
    val todos: Int = 0,
    val keeps: Int = 0,
) {
    val total: Int get() = todos + keeps
}

data class SmartFilterCounts(
    @Embedded(prefix = "all_") val all: BucketCount = BucketCount(),
    @Embedded(prefix = "due_") val dueSoon: BucketCount = BucketCount(),
    @Embedded(prefix = "recent_") val recentlyUpdated: BucketCount = BucketCount(),
    @Embedded(prefix = "starred_") val starred: BucketCount = BucketCount(),
)

@Dao
interface SavedItemDao {
    companion object {
        const val ATTENTION_ORDER = """
            isStarred DESC,
            CASE WHEN deadlineAtMs > 0 THEN deadlineAtMs ELSE 9223372036854775807 END ASC,
            lastUpdateTimestamp DESC,
            savedItemId DESC
        """
    }

    @Upsert
    suspend fun upsert(item: SavedItem)

    @Query("SELECT * FROM saved_item ORDER BY $ATTENTION_ORDER")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'todo' AND state IN ('new', 'updated', 'saved', 'completed') ORDER BY $ATTENTION_ORDER")
    fun observeTodos(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved', 'archived') ORDER BY $ATTENTION_ORDER")
    fun observeKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved') ORDER BY $ATTENTION_ORDER")
    fun observeActiveKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state = 'archived' ORDER BY $ATTENTION_ORDER")
    fun observeArchivedKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'todo' AND state = 'completed' ORDER BY $ATTENTION_ORDER")
    fun observeCompletedTodos(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated') ORDER BY lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeNewItems(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated')")
    suspend fun getNewItems(): List<SavedItem>

    @Query(
        """
        SELECT
          IFNULL(SUM(CASE WHEN itemType = 'todo' THEN 1 ELSE 0 END), 0) AS all_todos,
          IFNULL(SUM(CASE WHEN itemType = 'keep' THEN 1 ELSE 0 END), 0) AS all_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'todo' AND deadlineAtMs > 0 AND deadlineAtMs < :dueEndMs THEN 1 ELSE 0 END), 0) AS due_todos,
          0 AS due_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'todo' AND lastUpdateTimestamp >= :recentCutoffMs THEN 1 ELSE 0 END), 0) AS recent_todos,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND lastUpdateTimestamp >= :recentCutoffMs THEN 1 ELSE 0 END), 0) AS recent_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'todo' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_todos,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_keeps
        FROM saved_item
        WHERE state IN ('new', 'updated', 'saved')
        """
    )
    fun observeSmartFilterCounts(dueEndMs: Long, recentCutoffMs: Long): Flow<SmartFilterCounts>

    @Query("SELECT COUNT(*) FROM saved_item WHERE itemType = 'todo' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveTodoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveKeepCount(): Flow<Int>

    @Query("SELECT * FROM saved_item WHERE savedItemId = :savedItemId")
    suspend fun getById(savedItemId: String): SavedItem?

    @Query("SELECT * FROM saved_item WHERE savedItemId IN (:savedItemIds)")
    suspend fun getByIds(savedItemIds: List<String>): List<SavedItem>

    @Query("SELECT * FROM saved_item")
    suspend fun getAll(): List<SavedItem>

    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated', 'saved')")
    suspend fun getAllActive(): List<SavedItem>

    @Query("DELETE FROM saved_item WHERE savedItemId = :savedItemId")
    suspend fun hardDeleteById(savedItemId: String)

    @Query("DELETE FROM saved_item WHERE savedItemId IN (:savedItemIds)")
    suspend fun hardDeleteByIds(savedItemIds: List<String>)

    @Query("UPDATE saved_item SET state = :state, syncModifiedAt = :ts WHERE savedItemId = :savedItemId")
    suspend fun setState(savedItemId: String, state: String, ts: Long)

    @Query("UPDATE saved_item SET state = 'saved', syncModifiedAt = :ts WHERE savedItemId IN (:savedItemIds)")
    suspend fun markSavedByIds(savedItemIds: List<String>, ts: Long)

    suspend fun setCompleted(savedItemId: String, completed: Boolean, ts: Long) {
        setState(savedItemId, if (completed) SavedItemState.Completed else SavedItemState.Saved, ts)
    }

    @Query("UPDATE saved_item SET state = 'saved' WHERE savedItemId = :savedItemId")
    suspend fun setViewed(savedItemId: String)

    @Query("UPDATE saved_item SET isStarred = :starred, syncModifiedAt = :ts WHERE savedItemId = :savedItemId")
    suspend fun setStarred(savedItemId: String, starred: Boolean, ts: Long)

    @Query("UPDATE saved_item SET deadlineAtMs = :deadlineAtMs, lastUpdateTimestamp = :ts, syncModifiedAt = :ts WHERE savedItemId = :savedItemId")
    suspend fun setDeadline(savedItemId: String, deadlineAtMs: Long, ts: Long)

    @Query("UPDATE saved_item SET state = 'saved', lastViewedChangeAt = :ts, syncModifiedAt = :ts WHERE savedItemId = :savedItemId")
    suspend fun acknowledgeReview(savedItemId: String, ts: Long)

    @Query("UPDATE saved_item SET state = 'saved', lastViewedChangeAt = :ts, syncModifiedAt = :ts WHERE savedItemId IN (:savedItemIds)")
    suspend fun acknowledgeReviewByIds(savedItemIds: List<String>, ts: Long)

    @Query("UPDATE saved_item SET buttons = :buttons, lastUpdateTimestamp = :ts, syncModifiedAt = :ts WHERE savedItemId = :savedItemId")
    suspend fun updateButtons(savedItemId: String, buttons: String, ts: Long)

    @Query("DELETE FROM saved_item")
    suspend fun deleteAllForAccountSwitch()
}
