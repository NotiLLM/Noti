package org.muilab.notigpt.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.paging.NotiRepository
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr
import org.muilab.notigpt.util.getNotifications
import org.muilab.notigpt.util.postOngoingNotification

class DrawerViewModel(
    application: Application,
    notiRepository: NotiRepository
) : AndroidViewModel(application) {

    private val notifications: StateFlow<List<NotiUnit>> = notiRepository.getNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notSeenCount: LiveData<Int> = notiRepository.notSeenCount

    @SuppressLint("StaticFieldLeak")
    val context: Context = getApplication<Application>().applicationContext

    //filter notification
    fun getFilteredFlow(category: String): StateFlow<List<NotiUnit>> {
        return when (category) {
            "all" -> notifications  // Directly return the StateFlow
            "pinned" -> notifications.map { notiList: List<NotiUnit> ->
                notiList.filter { notiUnit: NotiUnit -> notiUnit.pinned }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            "social" -> notifications.map { notiList: List<NotiUnit> ->
                notiList.filter { notiUnit: NotiUnit ->
                    notiUnit.appName in listOf("Facebook", "Instagram", "LINE", "Messenger", "Slack")
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            "email" -> notifications.map { notiList: List<NotiUnit> ->
                notiList.filter { notiUnit: NotiUnit ->
                    notiUnit.appName == "Gmail"
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            else -> notifications  // fallback to all notifications
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    fun actOnNoti(notiUnit: NotiUnit, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val drawerDatabase = DrawerDatabase.getInstance(context)
            val drawerDao = drawerDatabase.drawerDao()
            val existingNoti = drawerDao.getBySbnKey(notiUnit.notiKey)
            if (existingNoti.isNotEmpty()) {
                when (action) {
                    "swipe_dismiss" -> existingNoti[0].removeNoti()
                    "click_dismiss" -> existingNoti[0].removeNoti()
                    "pin" -> existingNoti[0].flipNotiPin()
                }
                drawerDao.update(existingNoti[0])
            }
            if (action.contains("dismiss")) {
                postOngoingNotification(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun deleteAllNotis() {
        viewModelScope.launch(Dispatchers.IO) {
            val drawerDatabase = DrawerDatabase.getInstance(context)
            val drawerDao = drawerDatabase.drawerDao()
            drawerDao.deleteAllNotPinned()
            postOngoingNotification(context)
        }
    }

    fun resetGPTValues() {
        viewModelScope.launch(Dispatchers.IO) {
            val drawerDatabase = DrawerDatabase.getInstance(context)
            val drawerDao = drawerDatabase.drawerDao()
            val notifications = drawerDao.getAllVisible()
            for (noti in notifications)
                noti.resetGPTValues()
            drawerDao.updateList(notifications)
        }
    }

    // For testing
    val notiPostContent = MutableLiveData<String>()
    fun getPostContent(includeContext: Boolean) {

        viewModelScope.launch(Dispatchers.IO) {
            val notifications = getNotifications(context)
            val sb = StringBuilder()
            notifications.forEach { noti ->

                val isPeople = noti.isPeople
                val notiBody = noti.getNotiBody()
                val prevBody = noti.getPrevBody()

                val notiJson = JSONObject()
                notiJson.put("id", noti.hashKey)
                notiJson.put("app", noti.appName)

                val titlesIdentical = (notiBody + prevBody)
                    .map { it.title }
                    .filter { it.isNotBlank() }
                    .toSet().size == 1
                val notiType = if (isPeople) "message" else "info"
                val notiTypeTitle = if (isPeople) "sender" else "title"

                notiJson.put("overall_$notiTypeTitle", org.muilab.notigpt.util.replaceChars(noti.title))

                if (prevBody.isNotEmpty() && includeContext) {
                    val previousNotisArray = JSONArray()
                    prevBody.forEach {
                        val prevNotiJson = JSONObject()
                        prevNotiJson.put("time", getRelativeTimeStr(it.time))
                        prevNotiJson.put("relative_time", getRelativeTimeStr(it.time))
                        if (!titlesIdentical)
                            prevNotiJson.put(notiTypeTitle, org.muilab.notigpt.util.replaceChars(it.title))
                        prevNotiJson.put("content", org.muilab.notigpt.util.replaceChars(it.content))
                        previousNotisArray.put(prevNotiJson)
                    }
                    notiJson.put("previous_${notiType}s", previousNotisArray)

                    val newNotisArray = JSONArray()

                    notiBody.forEach {
                        val newNotiJson = JSONObject()
                        newNotiJson.put("time", getAbsoluteTimeStr(it.time))
                        newNotiJson.put("relative_time", getRelativeTimeStr(it.time))
                        if (!titlesIdentical)
                            newNotiJson.put(notiTypeTitle, org.muilab.notigpt.util.replaceChars(it.title))
                        newNotiJson.put("content", org.muilab.notigpt.util.replaceChars(it.content))
                        newNotisArray.put(newNotiJson)
                    }
                    notiJson.put("new_${notiType}s", newNotisArray)
                } else {
                    val notiInfosArray = JSONArray()

                    notiBody.forEach {
                        val notiInfoJson = JSONObject()
                        notiInfoJson.put("time", getRelativeTimeStr(it.time))
                        notiInfoJson.put("relative_time", getRelativeTimeStr(it.time))
                        if (!titlesIdentical)
                            notiInfoJson.put(notiTypeTitle, org.muilab.notigpt.util.replaceChars(it.title))
                        notiInfoJson.put("content", org.muilab.notigpt.util.replaceChars(it.content))
                        notiInfosArray.put(notiInfoJson)
                    }
                    notiJson.put("${notiType}s", notiInfosArray)
                }

                // Convert the JSON object to a string
                val notiJsonStr = notiJson.toString(2)
                sb.append("$notiJsonStr,\n")
            }
            notiPostContent.postValue("[\n${sb}]\n")
        }
    }
}