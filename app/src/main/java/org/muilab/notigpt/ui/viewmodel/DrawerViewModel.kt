@file:OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.muilab.notigpt.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.postOngoingNotification
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.filter
import org.muilab.notigpt.platform.ClipboardController
import org.muilab.notigpt.platform.NotiLogExporter
import org.muilab.notigpt.platform.UserNotifier
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerActionsController
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerReadStateController
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerSearchController
import org.muilab.notigpt.platform.NotificationLauncher

class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository,
    private val clipboard: ClipboardController,
    private val notifier: UserNotifier,
    private val logExporter: NotiLogExporter,
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

    @SuppressLint("StaticFieldLeak")
    val context: Context = getApplication<Application>().applicationContext

    private val searchController = DrawerSearchController(notiRepository)
    private val readStateController = DrawerReadStateController(notiRepository)
    private val actionsController = DrawerActionsController(context, notiRepository)

    // Search state (delegated)
    val includeHistory: StateFlow<Boolean> = searchController.includeHistory
    val searchResults: StateFlow<Map<String, List<NotiRecord>>> = searchController.searchResults
    val searchUnits: StateFlow<Map<String, NotiUnit>> = searchController.searchUnits

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
                    if (query.isBlank()) {
                        // reset loading state for empty query
                        // controller handles clearing results
                        searchController.performSearch("")
                    } else {
                        _isTargetLoading.value = true
                        try {
                            searchController.performSearch(query)
                        } finally {
                            _isTargetLoading.value = false
                        }
                    }
                }
        }

        removeExpiredRecords()
    }

    fun toggleIncludeHistory(enabled: Boolean) {
        searchController.setIncludeHistory(enabled)
        if (_queryString.value.isNotBlank()) {
            viewModelScope.launch {
                _isTargetLoading.value = true
                try {
                    searchController.performSearch(_queryString.value)
                } finally {
                    _isTargetLoading.value = false
                }
            }
        }
    }

    // [NEW] Helper to access notification (Launch Intent)
    // This replicates the logic in NotiCard
    @RequiresApi(Build.VERSION_CODES.S)
    fun accessNotification(notiUnit: NotiUnit) {
        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
        NotificationLauncher.launchPendingIntentOrFallback(
            context = context,
            pendingIntent = contentIntent,
            packageName = notiUnit.metadata.pkgName,
            logTag = "DrawerViewModel",
        )

        // Log action
        actOnNoti(notiUnit.notiKey, NotiActionType.AccessClickSearch)
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun actOnNoti(notiKey: String, action: String) {
        val typed = NotiActionType.fromWireValue(action)
        if (typed != null) {
            actOnNoti(notiKey, typed)
        } else {
            viewModelScope.launch {
                actionsController.actOnNotiLegacy(notiKey, action)
                if (action.contains("dismiss")) postOngoingNotification(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun actOnNoti(notiKey: String, action: NotiActionType) {
        viewModelScope.launch {
            actionsController.actOnNoti(notiKey, action)
            if (action.wireValue.contains("dismiss")) postOngoingNotification(context)
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

            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val filename = "notigpt_$ts.txt"

            // Persist file (best-effort)
            logExporter.exportToDocuments(filename = filename, content = notiLogsStr)

            // Copy
            clipboard.copyPlainText(label = "NotiGPT logs", text = notiLogsStr)

            withContext(Dispatchers.Main) {
                notifier.showShort("Copied to clipboard")
            }
        }
    }

    fun syncAppCategory() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.syncAppCategories(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun persistReadStatus() {
        viewModelScope.launch {
            readStateController.persistSeen()
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
        val typed = NotiActionType.fromWireValue(action)
        if (typed != null) {
            actOnGroup(groupId, typed)
            return
        }
        viewModelScope.launch {
            actionsController.actOnGroupLegacy(groupId, action)
        }
    }

    fun actOnGroup(groupId: String, action: NotiActionType) {
        viewModelScope.launch {
            actionsController.actOnGroup(groupId, action)
        }
    }

    fun removeFromGroup(notiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.removeFromGroup(notiKey)
        }
    }

    /**
     * Called by list items when a card is fully visible.
     * We batch persistence via [persistReadStatus].
     */
    fun markNotificationAsRead(notiKey: String) {
        val currentItems = _groupedNotifications.value
        val foundUnit = currentItems.asSequence().flatMap { item ->
            when (item) {
                is org.muilab.notigpt.model.notifications.NotiItem -> sequenceOf(item.displayUnit)
                is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children.asSequence()
            }
        }.firstOrNull { it.notiKey == notiKey }

        if (foundUnit != null) {
            readStateController.markSeenIfUnread(notiKey, foundUnit.notiUnit.isRead)
        }
    }

    fun loadSearchContext(notiKey: String, isOlder: Boolean) {
        viewModelScope.launch { searchController.loadSearchContext(notiKey, isOlder) }
    }

    suspend fun checkGapHasRecords(notiKey: String, startTime: Long, endTime: Long): Boolean {
        return searchController.checkGapHasRecords(notiKey, startTime, endTime)
    }

    fun loadGapRecords(notiKey: String, startTime: Long, endTime: Long, fromStart: Boolean) {
        viewModelScope.launch { searchController.loadGapRecords(notiKey, startTime, endTime, fromStart) }
    }
}
