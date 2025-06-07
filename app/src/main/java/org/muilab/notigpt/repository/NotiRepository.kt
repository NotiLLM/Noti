package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.NotiCategoryDao
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL

class NotiRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiCategoryDao: NotiCategoryDao
) {

    fun getNotificationDisplayFlow(): Flow<List<NotiDisplayUnit>> {
        return notiDrawerDao.getAllVisibleFlow().flatMapLatest { notiUnits ->
            val keys = notiUnits.map { it.notiKey }
            notiRecordDao.getVisibleRecordsFlowByKeys(keys).map { records ->
                notiUnits.map { notiUnit ->
                    val notiUnitRecords = records
                        .filter { it.notiKey == notiUnit.notiKey }
                        .sortedBy { it.time }
                    NotiDisplayUnit(notiUnit, notiUnitRecords)
                }
            }
        }
    }

    fun getNotifications(includeContext: Boolean, includeDismissed: Boolean): List<NotiDisplayUnit> {
        val notiUnits = if (includeDismissed)
            notiDrawerDao.getAll()
        else
            notiDrawerDao.getAllVisible()
        val keys = notiUnits.map { it.notiKey }
        val records = if (includeContext) {
            notiRecordDao.getRecordsByKeys(keys)
        } else {
            notiRecordDao.getVisibleRecordsByKeys(keys)
        }
        return notiUnits.map { notiUnit ->
            val notiUnitRecords = records
                .filter { it.notiKey == notiUnit.notiKey }
                .sortedBy { it.time }
            NotiDisplayUnit(notiUnit, notiUnitRecords)
        }
    }

    fun getNotificationKeys(): List<String> {
        return notiDrawerDao.getAllVisibleKeys()
    }

    fun getNotificationCount(): Int {
        return notiDrawerDao.getVisibleNotiCount()
    }

    @RequiresApi(Build.VERSION_CODES.S)
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

    fun updateNotiUnit(notiUnit: NotiUnit) {
        notiDrawerDao.update(notiUnit)
    }

    fun removeNotiUnit(notiKey: String) {
        notiDrawerDao.setUnitInvisibleByKey(notiKey)
        notiRecordDao.setRecordsInvisibleByKey(notiKey)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun insertNotiRecord(sbn: StatusBarNotification) {
        val notiRecord = NotiRecord(sbn)
        notiRecordDao.upsert(notiRecord)
    }

    fun getDisplayedNotification(notiKey: String, includeContext: Boolean): NotiDisplayUnit? {
        val notiUnit = notiDrawerDao.getByNotiKey(notiKey)
        val notiRecords = if (includeContext) {
            notiRecordDao.getRecordsByKey(notiKey)
        } else {
            notiRecordDao.getVisibleRecordsByKey(notiKey)
        }
        return if (notiUnit != null) {
            NotiDisplayUnit(notiUnit, notiRecords)
        } else {
            null
        }
    }

    fun actOnNoti(notiKey: String, action: String) {

        CoroutineScope(Dispatchers.IO).launch {
            val existingNoti = notiDrawerDao.getByNotiKey(notiKey)
            if (existingNoti != null) {
                when (action) {
                    "dismiss_swipe" -> existingNoti.setInvisible()
                    "access_click" -> existingNoti.setInvisible()
                    "dismiss_click" -> existingNoti.setInvisible()

                    "archive" -> existingNoti.changeCategory(NOTI_CATEGORY_ARCHIVE)
                    "unarchive" -> existingNoti.changeCategory(NOTI_CATEGORY_GENERAL)
                    "unpin" -> existingNoti.flipNotiPin()
                    "pin" -> existingNoti.flipNotiPin()
                }
                notiActionDao.insert(NotiAction(notiKey, action, System.currentTimeMillis()))
                notiDrawerDao.update(existingNoti)
            }
        }
    }

    fun deleteAllNotis(category: String) {
        val notiKeys = notiDrawerDao.getVisibleNotPinnedKeysByCategory(category)
        notiDrawerDao.setUnitsInvisibleByKeys(notiKeys)
        notiRecordDao.setRecordsInvisibleByKeys(notiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<String>, seenInfos: Set<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.toList())
            notiRecordDao.setRecordsReadByIds(seenInfos.toList())
        }
    }
}