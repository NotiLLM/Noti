package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiGroupDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.search.NotiSearchQueryBuilder
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getAppCategoryByAppName
import org.muilab.notigpt.repository.noti.NotiActionsRepository
import org.muilab.notigpt.repository.noti.NotiGroupingRepository
import org.muilab.notigpt.repository.noti.NotiGroupRepository

class NotiRepository(
    private val appContext: Context,
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiGroupDao: NotiGroupDao
) {

    private val groupingRepo = NotiGroupingRepository(
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
        notiGroupDao = notiGroupDao,
    )

    private val actionsRepo = NotiActionsRepository(
        appContext = appContext,
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
    )

    private val groupRepo = NotiGroupRepository(
        notiDrawerDao = notiDrawerDao,
        notiGroupDao = notiGroupDao,
    )

    suspend fun removeExpiredNotiRecords() {
        actionsRepo.removeExpiredNotiRecords()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupedNotifications(
        categoryFlow: Flow<String>,
        appCategoryFlow: Flow<String>,
    ): Flow<List<NotiDrawerItem>> {
        return groupingRepo.getGroupedNotifications(categoryFlow, appCategoryFlow)
    }

    // --- Group operations (delegated) ---
    suspend fun actOnGroup(groupId: String, action: String) {
        groupRepo.actOnGroup(groupId, action)
    }

    suspend fun merge(dragId: String, targetId: String) {
        groupRepo.merge(dragId, targetId)
    }

    suspend fun ungroup(groupId: String) {
        groupRepo.ungroup(groupId)
    }

    suspend fun updateGroupExpansion(groupId: String, expanded: Boolean) {
        groupRepo.updateGroupExpansion(groupId, expanded)
    }

    suspend fun updateGroupTitle(groupId: String, title: String) {
        groupRepo.updateGroupTitle(groupId, title)
    }

    suspend fun removeFromGroup(notiKey: String) {
        groupRepo.removeFromGroup(notiKey)
    }

    fun getVisibleNotiCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotiCountByCategory(category)
    }

    fun getVisibleNotReadNotificationCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotReadCountByCategory(category)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun upsertNotiUnit(context: Context, sbn: StatusBarNotification, isInit: Boolean) {
        actionsRepo.upsertNotiUnit(context, sbn, isInit)
    }

    fun updateNotiUnit(notiUnit: NotiUnit) {
        actionsRepo.updateNotiUnit(notiUnit)
    }

    suspend fun removeNotiUnit(notiKey: String) {
        actionsRepo.removeNotiUnit(notiKey)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun insertNotiRecord(sbn: StatusBarNotification) {
        actionsRepo.insertNotiRecord(sbn)
    }

    suspend fun markNotiRead(notiKey: String) {
        actionsRepo.markNotiRead(notiKey)
    }

    suspend fun setHasGenuineTask(notiKey: String, hasGenuine: Boolean) {
        actionsRepo.setHasGenuineTask(notiKey, hasGenuine)
    }

    suspend fun setPinnedState(notiKey: String, pinned: Boolean) {
        actionsRepo.setPinnedState(notiKey, pinned)
    }

    suspend fun recomputeShouldExtractForKey(notiKey: String) {
        val current = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val should = current.hasGenuineTask || current.isPinned
        notiDrawerDao.setShouldExtractTaskByKey(notiKey, should)
    }

    fun getVisibleNotificationKeys(): List<String> {
        return notiDrawerDao.getAllVisibleKeys()
    }

    fun getVisibleRecordsCountForKey(notiKey: String): Int {
        return notiRecordDao.getVisibleRecordsByKey(notiKey).size
    }

    fun getVisibleRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        val recs = notiRecordDao.getVisibleRecordsByKey(notiKey).sortedBy { it.time }
        return recs.takeLast(limit.coerceAtLeast(1)).map { it.notiRecordId }
    }

    fun requestRandomTaskExtraction(count: Int) {
        val keys = getVisibleNotificationKeys()
        if (keys.isEmpty() || count <= 0) return
        val randomKeys = keys.shuffled().take(count)
        if (randomKeys.isNotEmpty()) {
            enqueueTaskExtraction(appContext, randomKeys)
        }
    }

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
            .flatMap { (_, list) -> list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }.take(perKeyLimit) }
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

    // [NEW] Advanced Search Logic
    suspend fun searchNotifications(rawInput: String, includeHistory: Boolean): Map<String, List<NotiRecord>> {
        val built = NotiSearchQueryBuilder.build(rawInput = rawInput, includeHistory = includeHistory)
        val records = notiRecordDao.searchRecordsRaw(built.toSQLiteQuery())
        return records.groupBy { it.notiKey }
    }

    // [NEW] Gap Filling
    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return notiRecordDao.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
        includeHistory: Boolean
    ): List<NotiRecord> {
        return if (isOlder) {
            // "Older" means time < pivot, ordered DESC.
            // We want the result to be chronological eventually, but the DAO returns them closest to pivot first.
            notiRecordDao.getContextOlder(notiKey, pivotTime, 10, includeHistory).sortedBy { it.time }
        } else {
            // "Newer" means time > pivot, ordered ASC.
            notiRecordDao.getContextNewer(notiKey, pivotTime, 10, includeHistory)
        }
    }

    suspend fun getGapRecords(
        notiKey: String,
        minTime: Long,
        maxTime: Long,
        limit: Int,
        fromStart: Boolean
    ): List<NotiRecord> {
        return if (fromStart) {
            // Load "Next" records after minTime
            notiRecordDao.getGapRecordsNewer(notiKey, minTime, maxTime, limit)
        } else {
            // Load "Previous" records before maxTime
            // Note: DAO returns DESC, we might want to reverse here or let ViewModel sort
            notiRecordDao.getGapRecordsOlder(notiKey, minTime, maxTime, limit)
        }
    }

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long, includeHistory: Boolean): Boolean {
        return notiRecordDao.hasRecordsInGap(notiKey, minTime, maxTime, includeHistory) > 0
    }

    fun getNotSyncedNotiActions(notiKey: String, timestamp: Long): List<NotiAction> {
        return notiActionDao.getNotSyncedActionsByKey(notiKey, timestamp)
    }

    fun getNotiUnit(notiKey: String): NotiUnit? {
        return notiDrawerDao.getByNotiKey(notiKey)
    }

    fun getNotiUnitByKeys(notiKeys: List<String>): List<NotiUnit> {
        return notiDrawerDao.getByNotiKeys(notiKeys)
    }

    fun exportLog(includeContext: Boolean, includeDismissed: Boolean): JSONArray {
        val notiUnits = if (includeDismissed)
            notiDrawerDao.getAll()
        else
            notiDrawerDao.getAllVisible()

        val notificationLogs = JSONArray()

        notiUnits.forEach { notiUnit ->
            val notiKey = notiUnit.notiKey
            val notiRecords = if (includeContext) {
                notiRecordDao.getRecordsByKey(notiKey)
            } else {
                notiRecordDao.getVisibleRecordsByKey(notiKey)
            }.sortedBy { it.time }

            val notiActions = notiActionDao.getActionsByKey(notiKey).sortedBy { it.time }

            notificationLogs.put(
                org.muilab.notigpt.repository.noti.NotiExportFormatter.formatUnit(
                    notiUnit = notiUnit,
                    records = notiRecords,
                    actions = notiActions,
                    includeContext = includeContext,
                )
            )
        }

        return notificationLogs
    }

    fun logAction(
        notiKey: String,
        action: String,
        metadata: String = "",
        actionTime: Long = System.currentTimeMillis(),
    ) {
        val lastAppResumeTime = SharedPreferencesManager.lastAppResumeTime
        notiActionDao.insert(NotiAction(notiKey, action, actionTime, lastAppResumeTime, metadata))
    }

    suspend fun deleteAllNotis(category: String) {
        val notiKeys = notiDrawerDao.getVisibleNotPinnedKeysByCategory(category)
        notiKeys.forEach { k -> logAction(k, "delete_all") }
        notiDrawerDao.setUnitsInvisibleByKeys(notiKeys)
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.setRecordsInvisibleByKeys(notiKeys)
    }

    suspend fun markAllNotisRead(category: String) {
        val notReadNotiKeys = notiDrawerDao.getVisibleNotReadKeysByCategory(category)
        notReadNotiKeys.forEach { k -> logAction(k, "mark_all_read") }
        notiDrawerDao.setUnitsReadByKeys(notReadNotiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.toList())
            seenNotis.forEach { logAction(it, "scroll_read") }
        }
    }

    fun syncAppCategories(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val notiUnits = notiDrawerDao.getAll()
            notiUnits.forEach { notiUnit ->
                notiUnit.displayState.appCategory = getAppCategoryByAppName(context, notiUnit.appName)
                notiDrawerDao.update(notiUnit)
            }
        }
    }

    suspend fun actOnNoti(notiKey: String, action: String) {
        actionsRepo.actOnNotiLegacy(notiKey, action)
    }

    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        actionsRepo.actOnNoti(notiKey, action)
    }
}

data class Tuple<A, B, C>(val first: A, val second: B, val third: C)
