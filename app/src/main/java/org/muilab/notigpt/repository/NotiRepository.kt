package org.muilab.notigpt.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.DrawerDao
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL

class NotiRepository(private val drawerDao: DrawerDao) {

    fun getNotificationsFlow(): Flow<List<NotiUnit>> {
        return drawerDao.getAllVisibleFlow()
    }

    fun actOnNoti(notiKey: String, action: String) {

        CoroutineScope(Dispatchers.IO).launch {
            val existingNoti = drawerDao.getBySbnKey(notiKey)
            if (existingNoti != null) {
                when (action) {
                    "dismiss_swipe" -> existingNoti.removeNoti()
                    "access_click" -> existingNoti.removeNoti()
                    "dismiss_click" -> existingNoti.removeNoti()
                    // "move_todo" ->
                    "archive" -> existingNoti.changeCategory(NOTI_CATEGORY_ARCHIVE)
                    "unarchive" -> existingNoti.changeCategory(NOTI_CATEGORY_GENERAL)
                    "unpin" -> existingNoti.flipNotiPin()
                    "pin" -> existingNoti.flipNotiPin()
                }
                drawerDao.update(existingNoti)
            }
        }
    }

    fun deleteAllNotis(category: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (category == NOTI_CATEGORY_GENERAL)
                drawerDao.deleteAllGeneralNotPinned()
            else
                drawerDao.deleteAllCategoryNotPinned(category)
        }
    }

    val notSeenCount: LiveData<Int> = drawerDao.getNotiNotSeenCount()
}