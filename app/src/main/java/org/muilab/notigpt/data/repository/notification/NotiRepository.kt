package org.muilab.notigpt.data.repository.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.export.NotiExportRepository

/**
 * Facade over notification drawer, record, action, export, and maintenance repositories.
 *
 * The ViewModels and workers should depend on this class instead of individual DAOs. Keep new responsibilities
 * delegated to focused repository helpers so this facade stays a stable API surface.
 */
class NotiRepository(
    private val appContext: Context,
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiLlmStateDao: NotiLlmStateDao,
) {

    private val activeUnitsRepo = NotiActiveUnitsRepository(
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
    )

    private val actionsRepo = NotiActionsRepository(
        appContext = appContext,
        notiDrawerDao = notiDrawerDao,
        notiRecordDao = notiRecordDao,
        notiLlmStateDao = notiLlmStateDao,
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

    /** Returns flat active notification units after record joining and drawer sort rules are applied. */
    fun getActiveNotiUnits(): Flow<List<NotiDisplayUnit>> {
        return activeUnitsRepo.getActiveNotiUnits()
    }

    /**
     * Upserts the latest drawer row for an Android notification key.
     *
     * The service calls this for both initial active notifications and new posts; record history is inserted
     * separately so current state and timeline remain distinct.
     */
    fun upsertNotiUnit(context: Context, sbn: StatusBarNotification, isInit: Boolean) {
        actionsRepo.upsertNotiUnit(context, sbn, isInit)
    }

    fun updateNotiUnit(notiUnit: NotiUnit) {
        actionsRepo.updateNotiUnit(notiUnit)
    }

    suspend fun removeNotiUnit(notiKey: String) = withContext(Dispatchers.IO) {
        actionsRepo.removeNotiUnit(notiKey)
    }

    /**
     * Appends one durable content record for a captured Android notification.
     *
     * Call this alongside NotiUnit upserts when the timeline needs to preserve repeated updates for the same key.
     */
    suspend fun insertNotiRecord(sbn: StatusBarNotification) = withContext(Dispatchers.IO) {
        actionsRepo.insertNotiRecord(sbn)
    }

    suspend fun markNotiRead(notiKey: String) = withContext(Dispatchers.IO) {
        actionsRepo.markNotiRead(notiKey)
    }

    fun getNewRecords(): List<NotiRecord> = notiRecordDao.getNewRecords()

    suspend fun setPinnedState(notiKey: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        actionsRepo.setPinnedState(notiKey, pinned)
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

    suspend fun deleteAllNotis() = withContext(Dispatchers.IO) {
        maintenanceRepo.deleteAllNotis { k, a -> maintenanceRepo.logAction(k, a) }
    }

    suspend fun deleteNotisByKeys(notiKeys: List<String>) = withContext(Dispatchers.IO) {
        maintenanceRepo.deleteNotisByKeys(notiKeys) { k, a -> maintenanceRepo.logAction(k, a) }
    }

    suspend fun markAllNotisRead() = withContext(Dispatchers.IO) {
        maintenanceRepo.markAllNotisRead { k, a -> maintenanceRepo.logAction(k, a) }
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
     * Move a notification into a manual ordering slot and shift existing manual items to avoid collisions.
     *
     * Semantics:
     * - Only notifications that were already manual (sortPosition != -1) are affected, plus the moved key.
     * - Existing manual positions are treated as absolute "slots"; moving into an occupied slot shifts
     *   the collided manual item (and any subsequent collisions) forward by 1.
     * - Non-manual items remain sortPosition = -1.
     */
    suspend fun moveActiveManualSlot(notiKey: String, targetIndex: Int) = withContext(Dispatchers.IO) {
        // Read the authoritative set of *currently manual* active items from DB.
        // This ensures we only ever adjust items that already had manual positions.
        val manualKeyPositions = notiDrawerDao.getActiveManualKeyPositionsOrdered()

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

        // Persist: clear only the currently-manual active set, then write back result mapping.
        notiDrawerDao.resetActiveManualPositions()
        result.forEach { (key, pos) ->
            notiDrawerDao.updateSortPosition(key, pos)
        }
    }

    /** Persist manual sort position for a single active item. */
    suspend fun setManualSortPosition(notiKey: String, newPosition: Int) = withContext(Dispatchers.IO) {
        notiDrawerDao.updateSortPosition(notiKey, newPosition)
    }

    /**
     * Persists manual sort positions for every active item in the supplied order.
     *
     * Prefer targeted manual-key commits for drag flows; use this when the whole active list order is authoritative.
     */
    suspend fun commitManualSortPositions(keysInOrder: List<String>) = withContext(Dispatchers.IO) {
        keysInOrder.forEachIndexed { index, key ->
            notiDrawerDao.updateSortPosition(key, index)
        }
    }

    suspend fun resetAllManualSortPositions() = withContext(Dispatchers.IO) {
        notiDrawerDao.resetAllSortPositions()
    }

    /**
     * Commit manual sort positions for the provided [manualKeys] based on their final positions in [finalActiveOrder].
     *
     * Rules:
     * - Only keys in [manualKeys] are assigned a sortPosition.
     * - Keys not in [finalActiveOrder] are ignored (they may have been dismissed).
     * - Collisions are resolved by shifting forward to the next free slot.
     */
    suspend fun commitManualKeysFromFinalOrder(
        manualKeys: Set<String>,
        finalActiveOrder: List<String>,
    ) = withContext(Dispatchers.IO) {
        if (manualKeys.isEmpty() || finalActiveOrder.isEmpty()) return@withContext

        // Determine desired positions for keys that still exist in the final order.
        val desiredPairs = finalActiveOrder
            .withIndex()
            .asSequence()
            .filter { (_, key) -> key in manualKeys }
            .map { (idx, key) -> key to idx }
            .toList()

        if (desiredPairs.isEmpty()) return@withContext

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
