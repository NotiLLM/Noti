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

    @Query("SELECT * FROM noti_record WHERE notiKey IN ( :notiKeys )")
    fun getRecordsByKeys(notiKeys: List<String>): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isDismissed = 0 ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END ASC")
    fun getActiveRecordsByKey(notiKey: String): List<NotiRecord>

    /** Latest records the user has kept visible, newest first, for bounded manual extraction context. */
    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isDismissed = 0 ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END DESC LIMIT :limit")
    fun getLatestActiveRecordsByKey(notiKey: String, limit: Int): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND isDismissed = 0 ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END ASC")
    fun getNewRecordsByKey(notiKey: String): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END DESC")
    fun getNewRecords(): List<NotiRecord>

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey IN ( :notiKeys ) ORDER BY notiKey ASC, CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END ASC")
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

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey IN ( :notiKeys ) ORDER BY notiKey ASC, CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END ASC")
    fun getActiveRecordsFlowByKeys(notiKeys: List<String>): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record WHERE isDismissed = 0 AND notiKey = :notiKey ORDER BY whenTime ASC")
    fun getActiveRecordsFlowByKey(notiKey: String): Flow<List<NotiRecord>>

    @Query("SELECT * FROM noti_record")
    fun getAllRecords(): List<NotiRecord>

    /** Irreversibly removes all raw device-local notification content. */
    @Query("DELETE FROM noti_record")
    suspend fun deleteAll()

    @Insert
    fun insertAllRecords(notiRecords: List<NotiRecord>)

    // --- Per-notiKey pipeline: fold-watermark based record selection ---
    // "Unprocessed" records are those posted after the notiKey's fold watermark
    // (extraction_journal_summary.lastFoldedPostTime); they ride along in scan/extraction
    // requests until a summary-generation (fold) run advances the watermark past them.

    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND postTime > :afterPostTime ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END ASC")
    fun getRecordsByKeyAfter(notiKey: String, afterPostTime: Long): List<NotiRecord>

    @Query("SELECT COUNT(*) FROM noti_record WHERE notiKey = :notiKey AND postTime > :afterPostTime")
    fun getRecordCountByKeyAfter(notiKey: String, afterPostTime: Long): Int

    /** Already-folded records (at or before the watermark), newest first — sent as past context. */
    @Query("SELECT * FROM noti_record WHERE notiKey = :notiKey AND postTime <= :atPostTime ORDER BY CASE WHEN whenTime != 0 THEN whenTime ELSE postTime END DESC LIMIT :limit")
    fun getRecordsByKeyBeforeOrAt(notiKey: String, atPostTime: Long, limit: Int): List<NotiRecord>

    @Query("SELECT COUNT(*) FROM noti_record WHERE notiKey = :notiKey")
    fun getRecordCountByKey(notiKey: String): Int

    /** Newest post time for a key; null when the key has no records. Drives compaction/idle checks. */
    @Query("SELECT MAX(postTime) FROM noti_record WHERE notiKey = :notiKey")
    fun getLastRecordPostTimeByKey(notiKey: String): Long?

    /** Newest record time for a key; null when the key has no records. Drives idle-thread checks. */
    @Query("SELECT MAX(whenTime) FROM noti_record WHERE notiKey = :notiKey")
    fun getLastRecordTimeByKey(notiKey: String): Long?

    /**
     * Live count of unprocessed records for active extraction threads; drives the offline/pending
     * banner. A record is unprocessed while its postTime is beyond the key's fold watermark.
     */
    @Query(
        """
        SELECT COUNT(*) FROM noti_record r
        WHERE r.notiKey IN (
            SELECT s.notiKey FROM noti_llm_state s
            JOIN noti_drawer d ON d.notiKey = s.notiKey
            WHERE d.isDismissed = 0 AND s.shouldExtractSavedItem = 1
        )
        AND r.postTime > COALESCE(
            (SELECT j.lastFoldedPostTime FROM extraction_journal_summary j WHERE j.notiKey = r.notiKey),
            0
        )
        """
    )
    fun observePendingExtractionCount(): kotlinx.coroutines.flow.Flow<Int>

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
