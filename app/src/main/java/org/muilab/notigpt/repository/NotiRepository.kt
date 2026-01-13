package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
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

    fun getGroupedNotifications(): Flow<List<NotiDrawerItem>> {
        return groupingRepo.getGroupedNotifications()
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

    /** Active (not dismissed) notification keys. */
    fun getActiveNotificationKeys(): List<String> {
        return notiDrawerDao.getAllActiveKeys()
    }

    fun getActiveRecordsCountForKey(notiKey: String): Int {
        return recordsRepo.getActiveRecordsCountForKey(notiKey)
    }

    fun getActiveRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        return recordsRepo.getActiveRecordIdsForKey(notiKey, limit)
    }

    fun requestRandomTaskExtraction(count: Int) {
        val keys = getActiveNotificationKeys()
        if (keys.isEmpty() || count <= 0) return
        val randomKeys = keys.shuffled().take(count)
        if (randomKeys.isNotEmpty()) {
            enqueueTaskExtraction(appContext, randomKeys)
        }
    }

    fun getPreviewRecordsForKeys(keys: List<String>, perKeyLimit: Int = 3): List<NotiRecord> {
        return recordsRepo.getPreviewRecordsForKeys(keys, perKeyLimit)
    }

    fun activeRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return recordsRepo.activeRecordsFlowForKey(notiKey)
    }

    suspend fun fetchActiveRecordsForKey(notiKey: String): List<NotiRecord> {
        return recordsRepo.fetchActiveRecordsForKey(notiKey)
    }

    suspend fun searchNotifications(rawInput: String): Map<String, List<NotiRecord>> {
        return recordsRepo.searchNotifications(rawInput)
    }

    // Gap Filling
    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return recordsRepo.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
    ): List<NotiRecord> {
        return recordsRepo.getContextRecords(notiKey, pivotTime, isOlder)
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

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long): Boolean {
        return recordsRepo.hasRecordsInGap(notiKey, minTime, maxTime)
    }

    // History paging
    suspend fun getLatestRecords(limit: Int): List<NotiRecord> {
        return recordsRepo.getLatestRecords(limit)
    }

    suspend fun getRecordsBefore(pivotTime: Long, limit: Int): List<NotiRecord> {
        return recordsRepo.getRecordsBefore(pivotTime, limit)
    }

    suspend fun getRecordsAfter(pivotTime: Long, limit: Int): List<NotiRecord> {
        return recordsRepo.getRecordsAfter(pivotTime, limit)
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

    suspend fun deleteAllNotis() {
        maintenanceRepo.deleteAllNotis { k, a -> logAction(k, a) }
    }

    suspend fun markAllNotisRead() {
        maintenanceRepo.markAllNotisRead { k, a -> logAction(k, a) }
    }

    fun updateSeenNotifications(seenNotis: Set<String>) {
        maintenanceRepo.updateSeenNotifications(seenNotis) { k, a -> logAction(k, a) }
    }

    suspend fun actOnNoti(notiKey: String, action: String) {
        actionsRepo.actOnNotiLegacy(notiKey, action)
    }

    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        actionsRepo.actOnNoti(notiKey, action)
    }

    /** Worker/adapter-friendly accessor. */
    fun getNotiUnit(notiKey: String): NotiUnit? {
        return notiDrawerDao.getByNotiKey(notiKey)
    }

    /** Convenience batch accessor for fetching NotiUnit rows. */
    fun getNotiUnitByKeys(notiKeys: List<String>): List<NotiUnit> {
        if (notiKeys.isEmpty()) return emptyList()
        return notiDrawerDao.getByNotiKeys(notiKeys)
    }

    /** Returns actions since [sinceTimeMs]. */
    fun getNotSyncedNotiActions(notiKey: String, sinceTimeMs: Long): List<NotiAction> {
        return notiActionDao.getActionsByKey(notiKey).filter { it.time > sinceTimeMs }
    }

    /**
     * Move a loose notification into a manual ordering slot and shift existing manual items to avoid collisions.
     *
     * Semantics:
     * - Only notifications that were already manual (sortPosition != -1) are affected, plus the moved key.
     * - Existing manual positions are treated as absolute "slots"; moving into an occupied slot shifts
     *   the collided manual item (and any subsequent collisions) forward by 1.
     * - Non-manual items remain sortPosition = -1.
     */
    suspend fun moveLooseManualSlot(notiKey: String, targetIndex: Int) {
        notiDrawerDao.clearSortPositionsForGroupedItems()

        // Read the authoritative set of *currently manual* loose items from DB.
        // This ensures we only ever adjust items that already had manual positions.
        val manualKeyPositions = notiDrawerDao.getActiveLooseManualKeyPositionsOrdered()

        val existingManual = manualKeyPositions
            .asSequence()
            .filter { it.notiKey != notiKey }
            .map { it.notiKey to it.sortPosition }
            .toList()

        val desired = targetIndex.coerceAtLeast(0)

        val used = HashSet<Int>()
        val result = LinkedHashMap<String, Int>()

        // Reserve desired slot for the moved key.
        result[notiKey] = desired
        used.add(desired)

        // Keep original manual positions when possible; shift forward only on collision.
        for ((key, pos) in existingManual) {
            var p = pos
            while (p in used) p++
            result[key] = p
            used.add(p)
        }

        // Persist: clear only the currently-manual loose set, then write back result mapping.
        notiDrawerDao.resetActiveLooseManualPositions()
        result.forEach { (key, pos) ->
            notiDrawerDao.updateSortPosition(key, pos)
        }
    }

    /** Persist manual sort position for a single loose item. */
    suspend fun setManualSortPosition(notiKey: String, newPosition: Int) {
        // Defensive: groups should never have manual positions.
        notiDrawerDao.clearSortPositionsForGroupedItems()
        notiDrawerDao.updateSortPosition(notiKey, newPosition)
    }

    /**
     * Persist manual sort positions for *all* loose items (legacy behavior).
     * Kept for potential future batch-mode sorting.
     */
    suspend fun commitManualSortPositions(keysInOrder: List<String>) {
        // Defensive: groups should never have manual positions.
        notiDrawerDao.clearSortPositionsForGroupedItems()

        keysInOrder.forEachIndexed { index, key ->
            notiDrawerDao.updateSortPosition(key, index)
        }
    }

    suspend fun resetAllManualSortPositions() {
        notiDrawerDao.resetAllSortPositions()
    }

    /**
     * Commit manual sort positions for the provided [manualKeys] based on their final positions in [finalLooseOrder].
     *
     * Rules:
     * - Only keys in [manualKeys] are assigned a sortPosition.
     * - Keys not in [finalLooseOrder] are ignored (they may have been dismissed/grouped).
     * - Collisions are resolved by shifting forward to the next free slot.
     */
    suspend fun commitManualKeysFromFinalOrder(
        manualKeys: Set<String>,
        finalLooseOrder: List<String>,
    ) {
        if (manualKeys.isEmpty() || finalLooseOrder.isEmpty()) return

        notiDrawerDao.clearSortPositionsForGroupedItems()

        // Determine desired positions for keys that still exist in the final order.
        val desiredPairs = finalLooseOrder
            .withIndex()
            .asSequence()
            .filter { (_, key) -> key in manualKeys }
            .map { (idx, key) -> key to idx }
            .toList()

        if (desiredPairs.isEmpty()) return

        // Resolve collisions by shifting forward.
        val used = HashSet<Int>()
        val updates = mutableListOf<Pair<String, Int>>()
        desiredPairs.forEach { (key, desired) ->
            var p = desired
            while (p in used) p++
            used.add(p)
            updates.add(key to p)
        }

        // Persist only these keys. (Do NOT reset others here; we are tracking explicit manual keys.)
        notiDrawerDao.updateSortPositionsBulk(updates)
    }
}
