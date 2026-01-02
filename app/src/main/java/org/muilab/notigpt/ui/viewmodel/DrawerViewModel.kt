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
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerFiltersState
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerGroupingActions
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerReadStateController
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerSearchController
import org.muilab.notigpt.platform.NotificationLauncher
import org.muilab.notigpt.ui.viewmodel.drawer.FullRecordsController
import org.muilab.notigpt.ui.viewmodel.drawer.DrawerUnreadCounts

class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository,
    private val clipboard: ClipboardController,
    private val notifier: UserNotifier,
    private val logExporter: NotiLogExporter,
) : AndroidViewModel(application) {

    private val filters = DrawerFiltersState()

    val category: StateFlow<String> = filters.category
    val appCategory: StateFlow<String> = filters.appCategory
    val isTargetLoading: StateFlow<Boolean> = filters.isTargetLoading
    val isSortingMode: StateFlow<Boolean> = filters.isSortingMode

    fun toggleSortingMode() = filters.toggleSortingMode()

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateCategory(newCategory: String) {
        filters.startTargetLoading()
        filters.setCategory(newCategory)
        if (isSortingMode.value) toggleSortingMode()
        persistReadStatus()
        unreadCounts.refresh()
        updateAppCategory(APP_CATEGORY_ALL)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {
        filters.startTargetLoading()
        if (isSortingMode.value) toggleSortingMode()
        persistReadStatus()
        unreadCounts.refresh()
        filters.setAppCategory(newAppCategory)
    }

    fun clearTargetLoading() = filters.clearTargetLoading()

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
    private val groupingActions = DrawerGroupingActions(
        scope = viewModelScope,
        notiRepository = notiRepository,
        actionsController = actionsController,
    )
    private val fullRecordsController = FullRecordsController(
        scope = viewModelScope,
        notiRepository = notiRepository,
    )

    private val unreadCounts = DrawerUnreadCounts(
        scope = viewModelScope,
        notiRepository = notiRepository,
    )
    val unreadCountsByCategory: StateFlow<Map<String, Int>> = unreadCounts.unreadCountsByCategory

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
            unreadCounts.refresh()
            Log.d("DrawerViewModel", "groupedNotifications emitted: size=${notifications.size}, isTargetLoading=${isTargetLoading.value}")

            if (filters.shouldClearTargetLoading()) {
                filters.clearTargetLoading()
            } else if (isTargetLoading.value) {
                // Check if items match current filters to decide if we stop loading
                // Approximate check
                if (notifications.isNotEmpty()) {
                    filters.clearTargetLoading()
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
                        filters.startTargetLoading()
                        try {
                            searchController.performSearch(query)
                        } finally {
                            filters.clearTargetLoading()
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
                filters.startTargetLoading()
                try {
                    searchController.performSearch(_queryString.value)
                } finally {
                    filters.clearTargetLoading()
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
        unreadCounts.refresh()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun markAllNotisRead() {
        persistReadStatus()
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.markAllNotisRead(category.value)
        }
        unreadCounts.refresh()
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

    // Merge Actions (delegated)
    fun onMerge(dragId: String, targetId: String) = groupingActions.onMerge(dragId, targetId)

    fun onUngroup(groupId: String) = groupingActions.onUngroup(groupId)

    fun toggleGroupExpansion(groupId: String, currentExpanded: Boolean) =
        groupingActions.toggleGroupExpansion(groupId, currentExpanded)

    fun renameGroup(groupId: String, newTitle: String) = groupingActions.renameGroup(groupId, newTitle)

    fun actOnGroup(groupId: String, action: String) = groupingActions.actOnGroup(groupId, action)

    fun actOnGroup(groupId: String, action: NotiActionType) = groupingActions.actOnGroup(groupId, action)

    fun removeFromGroup(notiKey: String) = groupingActions.removeFromGroup(notiKey)

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

    /**
     * Full notification record streams (expanded card content).
     *
     * Kept as a small compatibility surface for UI composables.
     */
    fun getFullRecordsFlow(notiKey: String): StateFlow<List<NotiRecord>> =
        fullRecordsController.getFlow(notiKey)

    fun loadFullRecordsForKey(notiKey: String) {
        fullRecordsController.loadForKey(notiKey)
    }

    fun clearFullRecordsForKey(notiKey: String) {
        fullRecordsController.clearForKey(notiKey)
    }
}
