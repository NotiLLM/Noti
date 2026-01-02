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
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
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

    private val _isTargetLoading = MutableStateFlow(false)
    val isTargetLoading: StateFlow<Boolean> = _isTargetLoading.asStateFlow()

    private val _targetLoadingToken = MutableStateFlow(0L)

    private val _isSortingMode = MutableStateFlow(false)
    val isSortingMode: StateFlow<Boolean> = _isSortingMode

    fun toggleSortingMode() {
        _isSortingMode.value = !_isSortingMode.value
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateCategory(newCategory: String) {
        _isTargetLoading.value = true
        _targetLoadingToken.value = System.currentTimeMillis()
        _category.value = newCategory
        if (isSortingMode.value) toggleSortingMode()
        persistReadStatus()
        updateUnreadCounts()
        updateAppCategory(APP_CATEGORY_ALL)
    }

    private val _appCategory = MutableStateFlow(APP_CATEGORY_ALL)
    val appCategory: StateFlow<String> = _appCategory

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {
        _isTargetLoading.value = true
        _targetLoadingToken.value = System.currentTimeMillis()
        if (isSortingMode.value) toggleSortingMode()
        persistReadStatus()
        updateUnreadCounts()
        _appCategory.value = newAppCategory
    }

    fun clearTargetLoading() {
        _isTargetLoading.value = false
    }

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    // Main List State
    private val _groupedNotifications = MutableStateFlow<List<NotiDrawerItem>>(emptyList())
    val groupedNotifications: StateFlow<List<NotiDrawerItem>> = _groupedNotifications.asStateFlow()

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

    private val _includeHistory = MutableStateFlow(false)
    val includeHistory: StateFlow<Boolean> = _includeHistory

    fun toggleIncludeHistory(enabled: Boolean) {
        _includeHistory.value = enabled
        // Re-trigger search if query exists
        if (_queryString.value.isNotBlank()) {
            performSearch(_queryString.value)
        }
    }

    // Holds search results: Map of NotiKey -> List of Records to display
    private val _searchResults = MutableStateFlow<Map<String, List<NotiRecord>>>(emptyMap())
    val searchResults: StateFlow<Map<String, List<NotiRecord>>> = _searchResults

    // Holds the NotiUnit data for the keys found in search
    private val _searchUnits = MutableStateFlow<Map<String, NotiUnit>>(emptyMap())
    val searchUnits: StateFlow<Map<String, NotiUnit>> = _searchUnits

    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        // Main subscription to the grouped data flow
        notiRepository.getGroupedNotifications(category, appCategory)
            .debounce(60)
            .onEach { newList ->
                val prev = _groupedNotifications.value
                // Simple reference/size check to avoid some recompositions,
                // though NotiDrawerItem equality checks would be better.
                if (prev.size != newList.size || prev != newList) {
                    _groupedNotifications.value = newList
                }
            }.launchIn(viewModelScope)

        // Loading state management
        groupedNotifications.onEach { notifications ->
            updateUnreadCounts()
            Log.d("DrawerViewModel", "groupedNotifications emitted: size=${notifications.size}, isTargetLoading=${_isTargetLoading.value}")

            if (_targetLoadingToken.value != 0L) {
                _isTargetLoading.value = false
                _targetLoadingToken.value = 0L
            } else if (_isTargetLoading.value) {
                // Check if items match current filters to decide if we stop loading
                // Approximate check
                if (notifications.isNotEmpty()) {
                    _isTargetLoading.value = false
                }
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            _queryString
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }

        removeExpiredRecords()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyMap()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isTargetLoading.value = true
            val results = notiRepository.searchNotifications(query, _includeHistory.value)

            // We also need the NotiUnits for these keys to display the app icon/name
            val unitMap = mutableMapOf<String, NotiUnit>()
            val keysToFetch = results.keys.filter { !_searchUnits.value.containsKey(it) }

            if (keysToFetch.isNotEmpty()) {
                val units = notiRepository.getNotiUnitByKeys(keysToFetch)
                units.forEach { unitMap[it.notiKey] = it }
            }
            // Merge with existing cached units
            _searchUnits.value += unitMap

            // Sort records in each group chronologically
            val sortedResults = results.mapValues { entry -> entry.value.sortedBy { it.time } }

            _searchResults.value = sortedResults
            _isTargetLoading.value = false
        }
    }

    fun loadSearchContext(notiKey: String, isOlder: Boolean) {
        val currentList = _searchResults.value[notiKey] ?: return
        if (currentList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val pivotTime = if (isOlder) currentList.first().time else currentList.last().time
            val newRecords = notiRepository.getContextRecords(notiKey, pivotTime, isOlder, _includeHistory.value)

            if (newRecords.isNotEmpty()) {
                val updatedList = if (isOlder) {
                    newRecords + currentList
                } else {
                    currentList + newRecords
                }
                // Update state
                val currentMap = _searchResults.value.toMutableMap()
                currentMap[notiKey] = updatedList
                _searchResults.value = currentMap
            }
        }
    }

    // This helper will be used by the UI to check if gap buttons should be shown
    suspend fun checkGapHasRecords(notiKey: String, startTime: Long, endTime: Long): Boolean {
        return notiRepository.hasRecordsInGap(notiKey, startTime, endTime, _includeHistory.value)
    }

    // [UPDATED] Load records for a gap, either from the start (Newer) or end (Older)
    fun loadGapRecords(notiKey: String, startTime: Long, endTime: Long, fromStart: Boolean) {
        val currentList = _searchResults.value[notiKey] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Fetch 10 records at a time
            val gapRecords = notiRepository.getGapRecords(notiKey, startTime, endTime, 10, fromStart)

            if (gapRecords.isNotEmpty()) {
                // Merge, Dedup, Sort
                val combined = (currentList + gapRecords)
                    .distinctBy { it.notiRecordId }
                    .sortedBy { it.time }

                val currentMap = _searchResults.value.toMutableMap()
                currentMap[notiKey] = combined
                _searchResults.value = currentMap
            }
            // No need to explicitly re-check here. The `LaunchedEffect` in the UI
            // reacting to `_searchResults.value` change will re-run `checkGapHasRecords`.
        }
    }

    // [NEW] Helper to access notification (Launch Intent)
    // This replicates the logic in NotiCard
    @RequiresApi(Build.VERSION_CODES.S)
    fun accessNotification(notiUnit: NotiUnit) {
        val contentIntent = org.muilab.notigpt.service.NotiListenerService.getContentIntent(context, notiUnit)
        if (contentIntent != null) {
            try {
                // Android 14+ background activity start options logic
                // (Reuse the logic you had in NotiCard or simplify here)
                val options = android.app.ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    options.pendingIntentBackgroundActivityStartMode =
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                contentIntent.send(context, 0, null, null, null, null, options.toBundle())
            } catch (e: Exception) {
                // Fallback launch
                val launchIntent = context.packageManager.getLaunchIntentForPackage(notiUnit.metadata.pkgName)
                if (launchIntent != null) {
                    launchIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                }
            }
        } else {
            // Simple fallback
            val launchIntent = context.packageManager.getLaunchIntentForPackage(notiUnit.metadata.pkgName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        }
        // Log action
        actOnNoti(notiUnit.notiKey, "access_click_search")
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    val availableAppCategories: StateFlow<List<Pair<String, Int>>> =
        groupedNotifications.map { list ->
            // Extract leaf nodes (NotiDisplayUnit) from the polymorphic list
            val leaves = list.flatMap { item ->
                when(item) {
                    is org.muilab.notigpt.model.notifications.NotiItem -> listOf(item.displayUnit)
                    is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children
                }
            }

            val categoryCounts = leaves
                .groupBy { it.notiUnit.appCategory }
                .mapValues { it.value.size }
                .toMutableMap()

            val totalCount = leaves.size
            val result = mutableListOf<Pair<String, Int>>()
            if (totalCount > 0) {
                result.add(APP_CATEGORY_ALL to totalCount)
            }

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
            if (action == "pin" && SharedPreferencesManager.trackPin) return true
            if (action == "archive" && SharedPreferencesManager.autoArchive) return true
            if (action == "dismiss_swipe" && SharedPreferencesManager.autoDelete) return true
            return false
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (checkIfTrackAction()) enqueueNotificationAction(context, notiKey, action)
            notiRepository.actOnNoti(notiKey, action)
            if (action.contains("dismiss")) postOngoingNotification(context)
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


    private val _seenNotiKeys = ConcurrentHashMap.newKeySet<String>()
    fun markNotificationAsRead(notiKey: String) {
        val currentItems = _groupedNotifications.value
        val foundUnit = currentItems.asSequence().flatMap { item ->
            when(item) {
                is org.muilab.notigpt.model.notifications.NotiItem -> sequenceOf(item.displayUnit)
                is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children.asSequence()
            }
        }.firstOrNull { it.notiKey == notiKey }

        if (foundUnit != null) {
            if (foundUnit.notiUnit.isRead) return
            _seenNotiKeys.add(notiKey)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun persistReadStatus() {
        if (_seenNotiKeys.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ViewModelReadState", "Persisting seenNotis: ${_seenNotiKeys.size}")
            notiRepository.updateSeenNotifications(_seenNotiKeys.toSet())
            _seenNotiKeys.clear()
        }
    }

    fun removeExpiredRecords() {
        viewModelScope.launch {
            notiRepository.removeExpiredNotiRecords()
        }
    }

    private val _unreadCountsByCategory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByCategory = _unreadCountsByCategory.asStateFlow()

    private fun updateUnreadCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val counts = mutableMapOf<String, Int>()
            val cats = listOf(NOTI_CATEGORY_GENERAL, NOTI_CATEGORY_MAKETASK, NOTI_CATEGORY_SAVE, NOTI_CATEGORY_ARCHIVE)

            cats.forEach { cat ->
                counts[cat] = notiRepository.getVisibleNotReadNotificationCountByCategory(cat)
                counts["$cat-Total"] = notiRepository.getVisibleNotiCountByCategory(cat)
            }

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

    private val fullRecordsCache = ConcurrentHashMap<String, MutableStateFlow<List<NotiRecord>>>()
    private val fullRecordsJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun getFullRecordsFlow(notiKey: String): StateFlow<List<NotiRecord>> {
        return fullRecordsCache.getOrPut(notiKey) { MutableStateFlow(emptyList()) }
    }

    fun loadFullRecordsForKey(notiKey: String) {
        if (fullRecordsJobs.containsKey(notiKey)) return
        val stateFlow = fullRecordsCache.getOrPut(notiKey) { MutableStateFlow(emptyList()) }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                notiRepository.visibleRecordsFlowForKey(notiKey)
                    .collect { recs ->
                        stateFlow.value = recs.sortedBy { it.time }
                    }
            } catch (e: Exception) {
                Log.e("DrawerViewModel", "Error subscribing to full records for $notiKey", e)
            } finally {
                fullRecordsJobs.remove(notiKey)
            }
        }
        fullRecordsJobs[notiKey] = job
    }

    fun clearFullRecordsForKey(notiKey: String) {
        fullRecordsJobs.remove(notiKey)?.cancel()
        fullRecordsCache.remove(notiKey)
    }

    // Merge Actions
    fun onMerge(dragId: String, targetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.merge(dragId, targetId)
        }
    }

    fun onUngroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.ungroup(groupId)
        }
    }

    fun toggleGroupExpansion(groupId: String, currentExpanded: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.updateGroupExpansion(groupId, !currentExpanded)
        }
    }

    fun renameGroup(groupId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.updateGroupTitle(groupId, newTitle)
        }
    }

    fun actOnGroup(groupId: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.actOnGroup(groupId, action)
        }
    }

    fun removeFromGroup(notiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.removeFromGroup(notiKey)
        }
    }
}