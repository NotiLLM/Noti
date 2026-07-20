package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.NotiSavedItemLink

/** Per-notiKey count of linked active saved items by type, for the notification card badge. */
data class LinkedTypeCount(val notiKey: String, val taskCount: Int, val keepCount: Int)

/**
 * Local access layer for the noti-to-saved-item join table.
 *
 * This is the sole source of truth for which notification records back a saved item. Resolve provenance
 * in both directions (saved item -> its notis, noti -> its saved items) through this DAO instead of
 * parsing serialized id sets.
 */
@Dao
interface NotiSavedItemLinkDao {

    /**
     * Links are add-only: re-citing an already-linked record is a no-op (unique index on
     * savedItemId+notiRecordId+role), and existing evidence is never displaced by later responses.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<NotiSavedItemLink>): List<Long>

    @Query("SELECT * FROM noti_saved_item_link WHERE savedItemId = :savedItemId")
    suspend fun getBySavedItemId(savedItemId: String): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE savedItemId IN (:savedItemIds)")
    suspend fun getBySavedItemIds(savedItemIds: List<String>): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE notiKey = :notiKey")
    suspend fun getByNotiKey(notiKey: String): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE notiRecordId = :notiRecordId")
    suspend fun getByNotiRecordId(notiRecordId: String): List<NotiSavedItemLink>

    @Query("SELECT DISTINCT savedItemId FROM noti_saved_item_link WHERE notiKey = :notiKey")
    suspend fun getSavedItemIdsByNotiKey(notiKey: String): List<String>

    /**
     * Live per-notiKey task/keep counts of linked *active* saved items (excludes completed/archived),
     * de-duplicated so a saved item linked via several records counts once. Drives the card badge.
     */
    @Query(
        """
        SELECT notiKey AS notiKey,
            SUM(CASE WHEN itemType = 'task' THEN 1 ELSE 0 END) AS taskCount,
            SUM(CASE WHEN itemType = 'keep' THEN 1 ELSE 0 END) AS keepCount
        FROM (
            SELECT DISTINCT l.notiKey AS notiKey, l.savedItemId AS savedItemId, s.itemType AS itemType
            FROM noti_saved_item_link l
            JOIN saved_item s ON s.savedItemId = l.savedItemId
            WHERE s.state != 'archived' AND s.state != 'completed'
        )
        GROUP BY notiKey
        """
    )
    fun observeLinkedTypeCounts(): Flow<List<LinkedTypeCount>>

    @Query("SELECT DISTINCT savedItemId FROM noti_saved_item_link WHERE notiKey IN (:notiKeys)")
    suspend fun getSavedItemIdsByNotiKeys(notiKeys: List<String>): List<String>

    @Query("SELECT DISTINCT savedItemId FROM noti_saved_item_link WHERE notiRecordId = :notiRecordId")
    suspend fun getSavedItemIdsByRecordId(notiRecordId: String): List<String>

    @Query("DELETE FROM noti_saved_item_link WHERE savedItemId = :savedItemId")
    suspend fun deleteBySavedItemId(savedItemId: String)

    @Query("DELETE FROM noti_saved_item_link WHERE notiRecordId = :notiRecordId")
    suspend fun deleteByNotiRecordId(notiRecordId: String)

    @Query("DELETE FROM noti_saved_item_link WHERE linkId IN (:linkIds)")
    suspend fun deleteByIds(linkIds: List<Long>)

    @Query("DELETE FROM noti_saved_item_link")
    suspend fun deleteAll()
}
