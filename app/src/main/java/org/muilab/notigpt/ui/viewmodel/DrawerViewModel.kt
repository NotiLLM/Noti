@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

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
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.postOngoingNotification
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

    val isTargetLoading: StateFlow<Boolean> = filters.isTargetLoading
    val isSortingMode: StateFlow<Boolean> = filters.isSortingMode

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateCategory(newCategory: String) {
        filters.startTargetLoading()
        filters.setCategory(newCategory)
        if (isSortingMode.value) toggleSortingMode()
        // Persist read + commit manual sort session (if any)
        persistReadStatus()
        unreadCounts.refresh()
        updateAppCategory(APP_CATEGORY_ALL)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {
        filters.startTargetLoading()
        if (isSortingMode.value) toggleSortingMode()
        // Persist read + commit manual sort session (if any)
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

    /** Total unread notifications in the active drawer (not dismissed). */
    val unreadActiveCount: StateFlow<Int> = groupedNotifications
        .map { items ->
            items.asSequence().flatMap { item ->
                when (item) {
                    is org.muilab.notigpt.model.notifications.NotiItem -> sequenceOf(item.displayUnit)
                    is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children.asSequence()
                }
            }.count { du -> !du.notiUnit.isDismissed && !du.notiUnit.isRead }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Total active (not dismissed) notifications currently in the drawer. */
    val activeNotDismissedCount: StateFlow<Int> = groupedNotifications
        .map { items ->
            items.asSequence().flatMap { item ->
                when (item) {
                    is org.muilab.notigpt.model.notifications.NotiItem -> sequenceOf(item.displayUnit)
                    is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children.asSequence()
                }
            }.count { du -> !du.notiUnit.isDismissed }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Search state (delegated)
    val searchResults: StateFlow<Map<String, List<NotiRecord>>> = searchController.searchResults
    val searchUnits: StateFlow<Map<String, NotiUnit>> = searchController.searchUnits

    init {
        // Main subscription to the grouped data flow
        notiRepository.getGroupedNotifications()
            .debounce(60)
            .onEach { newList ->
                val prev = _groupedNotifications.value
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
            notiRepository.deleteAllNotis()
            postOngoingNotification(context)
        }
        unreadCounts.refresh()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun markAllNotisRead() {
        persistReadStatus()
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.markAllNotisRead()
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

    /**
     * Export all notification content and user action data, splitting into multiple files if needed.
     * Includes both notification content and user interaction logs.
     */
    fun exportAllData(includeContext: Boolean, includeDismissed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val baseFilename = "notigpt_all_data_$ts"

                // Fetch all notification logs
                val notiLogs = notiRepository.exportLog(includeContext, includeDismissed)

                // Fetch all action data (not currently used but fetched for completeness)
                @Suppress("UNUSED_VARIABLE")
                val allActions = notiRepository.getAllActions()

                // Export with file splitting if needed
                val dataExportManager = org.muilab.notigpt.platform.DataExportManager(context)
                val createdFiles = dataExportManager.exportNotificationData(
                    notiLogs,
                    baseFilename
                )

                withContext(Dispatchers.Main) {
                    val message = if (createdFiles.isEmpty()) {
                        "Export failed"
                    } else if (createdFiles.size == 1) {
                        "Data exported to: ${createdFiles[0]}"
                    } else {
                        "Data exported to ${createdFiles.size} files"
                    }
                    notifier.showShort(message)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notifier.showShort("Export error: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun persistReadStatus() {
        viewModelScope.launch {
            readStateController.persistSeen()
            // Also commit manual sort on pause-style persistence.
            commitManualSortSessionIfNeeded()
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

    // --- History helpers (Gmail-style "All Notifications") ---
    suspend fun getLatestRecordsForHistory(limit: Int): List<NotiRecord> {
        return withContext(Dispatchers.IO) { notiRepository.getLatestRecords(limit.coerceAtLeast(1)) }
    }

    suspend fun getRecordsBeforeForHistory(pivotTime: Long, limit: Int): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRepository.getRecordsBefore(pivotTime, limit.coerceAtLeast(1))
        }
    }

    suspend fun getRecordsAfterForHistory(pivotTime: Long, limit: Int): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRepository.getRecordsAfter(pivotTime, limit.coerceAtLeast(1))
        }
    }

    suspend fun getNotiUnitForHistory(notiKey: String): NotiUnit? {
        return withContext(Dispatchers.IO) { notiRepository.getNotiUnit(notiKey) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun accessNotificationByKey(notiKey: String) {
        viewModelScope.launch {
            val unit = withContext(Dispatchers.IO) { notiRepository.getNotiUnit(notiKey) }
            if (unit != null) {
                accessNotification(unit)
            }
        }
    }

    // --- Manual sort (loose items only) ---
    private val manualSortKeys = MutableStateFlow<List<String>>(emptyList())

    // Snapshot of keys that were already manual when entering sort mode.
    private var manualKeysAtSessionStart: Set<String> = emptySet()
    // Keys the user moved during the current session.
    private val manuallyTouchedKeysInSession = LinkedHashSet<String>()

    /** Called by UI when entering sorting mode; captures current loose order and existing manual keys. */
    fun startManualSortSession() {
        val loose = _groupedNotifications.value
            .asSequence()
            .filterIsInstance<org.muilab.notigpt.model.notifications.NotiItem>()
            .map { it.displayUnit.notiKey to it.displayUnit.notiUnit.sortPosition }
            .toList()

        manualSortKeys.value = loose.map { it.first }

        // Snapshot all keys that were already manual (sortPosition != -1) at session start.
        manualKeysAtSessionStart = loose.asSequence().filter { it.second != -1 }.map { it.first }.toSet()
        manuallyTouchedKeysInSession.clear()
    }

    /** Commit session manual sort positions using the final in-memory order. Safe to call multiple times. */
    fun commitManualSortSessionIfNeeded() {
        val finalOrder = manualSortKeys.value
        if (finalOrder.isEmpty()) return

        // All keys we should treat as manual for this commit.
        val commitKeys = LinkedHashSet<String>()
        commitKeys.addAll(manualKeysAtSessionStart)
        commitKeys.addAll(manuallyTouchedKeysInSession)
        if (commitKeys.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.commitManualKeysFromFinalOrder(commitKeys, finalOrder)
        }
    }

    /**
     * Move a loose item within the in-memory order list.
     * Persistence:
     * - We do NOT write to DB on every move.
     * - We record the moved key as "touched".
     * - Final sortPosition for touched + initially-manual keys is persisted on exit/pause.
     */
    fun moveLooseItem(key: String, fromIndex: Int, toIndex: Int) {
        val current = manualSortKeys.value
        if (current.isEmpty()) return

        val from = fromIndex.coerceIn(0, current.lastIndex)
        val to = toIndex.coerceIn(0, current.lastIndex)
        if (from == to) return

        if (current.getOrNull(from) != key) {
            val actualFrom = current.indexOf(key)
            if (actualFrom == -1) return
            return moveLooseItem(key, actualFrom, to)
        }

        manualSortKeys.value = current.toMutableList().apply {
            add(to, removeAt(from))
        }

        manuallyTouchedKeysInSession.add(key)
    }

    fun getManualSortKeys(): StateFlow<List<String>> = manualSortKeys.asStateFlow()

    fun toggleSortingMode() {
        val wasSorting = isSortingMode.value
        filters.toggleSortingMode()
        // If exiting sorting mode, commit.
        if (wasSorting && !isSortingMode.value) {
            commitManualSortSessionIfNeeded()
        }
    }
}
