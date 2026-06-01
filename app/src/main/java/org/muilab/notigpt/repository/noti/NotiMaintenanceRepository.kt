package org.muilab.notigpt.repository.noti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Repository slice for bulk drawer maintenance operations.
 *
 * Keep destructive or broad state changes here, including delete-all, mark-all-read, seen-state persistence, and
 * action logging. Feature-specific updates should stay in narrower repository slices.
 */
class NotiMaintenanceRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiActionDao: NotiActionDao,
) {

    suspend fun deleteAllNotis(logAction: (String, String) -> Unit) {
        val notiKeys = notiDrawerDao.getActiveNotPinnedKeys()
        notiKeys.forEach { k -> logAction(k, "delete_all") }
        notiDrawerDao.dismissUnitsByKeys(notiKeys)
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.dismissRecordsByKeys(notiKeys)
    }

    suspend fun markAllNotisRead(logAction: (String, String) -> Unit) {
        val notReadNotiKeys = notiDrawerDao.getActiveUnreadKeys()
        notReadNotiKeys.forEach { k -> logAction(k, "mark_all_read") }
        notiDrawerDao.setUnitsReadByKeys(notReadNotiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<String>, logAction: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.toList())
            seenNotis.forEach { logAction(it, "scroll_read") }
        }
    }

    fun logAction(
        notiKey: String,
        action: String,
        metadata: String = "",
        actionTime: Long = System.currentTimeMillis(),
    ) {
        val lastAppResumeTime = SharedPreferencesManager.lastAppResumeTime
        notiActionDao.insert(org.muilab.notigpt.model.notifications.NotiAction(notiKey, action, actionTime, lastAppResumeTime, metadata))
    }
}
