package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiRecord

@Dao
interface NotiRecordDao {
    @Upsert
    fun upsert(notiRecord: NotiRecord)

    @Query("SELECT DISTINCT notiKey FROM noti_record WHERE isVisible = 1")
    fun getVisibleRecordsKeys(): List<String>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey")
    fun getRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND whenTime > :timestamp")
    fun getNotSyncedRecordsByKey(notiKey: String, timestamp: Long): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey IN ( :notiKeys )")
    fun getRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isVisible = 1")
    fun getVisibleRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE isVisible = 1 AND notiKey IN ( :notiKeys )")
    fun getVisibleRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    // Fetch the latest visible record per notiKey using a join against a grouped subquery (MAX(whenTime)).
    @Query("""
        SELECT r.* FROM noti_record r
        INNER JOIN (
            SELECT notiKey, MAX(whenTime) AS max_when
            FROM noti_record
            WHERE isVisible = 1 AND notiKey IN (:notiKeys)
            GROUP BY notiKey
        ) grp ON r.notiKey = grp.notiKey AND r.whenTime = grp.max_when
        WHERE r.isVisible = 1 AND r.notiKey IN (:notiKeys)
    """)
    fun getLatestVisibleRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("UPDATE noti_record SET isVisible = 0, isRead = 1 WHERE notiKey = :notiKey")
    suspend fun setRecordsInvisibleByKey(notiKey: String)

    @Query("UPDATE noti_record SET isVisible = 0, isRead = 1 WHERE notiKey IN ( :notiKeys)")
    fun setRecordsInvisibleByKeys(notiKeys: List<String>)

    @Query("UPDATE noti_record SET isRead = 1 WHERE notiRecordId IN ( :notiRecordIds)")
    fun setRecordsReadByIds(notiRecordIds: List<String>)

    @Query("UPDATE noti_record SET isRead = 1 WHERE notiKey = :notiKey")
    suspend fun setRecordsReadByKey(notiKey: String)

    @Query("SELECT * FROM noti_record WHERE isVisible = 1 AND notiKey IN ( :notiKeys )")
    fun getVisibleRecordsFlowByKeys(notiKeys: List<String>): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record WHERE isVisible = 1 AND notiKey = :notiKey ORDER BY whenTime ASC")
    fun getVisibleRecordsFlowByKey(notiKey: String): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record")
    fun getAllRecords(): List<NotiRecord>

    @Query("""
        UPDATE noti_record
        SET isVisible = 0
        WHERE notiRecordId IN (
            SELECT notiRecordId FROM (
                SELECT
                    notiRecordId,
                    ROW_NUMBER() OVER(PARTITION BY notiKey ORDER BY postTime DESC) as row_num
                FROM noti_record
                WHERE isVisible = 1 AND isRead = 1 AND postTime < :expireTimestamp
            )
            WHERE row_num > :maxCount
        )
    """)
    suspend fun removeExpiredReadRecords(expireTimestamp: Long, maxCount: Int)

    @Insert
    fun insertAllRecords(notiRecords: List<NotiRecord>)

    // --- New helper queries for task detection/extraction flags ---
    @Query("UPDATE noti_record SET taskScanned = 1 WHERE notiRecordId IN (:notiRecordIds)")
    fun setRecordsTaskScannedByIds(notiRecordIds: List<String>)

    @Query("UPDATE noti_record SET taskExtracted = 1 WHERE notiRecordId IN (:notiRecordIds)")
    fun setRecordsTaskExtractedByIds(notiRecordIds: List<String>)

    @Query("UPDATE noti_record SET taskScanned = 1 WHERE notiKey = :notiKey AND whenTime > :sinceTime")
    fun setRecordsTaskScannedByKeySince(notiKey: String, sinceTime: Long)

    @Query("UPDATE noti_record SET taskExtracted = 1 WHERE notiKey = :notiKey AND whenTime > :sinceTime")
    fun setRecordsTaskExtractedByKeySince(notiKey: String, sinceTime: Long)

    @Query("UPDATE noti_record SET taskScanned = 1, taskExtracted = 1")
    fun setAllScannedAndExtractedTrue()

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND taskScanned = 0")
    fun getUnscannedRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND taskExtracted = 0")
    fun getUnextractedRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND taskScanned = 1 ORDER BY whenTime DESC LIMIT :limit")
    fun getLastScannedRecordsByKey(notiKey: String, limit: Int): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND taskExtracted = 1 ORDER BY whenTime DESC LIMIT :limit")
    fun getLastExtractedRecordsByKey(notiKey: String, limit: Int): List<NotiRecord>

    // --- Claiming helpers (for robust extraction) ---
    @Query("UPDATE noti_record SET taskExtractionClaimed = 1, taskExtractionClaimedAt = :ts WHERE notiRecordId IN (:ids) AND taskExtracted = 0 AND taskExtractionClaimed = 0")
    fun claimRecordsForExtractionWithTs(ids: List<String>, ts: Long): Int

    @Query("SELECT * FROM noti_record WHERE notiRecordId IN (:ids) AND taskExtractionClaimed = 1 AND taskExtracted = 0")
    fun getClaimedRecordsByIds(ids: List<String>): List<NotiRecord>

    @Query("UPDATE noti_record SET taskExtracted = 1, taskExtractionClaimed = 0, taskExtractionClaimedAt = 0 WHERE notiRecordId IN (:ids)")
    fun setClaimedRecordsExtracted(ids: List<String>)

    @Query("UPDATE noti_record SET taskExtractionClaimed = 0, taskExtractionClaimedAt = 0 WHERE notiRecordId IN (:ids)")
    fun clearClaimedRecords(ids: List<String>)

    @Query("UPDATE noti_record SET taskExtractionClaimed = 0, taskExtractionClaimedAt = 0 WHERE taskExtractionClaimed = 1 AND (:ts - taskExtractionClaimedAt) > :staleMs")
    fun reclaimStaleClaims(ts: Long, staleMs: Long)

    @Query("SELECT * FROM noti_record WHERE taskExtractionClaimed = 0 AND taskExtracted = 0 AND notiKey = :notiKey ORDER BY whenTime ASC")
    fun getUnclaimedUnextractedByKey(notiKey: String): List<NotiRecord>
}