package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiGroupDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.repository.noti.NotiActionsRepository
import org.muilab.notigpt.repository.noti.NotiGroupingRepository
import org.muilab.notigpt.repository.noti.NotiGroupRepository
import org.muilab.notigpt.repository.noti.NotiExportRepository
import org.muilab.notigpt.repository.noti.NotiMaintenanceRepository
import org.muilab.notigpt.repository.noti.NotiRecordsRepository

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

    private val recordsRepo = NotiRecordsRepository(
        notiRecordDao = notiRecordDao,
    )

    private val exportRepo = NotiExportRepository(
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
        notiActionDao = notiActionDao,
    )

    private val maintenanceRepo = NotiMaintenanceRepository(
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
        notiActionDao = notiActionDao,
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
        return maintenanceRepo.getVisibleNotiCountByCategory(category)
    }

    fun getVisibleNotReadNotificationCountByCategory(category: String): Int {
        return maintenanceRepo.getVisibleNotReadNotificationCountByCategory(category)
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
        return recordsRepo.getVisibleRecordsCountForKey(notiKey)
    }

    fun getVisibleRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        return recordsRepo.getVisibleRecordIdsForKey(notiKey, limit)
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
        return recordsRepo.getPreviewRecordsForKeys(keys, perKeyLimit)
    }

    fun visibleRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return recordsRepo.visibleRecordsFlowForKey(notiKey)
    }

    suspend fun fetchVisibleRecordsForKey(notiKey: String): List<NotiRecord> {
        return recordsRepo.fetchVisibleRecordsForKey(notiKey)
    }

    // [NEW] Advanced Search Logic
    suspend fun searchNotifications(rawInput: String, includeHistory: Boolean): Map<String, List<NotiRecord>> {
        return recordsRepo.searchNotifications(rawInput, includeHistory)
    }

    // [NEW] Gap Filling
    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return recordsRepo.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
        includeHistory: Boolean
    ): List<NotiRecord> {
        return recordsRepo.getContextRecords(notiKey, pivotTime, isOlder, includeHistory)
    }

    suspend fun getGapRecords(
        notiKey: String,
        minTime: Long,
        maxTime: Long,
        limit: Int,
        fromStart: Boolean
    ): List<NotiRecord> {
        return recordsRepo.getGapRecords(notiKey, minTime, maxTime, limit, fromStart)
    }

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long, includeHistory: Boolean): Boolean {
        return recordsRepo.hasRecordsInGap(notiKey, minTime, maxTime, includeHistory)
    }

    fun exportLog(includeContext: Boolean, includeDismissed: Boolean): JSONArray {
        return exportRepo.exportLog(includeContext, includeDismissed)
    }

    fun logAction(
        notiKey: String,
        action: String,
        metadata: String = "",
        actionTime: Long = System.currentTimeMillis(),
    ) {
        maintenanceRepo.logAction(notiKey, action, metadata, actionTime)
    }

    suspend fun deleteAllNotis(category: String) {
        maintenanceRepo.deleteAllNotis(category) { k, a -> logAction(k, a) }
    }

    suspend fun markAllNotisRead(category: String) {
        maintenanceRepo.markAllNotisRead(category) { k, a -> logAction(k, a) }
    }

    fun updateSeenNotifications(seenNotis: Set<String>) {
        maintenanceRepo.updateSeenNotifications(seenNotis) { k, a -> logAction(k, a) }
    }

    fun syncAppCategories(context: Context) {
        maintenanceRepo.syncAppCategories(context)
    }

    suspend fun actOnNoti(notiKey: String, action: String) {
        actionsRepo.actOnNotiLegacy(notiKey, action)
    }

    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        actionsRepo.actOnNoti(notiKey, action)
    }

    /**
     * Worker/adapter-friendly accessor.
     * Keeps N8n workers independent of the Room DAO.
     */
    fun getNotiUnit(notiKey: String): NotiUnit? {
        return notiDrawerDao.getByNotiKey(notiKey)
    }

    /**
     * Convenience batch accessor for fetching NotiUnit rows.
     * Used by DrawerSearchController to hydrate search results.
     */
    fun getNotiUnitByKeys(notiKeys: List<String>): List<NotiUnit> {
        if (notiKeys.isEmpty()) return emptyList()
        return notiDrawerDao.getByNotiKeys(notiKeys)
    }

    /**
     * Returns actions since [sinceTimeMs].
     * Used by N8n updateNotification sync.
     */
    fun getNotSyncedNotiActions(notiKey: String, sinceTimeMs: Long): List<NotiAction> {
        // Prefer to let the DAO filter; if you later add a DAO query for this,
        // update this method but keep the signature stable.
        return notiActionDao.getActionsByKey(notiKey).filter { it.time > sinceTimeMs }
    }
}
