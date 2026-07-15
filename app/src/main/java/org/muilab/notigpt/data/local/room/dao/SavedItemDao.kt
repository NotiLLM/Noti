package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState

/** Task/keep split for one smart-filter bucket. */
data class BucketCount(
    val tasks: Int = 0,
    val keeps: Int = 0,
) {
    val total: Int get() = tasks + keeps
}

/**
 * Home-screen smart-filter counts from [SavedItemDao.observeSmartFilterCounts], each bucket split by
 * item type so the UI can show a "N tasks · M keeps" breakdown. Keeps with a real do-date land in the
 * date buckets alongside tasks; keeps with no do-date default into [someday]. [undetermined] is
 * task-only (unscheduled tasks).
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
 *
 * List order is date-driven: starred first, then by the item's nearest actionable date (the
 * earlier of a concrete do-date and the deadline; the Someday sentinel and unset dates sort
 * last), then recency. See [DATE_ORDER].
 */
@Dao
interface SavedItemDao {

    companion object {
        /**
         * Shared ORDER BY: starred pins to the top; dated items sort by their nearest actionable
         * date ascending; undated items (and keeps, which carry no dates) fall back to recency.
         * [SavedItem.DO_AT_SOMEDAY] is excluded as a concrete date so Someday items sort with the
         * undated tail.
         */
        const val DATE_ORDER = """
            isStarred DESC,
            CASE
                WHEN doAtMs > 0 AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} AND deadlineAtMs > 0 THEN MIN(doAtMs, deadlineAtMs)
                WHEN doAtMs > 0 AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} THEN doAtMs
                WHEN deadlineAtMs > 0 THEN deadlineAtMs
                ELSE ${SavedItem.DO_AT_SOMEDAY}
            END ASC,
            lastUpdateTimestamp DESC, savedItemId DESC
        """
    }

    @Upsert
    suspend fun upsert(reminder: SavedItem)

    @Query("SELECT * FROM saved_item ORDER BY $DATE_ORDER")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'task' AND state IN ('new', 'updated', 'saved', 'completed') ORDER BY $DATE_ORDER")
    fun observeTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved', 'archived') ORDER BY $DATE_ORDER")
    fun observeMemos(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved') ORDER BY $DATE_ORDER")
    fun observeActiveKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'keep' AND state = 'archived' ORDER BY $DATE_ORDER")
    fun observeArchivedKeeps(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE itemType = 'task' AND state = 'completed' ORDER BY $DATE_ORDER")
    fun observeCompletedTasks(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated') ORDER BY lastUpdateTimestamp DESC, savedItemId DESC")
    fun observeNewItems(): Flow<List<SavedItem>>

    /** Legacy new/updated rows (single-item regeneration), for review counting alongside staged ops. */
    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated')")
    suspend fun getNewItems(): List<SavedItem>

    /**
     * Home-screen smart-filter counts, bucketed by planned date ([doAtMs]) and split by item type.
     * Completed/archived items are excluded. Bucket boundaries mirror [SavedItem.plannedBucket]; keep
     * them in lockstep. [startOfTomorrowMs] is local midnight so "today & earlier" absorbs overdue
     * planned dates. Keeps with a real do-date land in the date buckets same as tasks; keeps with no
     * do-date fall into `someday`. `undetermined` is task-only (unscheduled tasks).
     */
    @Query(
        """
        SELECT
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs > 0 AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} AND doAtMs < :startOfTomorrowMs THEN 1 ELSE 0 END), 0) AS today_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND doAtMs > 0 AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} AND doAtMs < :startOfTomorrowMs THEN 1 ELSE 0 END), 0) AS today_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs >= :startOfTomorrowMs AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} THEN 1 ELSE 0 END), 0) AS upcoming_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND doAtMs >= :startOfTomorrowMs AND doAtMs != ${SavedItem.DO_AT_SOMEDAY} THEN 1 ELSE 0 END), 0) AS upcoming_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs = ${SavedItem.DO_AT_SOMEDAY} THEN 1 ELSE 0 END), 0) AS someday_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND (doAtMs <= 0 OR doAtMs = ${SavedItem.DO_AT_SOMEDAY}) THEN 1 ELSE 0 END), 0) AS someday_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND doAtMs <= 0 THEN 1 ELSE 0 END), 0) AS undetermined_tasks,
          0 AS undetermined_keeps,
          IFNULL(SUM(CASE WHEN itemType = 'task' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_tasks,
          IFNULL(SUM(CASE WHEN itemType = 'keep' AND isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred_keeps
        FROM saved_item
        WHERE state IN ('new', 'updated', 'saved')
        """
    )
    fun observeSmartFilterCounts(startOfTomorrowMs: Long): Flow<SmartFilterCounts>

    /** Live count of active (non-completed) tasks, for the drawer's Tasks entry badge. */
    @Query("SELECT COUNT(*) FROM saved_item WHERE itemType = 'task' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveTaskCount(): Flow<Int>

    /** Live count of active (non-archived) keeps, for the drawer's Keep entry badge. */
    @Query("SELECT COUNT(*) FROM saved_item WHERE itemType = 'keep' AND state IN ('new', 'updated', 'saved')")
    fun observeActiveKeepCount(): Flow<Int>

    @Query("SELECT * FROM saved_item WHERE savedItemId = :savedItemId")
    suspend fun getById(savedItemId: String): SavedItem?

    @Query("SELECT * FROM saved_item WHERE savedItemId IN (:savedItemIds)")
    suspend fun getByIds(savedItemIds: List<String>): List<SavedItem>

    @Query("SELECT * FROM saved_item")
    suspend fun getAll(): List<SavedItem>

    /** Active items only — the merge stages' candidate pool (completed/archived excluded). */
    @Query("SELECT * FROM saved_item WHERE state IN ('new', 'updated', 'saved')")
    suspend fun getAllActive(): List<SavedItem>

    /** User deletion is a hard delete; links and change logs cascade, sub-items are deleted by the repository. */
    @Query("DELETE FROM saved_item WHERE savedItemId = :savedItemId")
    suspend fun hardDeleteById(savedItemId: String)

    @Query("DELETE FROM saved_item WHERE savedItemId IN (:savedItemIds)")
    suspend fun hardDeleteByIds(savedItemIds: List<String>)

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

    @Query("UPDATE saved_item SET buttons = :buttons WHERE savedItemId = :savedItemId")
    suspend fun updateButtons(savedItemId: String, buttons: String)

    /** Account-switch wipe: hard-deletes all saved items (cascades links + change log). */
    @Query("DELETE FROM saved_item")
    suspend fun deleteAllForAccountSwitch()
}
