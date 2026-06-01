package org.muilab.notigpt.repository.noti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.search.NotiSearchQueryBuilder
import org.muilab.notigpt.model.notifications.NotiRecord

/**
 * Repository slice for reading notification record history and search context.
 *
 * Keep record-query shapes here so ViewModels and cards do not call DAOs directly. Write-side lifecycle changes
 * belong to NotiActionsRepository or maintenance/grouping slices.
 */
class NotiRecordsRepository(
    private val notiRecordDao: NotiRecordDao,
) {

    fun getActiveRecordsCountForKey(notiKey: String): Int {
        return notiRecordDao.getActiveRecordsByKey(notiKey).size
    }

    fun getActiveRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        val recs = notiRecordDao.getActiveRecordsByKey(notiKey).sortedBy { it.time }
        return recs.takeLast(limit.coerceAtLeast(1)).map { it.notiRecordId }
    }

    fun getPreviewRecordsForKeys(keys: List<String>, perKeyLimit: Int = 3): List<NotiRecord> {
        if (keys.isEmpty()) return emptyList()
        val all = notiRecordDao.getActiveRecordsByKeys(keys)
        return all.groupBy { it.notiKey }
            .flatMap { (_, list) ->
                list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                    .take(perKeyLimit)
            }
    }

    fun activeRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return notiRecordDao.getActiveRecordsFlowByKey(notiKey)
            .map { it.sortedBy { r -> r.time } }
    }

    suspend fun fetchActiveRecordsForKey(notiKey: String): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRecordDao.getActiveRecordsByKey(notiKey).sortedBy { it.time }
        }
    }

    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return notiRecordDao.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
        limit: Int = 10,
    ): List<NotiRecord> {
        return if (isOlder) {
            notiRecordDao.getContextOlder(notiKey, pivotTime, limit).sortedBy { it.time }
        } else {
            notiRecordDao.getContextNewer(notiKey, pivotTime, limit)
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

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long): Boolean {
        return notiRecordDao.hasRecordsInGap(notiKey, minTime, maxTime) > 0
    }

    suspend fun getLatestRecords(limit: Int): List<NotiRecord> {
        return notiRecordDao.getLatestRecords(limit)
    }

    suspend fun getRecordsBefore(pivotTime: Long, limit: Int): List<NotiRecord> {
        return notiRecordDao.getRecordsBefore(pivotTime, limit)
    }

    suspend fun getRecordsAfter(pivotTime: Long, limit: Int): List<NotiRecord> {
        return notiRecordDao.getRecordsAfter(pivotTime, limit)
    }

    // Advanced Search Logic: always searches across all records.
    suspend fun searchNotifications(rawInput: String): Map<String, List<NotiRecord>> {
        val built = NotiSearchQueryBuilder.build(rawInput = rawInput)
        val records = notiRecordDao.searchRecordsRaw(built.toSQLiteQuery())
        return records.groupBy { it.notiKey }
    }
}
