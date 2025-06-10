package org.muilab.notigpt.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.room.NotiCategoryDatabase
import org.muilab.notigpt.database.server.enqueueNotificationAction
import org.muilab.notigpt.model.notifications.NotiCategory
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr
import org.muilab.notigpt.util.postOngoingNotification
import kotlin.collections.filter

class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository
) : AndroidViewModel(application) {

    private val _category = MutableStateFlow(NOTI_CATEGORY_GENERAL)
    val category: StateFlow<String> = _category

    fun updateCategory(newCategory: String) {
        _category.value = newCategory
        // Reset app category to "All" when main category changes
        _appCategory.value = APP_CATEGORY_ALL
    }

    // app category
    private val _appCategory = MutableStateFlow(APP_CATEGORY_ALL)
    val appCategory: StateFlow<String> = _appCategory

    fun updateAppCategory(newAppCategory: String) {
        _appCategory.value = newAppCategory
    }

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    private val _notiCategories = MutableStateFlow(listOf<NotiCategory>())
    val notiCategories: StateFlow<List<NotiCategory>> = _notiCategories

    private val _notiCategoryCount = MutableStateFlow(0)
    val notiCategoryCount: StateFlow<Int> = _notiCategoryCount

    fun loadCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val notiCategoryDatabase = NotiCategoryDatabase.getInstance(context)
            val categoryDao = notiCategoryDatabase.categoryDao()
            _notiCategories.value = categoryDao.getAll()
            _notiCategoryCount.value = categoryDao.getCount()
        }
    }

    fun insertCategory(newCategoryName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val notiCategoryDatabase = NotiCategoryDatabase.getInstance(context)
            val categoryDao = notiCategoryDatabase.categoryDao()
            categoryDao.insert(NotiCategory(categoryName = newCategoryName))
            loadCategories()
        }
    }

    fun deleteCategory(deletedCategoryName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val notiCategoryDatabase = NotiCategoryDatabase.getInstance(context)
            val categoryDao = notiCategoryDatabase.categoryDao()
            categoryDao.deleteCategory(deletedCategoryName)
            loadCategories()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val queryEmbeddingString: Flow<String?> = _queryString
        .debounce(500)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(null)
            } else {
                flow<String?> {
                    // TODO: Similarity Search
                }
            }
        }
        .flowOn(Dispatchers.IO)

    private val notificationDisplayFlow: Flow<List<NotiDisplayUnit>> = notiRepository.getNotificationDisplayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sortedNotifications: StateFlow<List<NotiDisplayUnit>> = queryEmbeddingString
        .flatMapLatest { currentQueryEmbedding ->
            notificationDisplayFlow.map { notifications ->
                notifications
                    .sortedWith(
                        compareByDescending<NotiDisplayUnit> { notiDisplayUnit ->
                            notiDisplayUnit.notiRecords.any { notiRecord ->
                                listOf(notiRecord.extraTitle, notiRecord.content, notiRecord.person)
                                    .any { it.contains(queryString.value, ignoreCase = true) }
                            }
                    }.thenByDescending { notiDisplayUnit ->
                        notiDisplayUnit.sortScore
                    }.thenByDescending { notiDisplayUnit ->
                        notiDisplayUnit.lastUpdateTime
                    }
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // First level filtering（by category）
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredByCategory: StateFlow<List<NotiDisplayUnit>> =
        combine(category, sortedNotifications) { latestCategory, notiList ->
            when (latestCategory) {
                NOTI_CATEGORY_GENERAL -> notiList.filter { it.category.isBlank() || it.category == latestCategory }
                else -> notiList.filter { it.category == latestCategory }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Second level filtering（by app category）
    @OptIn(ExperimentalCoroutinesApi::class)
    val presentedNotifications: StateFlow<List<NotiDisplayUnit>> =
        combine(appCategory, filteredByCategory) { latestAppCategory, notiList ->
            when (latestAppCategory) {
                APP_CATEGORY_ALL -> notiList
                else -> notiList.filter { it.notiUnit.appCategory == latestAppCategory }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Get available app categories for the current primary category with notification counts
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableAppCategories: StateFlow<List<Pair<String, Int>>> =
        filteredByCategory.map { notiList ->
            val categoryCounts = notiList
                .groupBy { it.notiUnit.appCategory }
                .mapValues { it.value.size }
                .toMutableMap()
            
            // Always include "All" category with total count
            val totalCount = notiList.size
            val result = mutableListOf<Pair<String, Int>>()
            if (totalCount > 0) {
                result.add(APP_CATEGORY_ALL to totalCount)
            }
            
            // Add other categories sorted by count (descending)
            categoryCounts.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .forEach { (category, count) ->
                    result.add(category to count)
                }
            
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @SuppressLint("StaticFieldLeak")
    val context: Context = getApplication<Application>().applicationContext

    @RequiresApi(Build.VERSION_CODES.S)
    fun actOnNoti(notiKey: String, action: String) {

        fun checkIfTrackAction(): Boolean {
            if (action == "pin" && SharedPreferencesManager.trackPin)
                return true
            if (action == "archive" && SharedPreferencesManager.autoArchive)
                return true
            if (action == "dismiss_click" && SharedPreferencesManager.autoDelete)
                return true
            return false
        }

        viewModelScope.launch(Dispatchers.IO) {

            if (checkIfTrackAction())
                enqueueNotificationAction(context, notiKey, action)

            notiRepository.actOnNoti(notiKey, action)
            if (action.contains("dismiss")) {
                postOngoingNotification(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun deleteAllNotis() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.deleteAllNotis(category.value)
            postOngoingNotification(context)
        }
    }

    fun exportPostContent(includeContext: Boolean, includeDismissed: Boolean) {

        viewModelScope.launch(Dispatchers.IO) {
            val notifications = notiRepository.getNotifications(includeContext, includeDismissed)
            val sb = StringBuilder()
            notifications.forEach { notiDisplayUnit ->

                val notiUnit = notiDisplayUnit.notiUnit
                val notiRecords = notiDisplayUnit.notiRecords

                val notiBody = notiRecords.filter { it.isVisible }
                val prevBody = notiRecords.filter { !it.isVisible }

                val notiJson = JSONObject()
                notiJson.put("id", notiUnit.notiKey)
                notiJson.put("app", notiUnit.appName)
                notiJson.put("isPeople", notiUnit.isPeople)

                val titlesIdentical = (notiBody + prevBody)
                    .map { it.extraTitle }
                    .filter { it.isNotBlank() }
                    .toSet().size == 1

                notiJson.put("noti_title", org.muilab.notigpt.util.replaceChars(notiDisplayUnit.title))

                val previousNotisArray = JSONArray()
                if (includeContext) {
                    prevBody.forEach {
                        val prevNotiJson = JSONObject()
                        prevNotiJson.put("absolute_time", getAbsoluteTimeStr(it.time))
                        prevNotiJson.put("relative_time", getRelativeTimeStr(it.time))
                        prevNotiJson.put(
                            "record_title",
                            org.muilab.notigpt.util.replaceChars(it.getDisplayedTitle(notiUnit.isPeople))
                        )
                        prevNotiJson.put(
                            "content",
                            org.muilab.notigpt.util.replaceChars(it.content)
                        )
                        previousNotisArray.put(prevNotiJson)
                    }
                }
                notiJson.put("previous_records", previousNotisArray)

                val currentNotisArray = JSONArray()

                notiBody.forEach {
                    val currentNotiJson = JSONObject()
                    currentNotiJson.put("absolute_time", getAbsoluteTimeStr(it.time))
                    currentNotiJson.put("relative_time", getRelativeTimeStr(it.time))
                    currentNotiJson.put("record_title", org.muilab.notigpt.util.replaceChars(it.getDisplayedTitle(notiUnit.isPeople)))
                    currentNotiJson.put("content", org.muilab.notigpt.util.replaceChars(it.content))
                    currentNotisArray.put(currentNotiJson)
                }
                notiJson.put("current_records", currentNotisArray)

                // Convert the JSON object to a string
                val notiJsonStr = notiJson.toString(2)
                sb.append("$notiJsonStr,\n")
            }

            val notiPostContent = "[\n${sb}]\n"
            // save to file
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "notigpt.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(notiPostContent.toByteArray())
                }
            }

            // copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", notiPostContent)
            clipboard.setPrimaryClip(clip)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}