package org.muilab.notigpt.repository.noti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.search.NotiSearchQueryBuilder
import org.muilab.notigpt.model.notifications.NotiRecord

/**
 * Read/query operations for NotiRecord.
 *
 * Extracted from NotiRepository to keep NotiRepository as an orchestrator/facade.
 */
class NotiRecordsRepository(
    private val notiRecordDao: NotiRecordDao,
) {

    fun getVisibleRecordsCountForKey(notiKey: String): Int {
        return notiRecordDao.getVisibleRecordsByKey(notiKey).size
    }

    fun getVisibleRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        val recs = notiRecordDao.getVisibleRecordsByKey(notiKey).sortedBy { it.time }
        return recs.takeLast(limit.coerceAtLeast(1)).map { it.notiRecordId }
    }

    @Suppress("unused")
    private suspend fun fetchLatestRecordsConcurrently(keys: List<String>, perKeyLimit: Int = 1): List<NotiRecord> {
        if (keys.isEmpty()) return emptyList()
        val safeThreshold = 900
        return if (keys.size <= safeThreshold) {
            withContext(Dispatchers.IO) {
                val allRecs = notiRecordDao.getVisibleRecordsByKeys(keys)
                val grouped = allRecs.groupBy { it.notiKey }
                grouped.flatMap { (_, list) ->
                    list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                        .take(perKeyLimit)
                }
            }
        } else {
            coroutineScope {
                val deferred = keys.chunked(safeThreshold).map { chunk ->
                    async(Dispatchers.IO) {
                        notiRecordDao.getVisibleRecordsByKeys(chunk)
                    }
                }
                val results = deferred.awaitAll().flatten()
                val grouped = results.groupBy { it.notiKey }
                grouped.flatMap { (_, list) ->
                    list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                        .take(perKeyLimit)
                }
            }
        }
    }

    fun getPreviewRecordsForKeys(keys: List<String>, perKeyLimit: Int = 3): List<NotiRecord> {
        if (keys.isEmpty()) return emptyList()
        val all = notiRecordDao.getVisibleRecordsByKeys(keys)
        return all.groupBy { it.notiKey }
            .flatMap { (_, list) ->
                list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                    .take(perKeyLimit)
            }
    }

    fun visibleRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return notiRecordDao.getVisibleRecordsFlowByKey(notiKey)
            .map { it.sortedBy { r -> r.time } }
    }

    suspend fun fetchVisibleRecordsForKey(notiKey: String): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRecordDao.getVisibleRecordsByKey(notiKey).sortedBy { it.time }
        }
    }

    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return notiRecordDao.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
        includeHistory: Boolean,
        limit: Int = 10,
    ): List<NotiRecord> {
        return if (isOlder) {
            notiRecordDao.getContextOlder(notiKey, pivotTime, limit, includeHistory).sortedBy { it.time }
        } else {
            notiRecordDao.getContextNewer(notiKey, pivotTime, limit, includeHistory)
        }
    }

    suspend fun getGapRecords(
        notiKey: String,
        minTime: Long,
        maxTime: Long,
        limit: Int,
        fromStart: Boolean,
    ): List<NotiRecord> {
        return if (fromStart) {
            notiRecordDao.getGapRecordsNewer(notiKey, minTime, maxTime, limit)
        } else {
            notiRecordDao.getGapRecordsOlder(notiKey, minTime, maxTime, limit)
        }
    }

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long, includeHistory: Boolean): Boolean {
        return notiRecordDao.hasRecordsInGap(notiKey, minTime, maxTime, includeHistory) > 0
    }

    // Advanced Search Logic
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun searchNotifications(rawInput: String, includeHistory: Boolean): Map<String, List<NotiRecord>> {
        val built = NotiSearchQueryBuilder.build(rawInput = rawInput, includeHistory = includeHistory)
        val records = notiRecordDao.searchRecordsRaw(built.toSQLiteQuery())
        return records.groupBy { it.notiKey }
    }
}
