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
import org.muilab.notigpt.database.server.enqueueNotificationAction
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.postOngoingNotification
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.filter

class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository
) : AndroidViewModel(application) {

    private val _category = MutableStateFlow(NOTI_CATEGORY_GENERAL)
    val category: StateFlow<String> = _category

    // Whether we're currently waiting for the newly selected category/appCategory to load
    private val _isTargetLoading = MutableStateFlow(false)
    val isTargetLoading: StateFlow<Boolean> = _isTargetLoading.asStateFlow()

    // Token to mark a pending target-loading session. Set when user requests a new category/appCategory.
    // When `presentedNotifications` emits its next value after this token is set we assume loading finished
    // (even if the emitted list is empty) and consume the token by clearing the spinner.
    private val _targetLoadingToken = MutableStateFlow(0L)

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
        // Start loading indicator immediately so UI can show spinner before other work/recomposition
        _isTargetLoading.value = true
        // mark a pending loading session; will be consumed by the next presentedNotifications emission
        _targetLoadingToken.value = System.currentTimeMillis()
        Log.d("DrawerViewModel", "[VM:${this.hashCode()}] updateCategory: set isTargetLoading=true for newCategory=$newCategory")
        _category.value = newCategory
        if (isSortingMode.value)
            toggleSortingMode()
        persistReadStatus()
        // Reset app category to "All" when main category changes
        updateUnreadCounts()
        updateAppCategory(APP_CATEGORY_ALL)
        // Intentionally do not auto-clear the loading flag here; UX will clear when data arrives.
    }

    // app category
    private val _appCategory = MutableStateFlow(APP_CATEGORY_ALL)
    val appCategory: StateFlow<String> = _appCategory

    private val _isAppCategoryView = MutableStateFlow(false)
    val isAppCategoryView: StateFlow<Boolean> = _isAppCategoryView

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {
        // Start loading indicator immediately so UI can show spinner before other work/recomposition
        _isTargetLoading.value = true
        // mark a pending loading session; will be consumed by the next presentedNotifications emission
        _targetLoadingToken.value = System.currentTimeMillis()
        Log.d("DrawerViewModel", "[VM:${this.hashCode()}] updateAppCategory: set isTargetLoading=true for newAppCategory=$newAppCategory")

        if (isSortingMode.value)
            toggleSortingMode()
        persistReadStatus()
        // Reset app category to "All" when main category changes
        updateUnreadCounts()
        _isAppCategoryView.value = newAppCategory != APP_CATEGORY_ALL
        _appCategory.value = newAppCategory
        // Intentionally do not auto-clear the loading flag here; UX will clear when data arrives.
    }

    // Called by UI when data matching the target arrives
    fun clearTargetLoading() {
        Log.d("DrawerViewModel", "[VM:${this.hashCode()}] clearTargetLoading called; clearing isTargetLoading")
        _isTargetLoading.value = false
    }

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    private val _optimisticNotifications = MutableStateFlow<List<NotiDisplayUnit>>(emptyList())
    val optimisticNotifications: StateFlow<List<NotiDisplayUnit>> = _optimisticNotifications.asStateFlow()

    private val _reorderRecords = MutableStateFlow<Map<String, Pair<Int, Long>>>(emptyMap())

    fun onNotificationMoved(key: String, from: Int, to: Int) {
        val currentTime = System.currentTimeMillis()
        isOrderDirty = true
        _optimisticNotifications.value = _optimisticNotifications.value.toMutableList().apply {
            add(to, removeAt(from))
        }
        _reorderRecords.value = _reorderRecords.value.toMutableMap().apply {
            put(key, Pair(to, currentTime))
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
        val listSize = listAfterMove.size
        isOrderDirty = false // Reset immediately
        _reorderRecords.value = emptyMap() // Clear the reorder records

        if (listAfterMove.isEmpty() || listAfterMove == listBeforeMove)
            return

        withContext(Dispatchers.IO) {
            if (updates.isNotEmpty()) {
                // Use the highly efficient bulk update
                notiRepository.updateSortPositionsInBulk(updates, isAppCategoryView, listSize)
                Log.d("DrawerViewModel", "Smart bulk committed manual sort order for ${updates.size} items.")
            }
        }
    }

    fun resetManualSortOrder(notiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.resetSortPosition(notiKey, isAppCategoryView.value)
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
        }
        // Collapse rapid bursts of updates so Compose isn't asked to recompose excessively.
        // 60 ms is a small latency tradeoff but dramatically reduces UI jank from quick bursts.
        .debounce(60)
        .onEach { newList ->
            // Sync the fully sorted and filtered list from the database to our optimistic UI list
            val prev = _optimisticNotifications.value
            if (!shallowEqualUiList(prev, newList)) {
                _optimisticNotifications.value = newList
            }
        }.launchIn(viewModelScope)

        optimisticNotifications.onEach { notifications ->
            updateUnreadCounts()
            // Log sizes for debugging
            Log.d("DrawerViewModel", "optimisticNotifications emitted: size=${notifications.size}, isTargetLoading=${_isTargetLoading.value}")
            // If we were showing the target-loading spinner and notifications matching
            // the current selection have arrived (after filtering), clear the loading state.
            if (_isTargetLoading.value) {
                val cat = category.value
                val appCat = appCategory.value
                val hasMatching = notifications.any { it.category == cat && it.notiUnit.appCategory == appCat }
                Log.d("DrawerViewModel", "optimistic check: cat=$cat appCat=$appCat hasMatching=$hasMatching")
                if (hasMatching) {
                    Log.d("DrawerViewModel", "[VM:${this.hashCode()}] Clearing isTargetLoading due to optimisticNotifications match")
                    _isTargetLoading.value = false
                }
            }
        }.launchIn(viewModelScope)

        // Additionally, clear the loading flag as soon as the parent-level presentedNotifications
        // (which represent the units) emit entries for the new target. This avoids waiting for
        // the record-level fetch to finish — the UI can hide the spinner as soon as units are ready.
        presentedNotifications.onEach { units ->
            Log.d("DrawerViewModel", "presentedNotifications emitted: parentUnits=${units.size}, isTargetLoading=${_isTargetLoading.value}, token=${_targetLoadingToken.value}")
            // If a loading token is present, consume it on the first emission (even if units is empty)
            if (_targetLoadingToken.value != 0L) {
                Log.d("DrawerViewModel", "[VM:${this.hashCode()}] Consuming target loading token and clearing spinner")
                _isTargetLoading.value = false
                _targetLoadingToken.value = 0L
            } else if (_isTargetLoading.value) {
                // Backwards-compatible behavior: if no token but spinner is on and units arrived, clear only when units exist
                if (units.isNotEmpty()) {
                    Log.d("DrawerViewModel", "[VM:${this.hashCode()}] Clearing isTargetLoading because presentedNotifications has units (${units.size})")
                    _isTargetLoading.value = false
                } else {
                    Log.d("DrawerViewModel", "[VM:${this.hashCode()}] presentedNotifications empty for current target; keeping spinner")
                }
            }
        }.launchIn(viewModelScope)

        removeExpiredRecords()
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

            if (action == "pin" || action == "unpin") {
                // Find the notification in optimistic notification and update its pin state
                val index = _optimisticNotifications.value.indexOfFirst { it.notiKey == notiKey }
                if (index != -1) {
                    val updatedNoti = _optimisticNotifications.value[index].notiUnit.copy()
                    updatedNoti.isPinned = action == "pin"
                    _optimisticNotifications.value = _optimisticNotifications.value.toMutableList().apply {
                        set(index, NotiDisplayUnit(updatedNoti, _optimisticNotifications.value[index].notiRecords))
                    }
                }
            }

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

    @RequiresApi(Build.VERSION_CODES.S)
    fun markAllNotisRead() {
        persistReadStatus()
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.markAllNotisRead(category.value)
        }
        updateUnreadCounts()
    }

    fun exportPostContent(includeContext: Boolean, includeDismissed: Boolean) {

        viewModelScope.launch(Dispatchers.IO) {
            val notiLogs = notiRepository.exportLog(includeContext, includeDismissed)
            val notiLogsStr = notiLogs.toString(2)

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
                    outputStream.write(notiLogsStr.toByteArray())
                }
            }

            // copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", notiLogsStr)
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
    private val _seenNotiKeys = ConcurrentHashMap.newKeySet<Pair<String, Long>>()
    private val _seenRecordIds = ConcurrentHashMap.newKeySet<String>()

    // MODIFICATION 1: Update the optimistic data directly
    fun markNotificationAsRead(notiKey: String, isManual: Boolean) {

        if (isManual) {
            viewModelScope.launch(Dispatchers.IO) {
                notiRepository.actOnNoti(notiKey, "mark_read")
            }
            return
        }

        // Find the notification in the current list
        val currentList = _optimisticNotifications.value.toMutableList()
        val notiIndex = currentList.indexOfFirst { it.notiKey == notiKey }

        if (notiIndex != -1) {
            val oldNoti = currentList[notiIndex]
            // If it's already marked as read, do nothing
            if (oldNoti.notiUnit.isCompletelyRead) return

            Log.d("ViewModelReadState", "Marking card as read: $notiKey")
            val currentTime = System.currentTimeMillis()
            _seenNotiKeys.add(Pair(notiKey, currentTime))
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
                val currentTime = System.currentTimeMillis()
                _seenNotiKeys.add(Pair(parentNoti.notiKey, currentTime)) // Add to persistence queue
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

    fun removeExpiredRecords() {
        viewModelScope.launch { // No dispatcher needed here
            notiRepository.removeExpiredNotiRecords()
        }
    }

    private val _unreadCountsByCategory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByCategory = _unreadCountsByCategory.asStateFlow()

    private fun updateUnreadCounts() {

        viewModelScope.launch(Dispatchers.IO) {

            val counts = mutableMapOf<String, Int>()

            val generalCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_GENERAL)
            val taskCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_MAKETASK)
            val saveCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_SAVE)
            val archiveCount = notiRepository.getVisibleNotReadNotificationCountByCategory(NOTI_CATEGORY_ARCHIVE)

            counts[NOTI_CATEGORY_GENERAL] = generalCount
            counts[NOTI_CATEGORY_MAKETASK] = taskCount
            counts[NOTI_CATEGORY_SAVE] = saveCount
            counts[NOTI_CATEGORY_ARCHIVE] = archiveCount

            val generalTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_GENERAL)
            val taskTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_MAKETASK)
            val archiveTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_ARCHIVE)
            val saveTotalCount = notiRepository.getVisibleNotiCountByCategory(NOTI_CATEGORY_SAVE)


            counts["$NOTI_CATEGORY_GENERAL-Total"] = generalTotalCount
            counts["$NOTI_CATEGORY_MAKETASK-Total"] = taskTotalCount
            counts["$NOTI_CATEGORY_SAVE-Total"] = saveTotalCount
            counts["$NOTI_CATEGORY_ARCHIVE-Total"] = archiveTotalCount

            Log.d("DrawerViewModel", "Unread counts updated: $counts")

            _unreadCountsByCategory.value = counts
        }
    }

    fun logAction(notiKey: String, action: String, metadata: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.logAction(notiKey, action, metadata)
        }
    }

    fun extractRandomTasks(count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.requestRandomTaskExtraction(count)
        }
    }

    // Shallow equality check for the UI list to avoid pointless updates/recompositions.
    private fun shallowEqualUiList(a: List<NotiDisplayUnit>, b: List<NotiDisplayUnit>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            val ai = a[i]
            val bi = b[i]
            if (ai.notiKey != bi.notiKey) return false
            if (ai.notiRecords.size != bi.notiRecords.size) return false
            if (ai.lastUpdateTime != bi.lastUpdateTime) return false
        }
        return true
    }

    // --- Per-key full-records cache & live subscription (systematic lazy-load approach) ---
    private val fullRecordsCache = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.MutableStateFlow<List<org.muilab.notigpt.model.notifications.NotiRecord>>>()
    // Track active collector jobs so we can cancel subscriptions when not needed
    private val fullRecordsJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun getFullRecordsFlow(notiKey: String): kotlinx.coroutines.flow.StateFlow<List<org.muilab.notigpt.model.notifications.NotiRecord>> {
        return fullRecordsCache.getOrPut(notiKey) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }
    }

    /**
     * Subscribe to a live flow for the given notiKey and forward updates into the cached StateFlow.
     * If a subscription already exists, this is a no-op. If the cached StateFlow already has data
     * we still subscribe so the expanded view stays live. This uses viewModelScope so it survives
     * configuration changes while the VM is active.
     */
    fun loadFullRecordsForKey(notiKey: String) {
        // If there's already an active job for this key, do nothing (already subscribed)
        if (fullRecordsJobs.containsKey(notiKey)) return

        val stateFlow = fullRecordsCache.getOrPut(notiKey) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }

        // Launch a subscription that collects the repository's live Flow and updates stateFlow
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                notiRepository.visibleRecordsFlowForKey(notiKey)
                    .collect { recs ->
                        // Re-sort by time ascending to keep the UI expectation
                        stateFlow.value = recs.sortedBy { it.time }
                    }
            } catch (e: Exception) {
                Log.e("DrawerViewModel", "Error subscribing to full records for $notiKey", e)
            } finally {
                // Ensure we remove the job entry when collector finishes
                fullRecordsJobs.remove(notiKey)
            }
        }

        fullRecordsJobs[notiKey] = job
    }

    /** Cancel subscription and clear cached full records for the key. */
    fun clearFullRecordsForKey(notiKey: String) {
        fullRecordsJobs.remove(notiKey)?.cancel()
        fullRecordsCache.remove(notiKey)
    }
}