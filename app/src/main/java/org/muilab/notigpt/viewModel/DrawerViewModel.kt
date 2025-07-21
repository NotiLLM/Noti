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
import android.util.Log
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.server.enqueueNotificationAction
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr
import org.muilab.notigpt.util.postOngoingNotification
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.filter

class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository
) : AndroidViewModel(application) {

    private val _category = MutableStateFlow(NOTI_CATEGORY_GENERAL)
    val category: StateFlow<String> = _category

    private val _isSortingMode = MutableStateFlow(false)
    val isSortingMode: StateFlow<Boolean> = _isSortingMode

    fun toggleSortingMode() {
        _isSortingMode.value = !_isSortingMode.value
        if (!_isSortingMode.value) {
            syncManualSortOrder(isAppCategoryView.value)
        }
    }

    private var isOrderDirty = false

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateCategory(newCategory: String) {
        _category.value = newCategory
        if (isSortingMode.value)
            toggleSortingMode()
        persistReadStatus()
        // Reset app category to "All" when main category changes
        updateUnreadCounts()
        updateAppCategory(APP_CATEGORY_ALL)
    }

    // app category
    private val _appCategory = MutableStateFlow(APP_CATEGORY_ALL)
    val appCategory: StateFlow<String> = _appCategory

    private val _isAppCategoryView = MutableStateFlow(false)
    val isAppCategoryView: StateFlow<Boolean> = _isAppCategoryView

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {

        if (isSortingMode.value)
            toggleSortingMode()
        persistReadStatus()
        // Reset app category to "All" when main category changes
        updateUnreadCounts()
        if (newAppCategory == APP_CATEGORY_ALL) {
            _isAppCategoryView.value = false
        } else {
            _isAppCategoryView.value = true
        }
        _appCategory.value = newAppCategory
    }

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    private val _optimisticNotifications = MutableStateFlow<List<NotiDisplayUnit>>(emptyList())
    val optimisticNotifications: StateFlow<List<NotiDisplayUnit>> = _optimisticNotifications.asStateFlow()

    private val _reorderRecords = MutableStateFlow<Map<String, Int>>(emptyMap())
    val reorderRecords: StateFlow<Map<String, Int>> = _reorderRecords.asStateFlow()

    fun onNotificationMoved(key: String, from: Int, to: Int) {
        isOrderDirty = true
        _optimisticNotifications.value = _optimisticNotifications.value.toMutableList().apply {
            add(to, removeAt(from))
        }
        _reorderRecords.value = _reorderRecords.value.toMutableMap().apply {
            put(key, to)
        }
    }

    fun syncManualSortOrder(isAppCategoryView: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            commitManualSortOrder(isAppCategoryView)
        }
    }

    suspend fun commitManualSortOrder(isAppCategoryView: Boolean) {
        if (!isOrderDirty) return

        val listAfterMove = _optimisticNotifications.value.toList()
        val listBeforeMove = presentedNotifications.value.toList()
        val updates = _reorderRecords.value.mapNotNull { (key, newPos) ->
            Pair(key, newPos)
        }
        isOrderDirty = false // Reset immediately
        _reorderRecords.value = emptyMap() // Clear the reorder records

        if (listAfterMove.isEmpty() || listAfterMove == listBeforeMove)
            return

        withContext(Dispatchers.IO) {
            if (updates.isNotEmpty()) {
                // Use the highly efficient bulk update
                notiRepository.updateSortPositionsInBulk(updates, isAppCategoryView)
                Log.d("DrawerViewModel", "Smart bulk committed manual sort order for ${updates.size} items.")
            }
        }
    }

    fun resetAllManualSortOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.resetAllSortPositions()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "All manual sort orders have been reset.", Toast.LENGTH_SHORT).show()
            }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val manuallySortedNotifications: Flow<List<NotiDisplayUnit>> =
        notiRepository.getManuallySorted(category, appCategory, isAppCategoryView)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val autoSortedNotifications: Flow<List<NotiDisplayUnit>> =
        notiRepository.getAutoSorted(category, appCategory, isAppCategoryView)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val presentedNotifications: StateFlow<List<NotiDisplayUnit>> =
        combine(
            manuallySortedNotifications,
            autoSortedNotifications,
            isAppCategoryView
        ) { manualList, autoList, isAppView ->

            // YOUR ORIGINAL LOGIC IS HERE - NOW OPERATING ON SMALL, PRE-SORTED LISTS
            if (manualList.isEmpty()) {
                autoList // Optimization: if no manual items, just return the auto-sorted list
            } else {
                fun getPos(unit: NotiDisplayUnit) = if (isAppView)
                    unit.notiUnit.appCategorySortPosition else unit.notiUnit.sortPosition

                val result = mutableListOf<NotiDisplayUnit>()
                val autoSortedIterator = autoList.iterator()
                var manualItemIndex = 0

                // The total size is simply the sum of the two list sizes
                val finalSize = manualList.size + autoList.size
                var itemsAddedCount = 0

                while (itemsAddedCount < finalSize) {
                    val currentPos = result.size
                    val manualItem = manualList.getOrNull(manualItemIndex)

                    if (manualItem != null && getPos(manualItem) == currentPos) {
                        result.add(manualItem)
                        manualItemIndex++
                    } else if (autoSortedIterator.hasNext()) {
                        result.add(autoSortedIterator.next())
                    } else if (manualItem != null) {
                        // This handles any remaining manual items if auto-list is exhausted
                        result.add(manualItem)
                        manualItemIndex++
                    } else {
                        break // Should not happen, but a safeguard
                    }
                    itemsAddedCount++
                }
                val seenKeys = mutableSetOf<String>()
                result.filter { item -> seenKeys.add(item.notiKey) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Third level filtering (by query string) and sync to optimistic list
    init {
        combine(queryString, presentedNotifications) { query, notiList ->
            if (query.isBlank()) {
                notiList
            } else {
                notiList.filter { notiDisplayUnit ->
                    notiDisplayUnit.title.contains(query, ignoreCase = true) ||
                            notiDisplayUnit.notiUnit.appName.contains(query, ignoreCase = true) ||
                            notiDisplayUnit.notiRecords.any { record ->
                                listOf(record.title, record.content, record.person)
                                    .any { it.contains(query, ignoreCase = true) }
                            }
                }
            }
        }.onEach {
            // Sync the fully sorted and filtered list from the database to our optimistic UI list
            _optimisticNotifications.value = it
        }.launchIn(viewModelScope)

        optimisticNotifications.onEach { notifications ->
            updateUnreadCounts()
        }.launchIn(viewModelScope)
    }

    // Get available app categories for the current primary category with notification counts
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableAppCategories: StateFlow<List<Pair<String, Int>>> =
        presentedNotifications.map { notiList ->
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
            if (action == "dismiss_swipe" && SharedPreferencesManager.autoDelete)
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
        updateUnreadCounts()
    }

    fun markAllNotisRead() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.markAllNotisRead(category.value)
        }
        updateUnreadCounts()
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

    fun syncAppCategory() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.syncAppCategories(context)
        }
    }

    // Add these properties to your DrawerViewModel
    private val _seenNotiKeys = ConcurrentHashMap.newKeySet<String>()
    private val _seenRecordIds = ConcurrentHashMap.newKeySet<String>()

    // MODIFICATION 1: Update the optimistic data directly
    fun markNotificationAsRead(notiKey: String) {
        // Find the notification in the current list
        val currentList = _optimisticNotifications.value.toMutableList()
        val notiIndex = currentList.indexOfFirst { it.notiKey == notiKey }

        if (notiIndex != -1) {
            val oldNoti = currentList[notiIndex]
            // If it's already marked as read, do nothing
            if (oldNoti.notiUnit.isCompletelyRead) return

            Log.d("ViewModelReadState", "Marking card as read: $notiKey")
            _seenNotiKeys.add(notiKey)
        }
    }

    // MODIFICATION 2: Update the optimistic data for single records
    fun markRecordAsRead(recordId: String) {
        val currentList = _optimisticNotifications.value.toMutableList()
        var notiIndex = -1
        var recordIndex = -1
        var parentNoti: NotiDisplayUnit? = null

        // Find the notification and the record within it
        for ((nIdx, noti) in currentList.withIndex()) {
            val rIdx = noti.notiRecords.indexOfFirst { it.notiRecordId == recordId }
            if (rIdx != -1) {
                notiIndex = nIdx
                recordIndex = rIdx
                parentNoti = noti
                break
            }
        }

        if (notiIndex != -1 && recordIndex != -1 && parentNoti != null) {
            // If it's already marked as read, do nothing
            if (parentNoti.notiRecords[recordIndex].isRead) return

            Log.d("ViewModelReadState", "Marking record as read: $recordId")
            _seenRecordIds.add(recordId)

            // Create a new updated record
            val updatedRecords = parentNoti.notiRecords.toMutableList()
            updatedRecords[recordIndex] = updatedRecords[recordIndex].copy(isRead = true)

            // Check if ALL records are now read. If so, mark the whole unit as read.
            val allRecordsRead = updatedRecords.all { it.isRead }
            if (allRecordsRead) {
                _seenNotiKeys.add(parentNoti.notiKey) // Add to persistence queue
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun persistReadStatus() {
        // A function to be called from DisposableEffect in the UI
        if (_seenNotiKeys.isEmpty() && _seenRecordIds.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ViewModelReadState", "Persisting seenNotis: ${_seenNotiKeys.size}, seenRecords: ${_seenRecordIds.size}")
            notiRepository.updateSeenNotifications(_seenNotiKeys.toSet(), _seenRecordIds.toSet())
            // Clear them after persisting
            _seenNotiKeys.clear()
            _seenRecordIds.clear()
        }
    }

    private val _unreadCountsByCategory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByCategory = _unreadCountsByCategory.asStateFlow()

    private fun updateUnreadCounts() {

        viewModelScope.launch(Dispatchers.IO) {

            val counts = mutableMapOf<String, Int>()

            val generalCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_GENERAL)
            val taskCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_MAKETASK)
            val archiveCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_ARCHIVE)

            counts[NOTI_CATEGORY_GENERAL] = generalCount
            counts[NOTI_CATEGORY_MAKETASK] = taskCount
            counts[NOTI_CATEGORY_ARCHIVE] = archiveCount

            val generalTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_GENERAL)
            val taskTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_MAKETASK)
            val archiveTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_ARCHIVE)

            counts["$NOTI_CATEGORY_GENERAL-Total"] = generalTotalCount
            counts["$NOTI_CATEGORY_MAKETASK-Total"] = taskTotalCount
            counts["$NOTI_CATEGORY_ARCHIVE-Total"] = archiveTotalCount

            Log.d("DrawerViewModel", "Unread counts updated: $counts")

            _unreadCountsByCategory.value = counts
        }
    }
}