package org.muilab.notigpt.data.repository.notification

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.remote.n8n.enqueueDelayedTaskExtraction
import org.muilab.notigpt.data.remote.n8n.enqueueTaskExtraction
import org.muilab.notigpt.data.remote.n8n.enqueueTaskScan
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.util.Constants.Companion.MAX_EXPIRED_RECORDS_PER_KEY
import org.muilab.notigpt.util.Constants.Companion.NOTI_RECORD_EXPIRE_TIME_MS
import org.muilab.notigpt.util.SharedPreferencesManager

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
    private val extractionCounters = mutableMapOf<String, Int>()

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Dismisses old read records while preserving the most recent context per notification key. */
    suspend fun removeExpiredNotiRecords() {
        val expireTimestamp = System.currentTimeMillis() - NOTI_RECORD_EXPIRE_TIME_MS
        notiRecordDao.dismissExpiredReadRecords(expireTimestamp, MAX_EXPIRED_RECORDS_PER_KEY)
    }

    /**
     * Creates or refreshes the current drawer row for a posted notification.
     *
     * Initial listener hydration inserts missing rows without resetting existing state; live posts refresh metadata
     * and unread state because they represent new user-visible activity.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun upsertNotiUnit(context: Context, sbn: StatusBarNotification, isInit: Boolean) {
        val existingNoti = notiDrawerDao.getByNotiKey(sbn.key)
        val newNoti = NotiUnit(context, sbn)

        if (existingNoti == null) {
            notiDrawerDao.insert(newNoti)
        } else if (!isInit) {
            existingNoti.updateNoti(context, sbn)
            existingNoti.isRead = false
            notiDrawerDao.update(existingNoti)
        }
    }

    fun updateNotiUnit(notiUnit: NotiUnit) {
        notiDrawerDao.update(notiUnit)
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
    @RequiresApi(Build.VERSION_CODES.S)
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
        }

        registerNewRecordForNotiUnit(notiRecord.notiKey)
    }

    private fun registerNewRecordForNotiUnit(notiKey: String) {
        val newCount = (detectionCounters[notiKey] ?: 0) + 1
        detectionCounters[notiKey] = newCount
        detectionJobs[notiKey]?.cancel()

        val job = scope.launch {
            val waitSeconds = SharedPreferencesManager.waitSecondsBeforeNotiUnitSync
            val maxRecords = SharedPreferencesManager.maxRecordsBeforeNotiSync
            if (newCount >= maxRecords) {
                enqueueTaskScan(appContext, notiKey)
                detectionCounters.remove(notiKey)
                detectionJobs.remove(notiKey)
                return@launch
            }
            delay(waitSeconds * 1000L)
            enqueueTaskScan(appContext, notiKey)
            detectionCounters.remove(notiKey)
            detectionJobs.remove(notiKey)
        }
        detectionJobs[notiKey] = job
    }

    private fun registerShouldExtractForNotiUnit(notiKey: String) {
        synchronized(extractionCounters) {
            extractionCounters[notiKey] = (extractionCounters[notiKey] ?: 0) + 1
        }
        val totalPending = synchronized(extractionCounters) { extractionCounters.values.sum() }
        val maxCount = SharedPreferencesManager.maxRecordsBeforeDrawerSync
        val waitSeconds = SharedPreferencesManager.waitSecondsBeforeDrawerSync

        if (totalPending >= maxCount) {
            val candidateKeys: List<String> = synchronized(extractionCounters) { extractionCounters.keys.toList() }
            val toSubmit = candidateKeys.filter { k -> notiLlmStateDao.getByKey(k)?.shouldExtractReminder == true }
            if (toSubmit.isNotEmpty()) {
                notiLlmStateDao.setShouldExtractByKeys(toSubmit, false, System.currentTimeMillis())
                enqueueTaskExtraction(appContext, toSubmit)
            }
            synchronized(extractionCounters) { extractionCounters.clear() }
            return
        }

        enqueueDelayedTaskExtraction(appContext, waitSeconds.toLong())
    }

    suspend fun markNotiRead(notiKey: String) {
        notiDrawerDao.setUnitReadByKey(notiKey)
    }

    suspend fun actOnNotiLegacy(notiKey: String, action: String) {
        // Special-case actions that carry payload after ::
        if (action.startsWith("extract_reminder_with_records")) {
            notiDrawerDao.getByNotiKey(notiKey) ?: return
            promoteShouldExtract(notiKey)

            val parts = action.split("::", limit = 2)
            val json = parts.getOrNull(1).orEmpty()
            val ids: List<String> = try {
                com.google.gson.Gson().fromJson(json, Array<String>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }

            enqueueTaskExtraction(
                appContext,
                listOf(notiKey),
                userTriggered = true,
                visibleRecordIds = ids,
            )
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
            "mark_read" -> markNotiRead(notiKey)
            "extract_reminder" -> {
                // Legacy path: still allows triggering extraction, but without explicit record IDs we
                // fall back to previous behavior.
                notiDrawerDao.getByNotiKey(notiKey) ?: return
                promoteShouldExtract(notiKey)

                enqueueTaskExtraction(appContext, listOf(notiKey), userTriggered = true)
            }
        }
    }

    suspend fun setPinnedState(notiKey: String, pinned: Boolean? = null) {
        if (pinned == null) notiDrawerDao.flipPin(notiKey)
        else notiDrawerDao.setPinned(notiKey, pinned)
    }

    suspend fun setHasTask(notiKey: String, hasTask: Boolean) {
        notiDrawerDao.getByNotiKey(notiKey) ?: return
        val state = llmStateOrDefault(notiKey)
        notiLlmStateDao.upsert(state.copy(hasTask = hasTask, updatedAt = System.currentTimeMillis()))

        // Never demote the flag once it's true.
        if (!state.shouldExtractReminder && hasTask) {
            Log.d("NotiActionsRepository", "Setting shouldExtractReminder to true for $notiKey")
            promoteShouldExtract(notiKey)
            registerShouldExtractForNotiUnit(notiKey)
        }
    }

    suspend fun setHasMemo(notiKey: String, hasMemo: Boolean) {
        notiDrawerDao.getByNotiKey(notiKey) ?: return
        val state = llmStateOrDefault(notiKey)
        notiLlmStateDao.upsert(state.copy(hasMemo = hasMemo, updatedAt = System.currentTimeMillis()))

        // Never demote the flag once it's true.
        if (!state.shouldExtractReminder && hasMemo) {
            Log.d("NotiActionsRepository", "Setting shouldExtractReminder to true for $notiKey")
            promoteShouldExtract(notiKey)
            registerShouldExtractForNotiUnit(notiKey)
        }
    }

    suspend fun setHasEvent(notiKey: String, hasEvent: Boolean) {
        notiDrawerDao.getByNotiKey(notiKey) ?: return
        val state = llmStateOrDefault(notiKey)
        notiLlmStateDao.upsert(state.copy(hasEvent = hasEvent, updatedAt = System.currentTimeMillis()))

        // Never demote the flag once it's true.
        if (!state.shouldExtractReminder && hasEvent) {
            Log.d("NotiActionsRepository", "Setting shouldExtractReminder to true for $notiKey")
            promoteShouldExtract(notiKey)
            registerShouldExtractForNotiUnit(notiKey)
        }
    }

    private fun llmStateOrDefault(notiKey: String): NotiLlmState =
        notiLlmStateDao.getByKey(notiKey) ?: NotiLlmState(notiKey = notiKey)

    /** Sets shouldExtractReminder without disturbing the other thread-state fields. */
    private fun promoteShouldExtract(notiKey: String) {
        val state = llmStateOrDefault(notiKey)
        if (state.shouldExtractReminder) return
        notiLlmStateDao.upsert(state.copy(shouldExtractReminder = true, updatedAt = System.currentTimeMillis()))
    }
}
