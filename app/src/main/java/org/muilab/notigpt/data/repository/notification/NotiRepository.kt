package org.muilab.notigpt.data.repository.notification

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiGroupDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.repository.notification.NotiActionsRepository
import org.muilab.notigpt.data.repository.notification.NotiGroupingRepository
import org.muilab.notigpt.data.repository.notification.NotiGroupRepository
import org.muilab.notigpt.data.export.NotiExportRepository
import org.muilab.notigpt.data.repository.notification.NotiMaintenanceRepository
import org.muilab.notigpt.data.repository.notification.NotiRecordsRepository

/**
 * Facade over notification drawer, record, action, group, export, and maintenance repositories.
 *
 * The ViewModels and workers should depend on this class instead of individual DAOs. Keep new responsibilities
 * delegated to focused repository helpers so this facade stays a stable API surface.
 */
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

    /**
     * Returns the drawer-ready stream after active filtering, grouping, and sort rules are applied.
     *
     * Consumers should render this output directly instead of regrouping notification rows in UI code.
     */
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

    /**
     * Upserts the latest drawer row for an Android notification key.
     *
     * The service calls this for both initial active notifications and new posts; record history is inserted
     * separately so current state and timeline remain distinct.
     */
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

    /**
     * Appends one durable content record for a captured Android notification.
     *
     * Call this alongside NotiUnit upserts when the timeline needs to preserve repeated updates for the same key.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun insertNotiRecord(sbn: StatusBarNotification) {
        actionsRepo.insertNotiRecord(sbn)
    }

    suspend fun markNotiRead(notiKey: String) {
        actionsRepo.markNotiRead(notiKey)
    }

    suspend fun setScanStates(notiKey: String, hasTask: Boolean, hasMemo: Boolean, hasEvent: Boolean) {
        actionsRepo.setHasTask(notiKey, hasTask)
        actionsRepo.setHasMemo(notiKey, hasMemo)
        actionsRepo.setHasEvent(notiKey, hasEvent)
    }

    suspend fun setPinnedState(notiKey: String, pinned: Boolean) {
        actionsRepo.setPinnedState(notiKey)
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

    fun activeRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return recordsRepo.activeRecordsFlowForKey(notiKey)
    }

    suspend fun searchNotifications(rawInput: String): Map<String, List<NotiRecord>> {
        return recordsRepo.searchNotifications(rawInput)
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

    fun exportLog(includeContext: Boolean, includeDismissed: Boolean): Sequence<JSONObject> {
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
     * Persists manual sort positions for every loose item in the supplied order.
     *
     * Prefer targeted manual-key commits for drag flows; use this when the whole loose list order is authoritative.
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

    /**
     * Get all notification records (across all notifications).
     */
    fun getAllRecords(): List<NotiRecord> {
        return notiRecordDao.getAllRecords()
    }

    /**
     * Get all notification actions (user interactions).
     */
    fun getAllActions(): List<NotiAction> {
        return notiActionDao.getAllActions()
    }
}
