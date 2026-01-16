package org.muilab.notigpt.repository.noti

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
import org.muilab.notigpt.database.server.enqueueDelayedTaskExtraction
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.server.enqueueTaskScan
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.repository.firestore.FirestoreSyncRepository
import org.muilab.notigpt.util.Constants.Companion.MAX_EXPIRED_RECORDS_PER_KEY
import org.muilab.notigpt.util.Constants.Companion.NOTI_RECORD_EXPIRE_TIME_MS
import org.muilab.notigpt.util.SharedPreferencesManager

class NotiActionsRepository(
    private val appContext: Context,
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
) {

    private val detectionCounters = mutableMapOf<String, Int>()
    private val detectionJobs = mutableMapOf<String, Job?>()
    private val extractionCounters = mutableMapOf<String, Int>()

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun removeExpiredNotiRecords() {
        val expireTimestamp = System.currentTimeMillis() - NOTI_RECORD_EXPIRE_TIME_MS
        notiRecordDao.dismissExpiredReadRecords(expireTimestamp, MAX_EXPIRED_RECORDS_PER_KEY)
    }

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
            val toSubmit = candidateKeys.filter { k -> notiDrawerDao.getByNotiKey(k)?.shouldExtractReminder == true }
            if (toSubmit.isNotEmpty()) {
                notiDrawerDao.setShouldExtractReminderByKeys(toSubmit, false)
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

    suspend fun actOnNoti(notiKey: String, action: NotiActionType) {
        actOnNotiLegacy(notiKey, action.wireValue)
    }

    suspend fun actOnNotiLegacy(notiKey: String, action: String) {
        // Special-case actions that carry payload after ::
        if (action.startsWith("extract_reminder_with_records")) {
            val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
            if (!existing.shouldExtractReminder) {
                notiDrawerDao.setShouldExtractReminderByKey(notiKey, true)
            }

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
            "unpin" -> setPinnedState(notiKey)
            "pin" -> setPinnedState(notiKey)
            "mark_read" -> markNotiRead(notiKey)
            "extract_reminder" -> {
                // Legacy path: still allows triggering extraction, but without explicit record IDs we
                // fall back to previous behavior.
                val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
                if (!existing.shouldExtractReminder) {
                    notiDrawerDao.setShouldExtractReminderByKey(notiKey, true)
                }

                enqueueTaskExtraction(appContext, listOf(notiKey), userTriggered = true)
            }
        }
    }

    suspend fun setPinnedState(notiKey: String) {
        notiDrawerDao.flipPin(notiKey)
    }

    suspend fun setHasTask(notiKey: String, hasTask: Boolean) {
        val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
        notiDrawerDao.setHasTaskByKey(notiKey, hasTask)

        // Never demote the flag once it's true.
        if (!existing.shouldExtractReminder && hasTask) {
            Log.d("NotiActionsRepository", "Setting shouldExtractReminder to true for $notiKey")
            notiDrawerDao.setShouldExtractReminderByKey(notiKey, true)
            registerShouldExtractForNotiUnit(notiKey)
        }
    }

    suspend fun setHasMemo(notiKey: String, hasMemo: Boolean) {
        val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
        notiDrawerDao.setHasMemoByKey(notiKey, hasMemo)

        // Never demote the flag once it's true.
        if (!existing.shouldExtractReminder && hasMemo) {
            Log.d("NotiActionsRepository", "Setting shouldExtractReminder to true for $notiKey")
            notiDrawerDao.setShouldExtractReminderByKey(notiKey, true)
            registerShouldExtractForNotiUnit(notiKey)
        }
    }
}
