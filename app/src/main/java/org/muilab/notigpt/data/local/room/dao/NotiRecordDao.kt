package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiRecord

/**
 * Local access layer for immutable-ish notification record history.
 *
 * Records preserve the concrete notifications that fed scan, extraction, and reminder context.
 * Keep lifecycle flags here only when they describe processing of the record itself.
 */
@Dao
interface NotiRecordDao {
    @Upsert
    fun upsert(notiRecord: NotiRecord)

    @Query("SELECT DISTINCT notiKey FROM noti_record WHERE isDismissed = 0")
    fun getActiveRecordsKeys(): List<String>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey")
    fun getRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND whenTime > :timestamp")
    fun getNotSyncedRecordsByKey(notiKey: String, timestamp: Long): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey IN ( :notiKeys )")
    fun getRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isDismissed = 0")
    fun getActiveRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isNew = 1 ORDER BY whenTime ASC")
    fun getNewRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("UPDATE noti_record SET isNew = 0 WHERE notiKey = :notiKey AND isNew = 1")
    suspend fun markNewRecordsArchivedByKey(notiKey: String)

    @Query("SELECT * FROM noti_record WHERE isNew = 1 ORDER BY whenTime DESC")
    fun getNewRecords(): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey IN ( :notiKeys )")
    fun getActiveRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    // Fetch the latest active record per notiKey using a join against a grouped subquery (MAX(whenTime)).
    @Query("""
        SELECT r.* FROM noti_record r
        INNER JOIN (
            SELECT notiKey, MAX(whenTime) AS max_when
            FROM noti_record
            WHERE isDismissed = 0 AND notiKey IN (:notiKeys)
            GROUP BY notiKey
        ) grp ON r.notiKey = grp.notiKey AND r.whenTime = grp.max_when
        WHERE r.isDismissed = 0 AND r.notiKey IN (:notiKeys)
    """)
    fun getLatestActiveRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("UPDATE noti_record SET isDismissed = 1 WHERE notiKey = :notiKey")
    suspend fun dismissRecordsByKey(notiKey: String)

    @Query("UPDATE noti_record SET isDismissed = 1 WHERE notiKey IN ( :notiKeys)")
    fun dismissRecordsByKeys(notiKeys: List<String>)

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey IN ( :notiKeys )")
    fun getActiveRecordsFlowByKeys(notiKeys: List<String>): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey = :notiKey ORDER BY whenTime ASC")
    fun getActiveRecordsFlowByKey(notiKey: String): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record")
    fun getAllRecords(): List<NotiRecord>

    @Query("""
        UPDATE noti_record
        SET isDismissed = 1
        WHERE notiRecordId IN (
            SELECT notiRecordId FROM (
                SELECT
                    notiRecordId,
                    ROW_NUMBER() OVER(PARTITION BY notiKey ORDER BY postTime DESC) as row_num
                FROM noti_record
                WHERE isDismissed = 0 AND postTime < :expireTimestamp
            )
            WHERE row_num > :maxCount
        )
    """)
    suspend fun dismissExpiredReadRecords(expireTimestamp: Long, maxCount: Int)

    @Insert
    fun insertAllRecords(notiRecords: List<NotiRecord>)

    // --- Task detection/extraction flags ---
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

    // Search: always searches across all records ever received.
    @Query("""
        SELECT * FROM noti_record 
        WHERE (
            extraText LIKE '%' || :query || '%' OR 
            extraBigText LIKE '%' || :query || '%' OR 
            extraTitle LIKE '%' || :query || '%' OR
            person LIKE '%' || :query || '%'
        )
        ORDER BY whenTime DESC
        LIMIT 100
    """)
    suspend fun searchRecords(query: String): List<NotiRecord>

    // Context: Get Older
    @Query("""
        SELECT * FROM noti_record 
        WHERE notiKey = :notiKey 
        AND whenTime < :pivotTime 
        ORDER BY whenTime DESC 
        LIMIT :limit
    """)
    suspend fun getContextOlder(notiKey: String, pivotTime: Long, limit: Int): List<NotiRecord>

    // Context: Get Newer
    @Query("""
        SELECT * FROM noti_record 
        WHERE notiKey = :notiKey 
        AND whenTime > :pivotTime 
        ORDER BY whenTime ASC 
        LIMIT :limit
    """)
    suspend fun getContextNewer(notiKey: String, pivotTime: Long, limit: Int): List<NotiRecord>

    // Dynamic Search using RawQuery
    @RawQuery
    suspend fun searchRecordsRaw(query: SupportSQLiteQuery): List<NotiRecord>

    // Fetch records between two timestamps for gap filling
    @Query("""
        SELECT * FROM noti_record 
        WHERE notiKey = :notiKey 
        AND whenTime > :startAtMs AND whenTime < :endAtMs
        ORDER BY whenTime ASC
    """)
    suspend fun getRecordsBetween(notiKey: String, startAtMs: Long, endAtMs: Long): List<NotiRecord>

    @Query("""
        SELECT * FROM noti_record 
        WHERE notiKey = :notiKey 
        AND whenTime > :minTime AND whenTime < :maxTime 
        ORDER BY whenTime ASC 
        LIMIT :limit
    """)
    suspend fun getGapRecordsNewer(notiKey: String, minTime: Long, maxTime: Long, limit: Int): List<NotiRecord>

    // Fetch records immediately BEFORE maxTime (Descending) -> "Load More "
    @Query("""
        SELECT * FROM noti_record 
        WHERE notiKey = :notiKey 
        AND whenTime > :minTime AND whenTime < :maxTime 
        ORDER BY whenTime DESC 
        LIMIT :limit
    """)
    suspend fun getGapRecordsOlder(notiKey: String, minTime: Long, maxTime: Long, limit: Int): List<NotiRecord>

    // Check if any records exist in a given time gap (for button visibility)
    @Query("""
        SELECT COUNT(*) FROM noti_record
        WHERE notiKey = :notiKey
        AND whenTime > :minTime AND whenTime < :maxTime
        LIMIT 1
    """)
    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long): Int

    // --- History paging (Gmail-style "All Notifications") ---
    @Query("""
        SELECT * FROM noti_record
        ORDER BY whenTime DESC
        LIMIT :limit
    """)
    suspend fun getLatestRecords(limit: Int): List<NotiRecord>

    @Query("""
        SELECT * FROM noti_record
        WHERE whenTime < :pivotTime
        ORDER BY whenTime DESC
        LIMIT :limit
    """)
    suspend fun getRecordsBefore(pivotTime: Long, limit: Int): List<NotiRecord>

    @Query("""
        SELECT * FROM noti_record
        WHERE whenTime > :pivotTime
        ORDER BY whenTime ASC
        LIMIT :limit
    """)
    suspend fun getRecordsAfter(pivotTime: Long, limit: Int): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiRecordId IN (:ids)")
    fun getRecordsByIds(ids: List<String>): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiRecordId = :id LIMIT 1")
    fun getRecordById(id: String): NotiRecord?
}