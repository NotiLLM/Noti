package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState

/** One (itemType, state) group count from [SavedItemDao.observeReviewCounts]. */
data class TypeStateCount(val itemType: String, val state: String, val cnt: Int)

/** Task/keep split for one smart-filter bucket. */
data class BucketCount(
    val tasks: Int = 0,
    val keeps: Int = 0,
) {
    val total: Int get() = tasks + keeps
}

/**
 * Home-screen smart-filter counts from [SavedItemDao.observeSmartFilterCounts], each bucket split by
 * item type so the UI can show a "N tasks · M keeps" breakdown. Keeps have no do-date, so they only
 * ever land in [someday] (or [starred]); the date buckets are task-only.
 */
data class SmartFilterCounts(
    @Embedded(prefix = "today_") val todayEarlier: BucketCount = BucketCount(),
    @Embedded(prefix = "upcoming_") val upcoming: BucketCount = BucketCount(),
    @Embedded(prefix = "someday_") val someday: BucketCount = BucketCount(),
    @Embedded(prefix = "undetermined_") val undetermined: BucketCount = BucketCount(),
    @Embedded(prefix = "starred_") val starred: BucketCount = BucketCount(),
)

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

    /** Per-(type, state) counts of items awaiting review, for the home-screen review row summary. */
    @Query("SELECT itemType, state, COUNT(*) AS cnt FROM saved_item WHERE isVisible = 1 AND state IN ('new', 'updated') GROUP BY itemType, state")
    fun observeReviewCounts(): Flow<List<TypeStateCount>>

    /**
     * Home-screen smart-filter counts, bucketed by planned date ([doAtMs]) and split by item type.
     * Completed/archived items are excluded. Bucket boundaries mirror [SavedItem.plannedBucket]; keep
     * them in lockstep. [startOfTomorrowMs] is local midnight so "today & earlier" absorbs overdue
     * planned dates. Keeps have no do-date, so they only fall into `someday`/`starred`; the date
     * buckets are task-only (`undetermined` = unscheduled tasks).
     */
    @Query(
        """
        SELECT
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs > 0 AND doAtMs < :startOfTomorrowMs THEN 1 ELSE 0 END), 0) AS today_tasks,
          0 AS today_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs >= :startOfTomorrowMs AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} THEN 1 ELSE 0 END), 0) AS upcoming_tasks,
          0 AS upcoming_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs = ${SavedItem.DO_AT_SOMEDAY} THEN 1 ELSE 0 END), 0) AS someday_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' THEN 1 ELSE 0 END), 0) AS someday_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs <= 0 THEN 1 ELSE 0 END), 0) AS undetermined_tasks,
          0 AS undetermined_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_keeps
        FROM saved_item
        WHERE isVisible = 1 AND state IN ('new', 'updated', 'saved')
        """
    )
    fun observeSmartFilterCounts(startOfTomorrowMs: Long): Flow<SmartFilterCounts>

    /** Live count of active (non-completed) tasks, for the drawer's Tasks entry badge. */
    @Query("SELECT COUNT(*) FROM saved_item WHERE isVisible = 1 AND itemType = 'task' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveTaskCount(): Flow<Int>

    /** Live count of active (non-archived) keeps, for the drawer's Keep entry badge. */
    @Query("SELECT COUNT(*) FROM saved_item WHERE isVisible = 1 AND itemType = 'keep' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveKeepCount(): Flow<Int>

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

    /** Batch acknowledgment for "approve all" in the review screen. */
    @Query("UPDATE saved_item SET state = 'saved', lastViewedChangeAt = :ts, lastUpdateTimestamp = :ts WHERE savedItemId IN (:savedItemIds)")
    suspend fun acknowledgeReviewByIds(savedItemIds: List<String>, ts: Long)

    @Query("UPDATE saved_item SET sortScore = :sortScore, reRankHistory = :reRankHistory WHERE savedItemId = :savedItemId")
    suspend fun updateSortScoreAndHistory(savedItemId: String, sortScore: Float, reRankHistory: String)

    @Query("UPDATE saved_item SET buttons = :buttons WHERE savedItemId = :savedItemId")
    suspend fun updateButtons(savedItemId: String, buttons: String)

    /** Account-switch wipe: hard-deletes all saved items (cascades links + change log). */
    @Query("DELETE FROM saved_item")
    suspend fun deleteAllForAccountSwitch()
}
