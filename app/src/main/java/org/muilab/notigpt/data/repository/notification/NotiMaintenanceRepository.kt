package org.muilab.notigpt.data.repository.notification

import org.muilab.notigpt.data.local.room.dao.NotiActionDao
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Repository slice for bulk drawer maintenance operations.
 *
 * Keep destructive or broad state changes here, including delete-all and action logging. Feature-specific
 * updates should stay in narrower repository slices.
 */
class NotiMaintenanceRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiActionDao: NotiActionDao,
) {

    suspend fun deleteAllNotis(logAction: (String, String) -> Unit) {
        deleteNotisByKeys(notiDrawerDao.getActiveNotPinnedKeys(), logAction)
    }

    /**
     * Clears a specific set of units (dismiss unit + records). Callers are responsible for
     * scoping the keys — e.g. the category page passes only the visible, unpinned threads.
     */
    suspend fun deleteNotisByKeys(notiKeys: List<String>, logAction: (String, String) -> Unit) {
        if (notiKeys.isEmpty()) return
        notiKeys.forEach { k -> logAction(k, "delete_all") }
        notiDrawerDao.dismissUnitsByKeys(notiKeys)
        notiRecordDao.dismissRecordsByKeys(notiKeys)
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
