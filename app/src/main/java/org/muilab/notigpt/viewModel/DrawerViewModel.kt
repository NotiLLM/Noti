package org.muilab.notigpt.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.workers.ApiWorker
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.paging.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.API_SYNC_QUERY
import org.muilab.notigpt.util.compressedBase64ToDoubleArray
import org.muilab.notigpt.util.cosineSimilarity
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getNotifications
import org.muilab.notigpt.util.getRelativeTimeStr
import org.muilab.notigpt.util.postOngoingNotification
import org.muilab.notigpt.util.resetSimilarity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.collections.filter

class DrawerViewModel(
    application: Application,
    notiRepository: NotiRepository
) : AndroidViewModel(application) {

    private val _category = MutableStateFlow("")
    val category: StateFlow<String> = _category

    fun updateCategory(newCategory: String) {
        _category.value = newCategory
    }

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    private val lastValidEmbedding = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val queryEmbeddingBase64: Flow<String?> = _queryString
        .debounce(500)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                resetSimilarity(context)
                flowOf(null)
            } else {
                flow<String?> {
                    emit(null) // UI shows loading state
                    val inputData = Data.Builder()
                        .putString("api_type", API_SYNC_QUERY)
                        .putString("query_string", query)
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()

                    val workManager = WorkManager.getInstance(application)
                    workManager.enqueue(workRequest)

                    val workInfo = workManager
                        .getWorkInfoByIdLiveData(workRequest.id)
                        .asFlow()
                        .filter { it?.state!!.isFinished }
                        .firstOrNull()

                    if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                        val embeddingString = workInfo.outputData.getString("embeddingString")
                        if (!embeddingString.isNullOrBlank()) {
                            lastValidEmbedding.value = embeddingString
                            emit(embeddingString)
                        }
                    } else {
                        resetSimilarity(context)
                        emit(lastValidEmbedding.value) // ✅ Use the last valid embedding instead of null
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)

    private val notificationFlow: Flow<List<NotiUnit>> = notiRepository.getNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sortedNotifications: StateFlow<List<NotiUnit>> = queryEmbeddingBase64
        .flatMapLatest { currentQueryEmbedding ->
            notificationFlow.map { notifications ->
                val embeddingToUse = currentQueryEmbedding ?: lastValidEmbedding.value
                notifications.map { noti ->
                    val similarity = if (queryString.value.isBlank()) {
                        lastValidEmbedding.value = null
                        -1.0
                    } else if (embeddingToUse != null) {
                        cosineSimilarity(embeddingToUse, noti.embeddingString)
                    } else {
                        noti.outcome.similarityScore
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val drawerDatabase = DrawerDatabase.getInstance(context)
                        val drawerDao = drawerDatabase.drawerDao()
                        drawerDao.updateSimilarity(noti.notiKey, similarity)
                    }

                    noti.withUpdatedSimilarity(similarity)
                }.sortedWith(
                    compareByDescending<NotiUnit> { noti ->
                        noti.getNotiBody().any { notiInfo ->
                            listOf(notiInfo.title, notiInfo.content, notiInfo.person)
                                .any { it.contains(queryString.value, ignoreCase = true) }
                        }
                    }.thenByDescending { noti ->
                        noti.outcome.similarityScore
                    }.thenByDescending { noti ->
                        noti.getAbsLatestTimeStr()
                    }
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val presentedNotifications: StateFlow<List<NotiUnit>> =
        combine(category, sortedNotifications) { latestCategory, notiList ->
            when (latestCategory) {
                "pinned" -> notiList.filter { it.pinned }
                "social" -> notiList.filter {
                    it.appName in listOf("Facebook", "Instagram", "LINE", "Messenger", "Slack")
                }
                "email" -> notiList.filter { it.appName in listOf("Gmail") }
                else -> notiList
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notSeenCount: LiveData<Int> = notiRepository.notSeenCount

    @SuppressLint("StaticFieldLeak")
    val context: Context = getApplication<Application>().applicationContext

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

    fun resetLLMValues() {
        viewModelScope.launch(Dispatchers.IO) {
            val drawerDatabase = DrawerDatabase.getInstance(context)
            val drawerDao = drawerDatabase.drawerDao()
            val notifications = drawerDao.getAllVisible()
            for (noti in notifications)
                noti.resetLLMValues()
            drawerDao.updateList(notifications)
        }
    }

    fun exportPostContent(includeContext: Boolean) {

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
                        prevNotiJson.put("time", getAbsoluteTimeStr(it.time))
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
                        notiInfoJson.put("time", getAbsoluteTimeStr(it.time))
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

            val notiPostContent = "[\n${sb}]\n"
            // save to file
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "notigpt.txt")
            try {
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(notiPostContent.toByteArray(Charsets.UTF_8))
                    Toast.makeText(context, "Data saved to Downloads folder as notigpt.txt", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(context, "Failed to save notification data", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
            // copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", notiPostContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}