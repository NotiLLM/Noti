package org.muilab.notigpt.repository.noti

import org.json.JSONArray
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao

/**
 * Export logic (JSON assembly) extracted from NotiRepository.
 */
class NotiExportRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiActionDao: NotiActionDao,
) {
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
                NotiExportFormatter.formatUnit(
                    notiUnit = notiUnit,
                    records = notiRecords,
                    actions = notiActions,
                    includeContext = includeContext,
                )
            )
        }

        return notificationLogs
    }
}

