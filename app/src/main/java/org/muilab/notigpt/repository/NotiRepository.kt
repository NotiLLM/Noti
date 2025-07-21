package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiUnitWithRecords
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.getAppCategoryByAppName

class NotiRepository(
    private val db: AppDatabase, // Add database instance
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao
) {

    // Helper function to avoid code duplication
    private fun mapToDisplayUnit(listWithRecords: List<NotiUnitWithRecords>): List<NotiDisplayUnit> {
        return listWithRecords.map { unitWithRecords ->
            val mappedNotiRecords = unitWithRecords.notiRecords.map { visibleRecord ->
                NotiRecord(
                    // KEYS
                    notiRecordId = visibleRecord.notiRecordId,
                    notiKey = visibleRecord.notiKey,

                    // TIME RELATED
                    whenTime = visibleRecord.whenTime,
                    postTime = visibleRecord.postTime,

                    // TITLE RELATED
                    person = visibleRecord.person,
                    extraTitle = visibleRecord.extraTitle,
                    extraBigTitle = visibleRecord.extraBigTitle,
                    extraConversationTitle = visibleRecord.extraConversationTitle,

                    // CONTENT RELATED
                    extraBigText = visibleRecord.extraBigText,
                    extraText = visibleRecord.extraText,
                    extraTextLines = visibleRecord.extraTextLines,
                    extraSummaryText = visibleRecord.extraSummaryText,
                    extraInfoText = visibleRecord.extraInfoText,
                    extraSubText = visibleRecord.extraSubText,

                    // STATUS
                    isRead = visibleRecord.isRead,
                    isVisible = visibleRecord.isVisible
                )
            }
            NotiDisplayUnit(unitWithRecords.notiUnit, mappedNotiRecords.sortedBy { it.time })
        }
    }

    // NEW REPO METHOD 1
    fun getManuallySorted(
        category: Flow<String>,
        appCategory: Flow<String>,
        isAppCategoryView: Flow<Boolean>
    ): Flow<List<NotiDisplayUnit>> {
        return combine(category, appCategory, isAppCategoryView) { cat, appCat, isAppView ->
            Triple(cat, appCat, isAppView)
        }.flatMapLatest { (cat, appCat, isAppView) ->
            notiDrawerDao.getManuallySortedNotifications(cat, appCat, isAppView)
                .map { mapToDisplayUnit(it) }
        }
    }

    // NEW REPO METHOD 2
    fun getAutoSorted(
        category: Flow<String>,
        appCategory: Flow<String>,
        isAppCategoryView: Flow<Boolean>
    ): Flow<List<NotiDisplayUnit>> {
        return combine(category, appCategory, isAppCategoryView) { cat, appCat, isAppView ->
            Triple(cat, appCat, isAppView)
        }.flatMapLatest { (cat, appCat, isAppView) ->
            notiDrawerDao.getAutoSortedNotifications(cat, appCat, isAppView)
                .map { mapToDisplayUnit(it) }
        }
    }

    suspend fun updateSortPositionsInBulk(updates: List<Pair<String, Int>>, isAppCategoryView: Boolean) {
        if (updates.isEmpty()) return

        val columnName = if (isAppCategoryView) "appCategorySortPosition" else "sortPosition"

        val sql = buildString {
            append("UPDATE noti_drawer SET $columnName = CASE notiKey ")
            updates.forEach { (key, pos) ->
                // Note: It's crucial that 'key' is properly escaped or known to be safe.
                // Since notiKey comes from the system, it's generally safe.
                append("WHEN '$key' THEN $pos ")
            }
            append("END WHERE notiKey IN (")
            updates.joinTo(this, separator = ", ") { "'${it.first}'" }
            append(")")
        }

        // Execute the raw query in a transaction for atomicity.
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(sql)
        }
    }


    suspend fun updateSortPositions(updates: List<Pair<String, Int>>, isAppCategoryView: Boolean) {
        notiDrawerDao.updateSortPositions(updates, isAppCategoryView)
    }

    suspend fun resetAllSortPositions() {
        notiDrawerDao.resetAllSortPositions()
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

    fun getVisibleNotiCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotiCountByCategory(category)
    }

    fun getVisibleNotReadNotificationCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotReadCountByCategory(category)
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
        when (action) {
            "dismiss_swipe" -> notiDrawerDao.setUnitInvisibleByKey(notiKey)
            "access_click_dismiss" -> notiDrawerDao.setUnitInvisibleByKey(notiKey)

            "archive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_ARCHIVE)
            "unarchive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "make_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_MAKETASK)
            "dismiss_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)

            "unpin" -> notiDrawerDao.flipPin(notiKey)
            "pin" -> notiDrawerDao.flipPin(notiKey)

            "mark_task_in_progress" -> notiDrawerDao.incrementTaskState(notiKey)
            "mark_task_completed" -> notiDrawerDao.incrementTaskState(notiKey)
            "mark_task_reset" -> notiDrawerDao.incrementTaskState(notiKey)
        }
        notiActionDao.insert(NotiAction(notiKey, action, System.currentTimeMillis()))
    }

    fun markAllNotisRead(category: String) {
        val notiKeys = notiDrawerDao.getVisibleKeysByCategory(category)
        notiKeys.forEach { notiKey ->
            notiActionDao.insert(NotiAction(notiKey, "mark_all_read", System.currentTimeMillis()))
        }
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.setRecordsReadByIds(notiKeys)
    }

    fun deleteAllNotis(category: String) {
        val notiKeys = notiDrawerDao.getVisibleNotPinnedKeysByCategory(category)
        val currentTime = System.currentTimeMillis()
        notiKeys.forEach { notiKey ->
            notiActionDao.insert(NotiAction(notiKey, "delete_all", currentTime))
        }

        notiDrawerDao.setUnitsInvisibleByKeys(notiKeys)
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.setRecordsInvisibleByKeys(notiKeys)
        notiRecordDao.setRecordsReadByIds(notiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<String>, seenInfos: Set<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.toList())
            notiRecordDao.setRecordsReadByIds(seenInfos.toList())
        }
    }

    fun syncAppCategories(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val notiUnits = notiDrawerDao.getAll()
            notiUnits.forEach { notiUnit ->
                notiUnit.displayState.appCategory = getAppCategoryByAppName(context, notiUnit.appName)
                notiDrawerDao.update(notiUnit)
            }
        }
    }
}