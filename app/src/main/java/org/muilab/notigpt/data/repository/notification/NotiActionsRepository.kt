package org.muilab.notigpt.data.repository.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.remote.n8n.enqueueExtractionPipeline
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.data.remote.firestore.NotificationUsageRepository

/**
 * Repository slice for notification lifecycle actions, scan counters, extraction scheduling, and action logging.
 *
 * This class bridges captured Android notifications to Room state and n8n WorkManager jobs. Keep low-level DAO
 * writes here while export and record-query concerns stay in their sibling repository slices.
 */
class NotiActionsRepository(
    private val appContext: Context,
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiLlmStateDao: NotiLlmStateDao,
) {

    private val detectionCounters = mutableMapOf<String, Int>()
    private val detectionJobs = mutableMapOf<String, Job?>()

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Creates or refreshes the current drawer row for a posted notification.
     *
     * Initial listener hydration inserts missing rows without resetting existing state; live posts refresh metadata
     * because they represent new user-visible activity.
     */
    fun upsertNotiUnit(context: Context, sbn: StatusBarNotification, isInit: Boolean) {
        val existingNoti = notiDrawerDao.getByNotiKey(sbn.key)
        val newNoti = NotiUnit(context, sbn)

        if (existingNoti == null) {
            notiDrawerDao.insert(newNoti)
        } else if (!isInit) {
            existingNoti.updateNoti(context, sbn)
            notiDrawerDao.update(existingNoti)
        }
    }

    suspend fun removeNotiUnit(notiKey: String) {
        // Soft-delete: mark dismissed.
        notiDrawerDao.dismissUnitByKey(notiKey)
        notiRecordDao.dismissRecordsByKey(notiKey)
    }

    /**
     * Appends the captured notification contents and schedules downstream scan/extraction work.
     *
     * New records increment analytics, feed scan counters, and may trigger delayed extraction after enough context
     * accumulates for a notification key.
     */
    suspend fun insertNotiRecord(sbn: StatusBarNotification) {
        val notiRecord = NotiRecord(sbn)

        // Only count truly new records.
        val existed = try {
            notiRecordDao.getRecordById(notiRecord.notiRecordId) != null
        } catch (_: Throwable) {
            false
        }

        notiRecordDao.upsert(notiRecord)

        // Firestore analytics: count received records since installation
        if (!existed) {
            try {
                FirestoreSyncRepository(appContext).incrementNotiRecordCount()
            } catch (_: Throwable) {
            }
            // 6-hour notification-volume rollup (see plans/3-invitation-and-llm-usage.md). Counted
            // here, exactly once per genuinely new record, so later pipeline stages (A/B/C)
            // reprocessing this same record never inflate the count.
            try {
                NotificationUsageRepository().recordCapturedNotification(
                    notiRecord.title.length + notiRecord.content.length
                )
            } catch (_: Throwable) {
            }
        }

        registerNewRecordForNotiUnit(notiRecord.notiKey)
    }

    /**
     * Debounces the per-notiKey extraction pipeline after new records arrive: a burst of records
     * coalesces into one pipeline run (scan → extract), and a large burst fires immediately.
     * Stage A scheduling is independent from the more expensive Stage B cooldown.
     */
    private fun registerNewRecordForNotiUnit(notiKey: String) {
        val newCount = (detectionCounters[notiKey] ?: 0) + 1
        detectionCounters[notiKey] = newCount
        detectionJobs[notiKey]?.cancel()

        val job = scope.launch {
            val delayMs = captureDelayMs(newCount)
            if (delayMs > 0L) delay(delayMs)
            enqueueExtractionPipeline(appContext, notiKey)
            detectionCounters.remove(notiKey)
            detectionJobs.remove(notiKey)
        }
        detectionJobs[notiKey] = job
    }

    suspend fun actOnNotiLegacy(notiKey: String, action: String) {
        // Special-case actions that carry payload after :: (record ids are no longer used — the
        // forced pipeline extracts from the thread's unprocessed records).
        if (action.startsWith("extract_saved_item_with_records") ||
            action.startsWith("extract_reminder_with_records")) {
            notiDrawerDao.getByNotiKey(notiKey) ?: return
            enqueueExtractionPipeline(appContext, notiKey, forced = true)
            return
        }

        when (action) {
            "dismiss_swipe" -> {
                val noti = notiDrawerDao.getByNotiKey(notiKey)
                if (noti != null && !noti.isPinned) {
                    notiDrawerDao.dismissUnitByKey(notiKey)
                    notiRecordDao.dismissRecordsByKey(notiKey)
                }
                return
            }
            "access_click_dismiss" -> {
                val noti = notiDrawerDao.getByNotiKey(notiKey)
                if (noti != null) {
                    if (!noti.isPinned) {
                        notiDrawerDao.dismissUnitByKey(notiKey)
                        notiRecordDao.dismissRecordsByKey(notiKey)
                    }
                }
                return
            }
            "to_top" -> notiDrawerDao.updateToTopStatus(notiKey, true, System.currentTimeMillis())
            "undo_to_top" -> notiDrawerDao.updateToTopStatus(notiKey, false, 0L)
            "unpin" -> setPinnedState(notiKey, false)
            "pin" -> setPinnedState(notiKey, true)
            "extract_reminder" -> {
                // User-triggered manual extraction: run the forced pipeline for this thread.
                notiDrawerDao.getByNotiKey(notiKey) ?: return
                enqueueExtractionPipeline(appContext, notiKey, forced = true)
            }
        }
    }

    suspend fun setPinnedState(notiKey: String, pinned: Boolean? = null) {
        if (pinned == null) notiDrawerDao.flipPin(notiKey)
        else notiDrawerDao.setPinned(notiKey, pinned)
    }

    companion object {
        internal const val CAPTURE_DELAY_MS = 2 * 60 * 1000L
        internal const val CAPTURE_IMMEDIATE_RECORD_COUNT = 10

        internal fun captureDelayMs(accumulatedRecords: Int): Long =
            if (accumulatedRecords >= CAPTURE_IMMEDIATE_RECORD_COUNT) 0L else CAPTURE_DELAY_MS
    }
}
