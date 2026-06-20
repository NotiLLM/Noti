@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package org.muilab.notigpt.ui.notification.viewmodel

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.postOngoingNotification
import org.muilab.notigpt.ui.common.clipboard.ClipboardController
import org.muilab.notigpt.data.export.NotiLogExporter
import org.muilab.notigpt.ui.common.feedback.UserToaster
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.notification.controller.DrawerActionsController
import org.muilab.notigpt.ui.notification.controller.DrawerFiltersState
import org.muilab.notigpt.ui.notification.controller.DrawerReadStateController
import org.muilab.notigpt.ui.notification.controller.DrawerSearchController
import org.muilab.notigpt.ui.notification.action.NotificationLauncher
import org.muilab.notigpt.ui.notification.controller.FullRecordsController
import org.muilab.notigpt.ui.notification.controller.DrawerUnreadCounts

/**
 * ViewModel for notification drawer state, filters, actions, and record context loading.
 *
 * This class is the UI-facing coordinator over smaller drawer controllers. If a responsibility grows large,
 * prefer extracting another controller/repository method over adding more direct database logic here.
 */
class DrawerViewModel(
    application: Application,
    private val notiRepository: NotiRepository,
    private val clipboard: ClipboardController,
    private val notifier: UserToaster,
    private val logExporter: NotiLogExporter,
) : AndroidViewModel(application) {

    private val filters = DrawerFiltersState()

    /**
     * Emits notiKey whenever the user triggers a manual extract_reminder action.
     * Observed in AppScaffold to trigger preference learning (Flow 3).
     */
    private val _manualExtractEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val manualExtractEvent: SharedFlow<String> = _manualExtractEvent

    val isTargetLoading: StateFlow<Boolean> = filters.isTargetLoading
    val isSortingMode: StateFlow<Boolean> = filters.isSortingMode

    /** True while the n8n task-extraction WorkManager job is actively running. */
    val isExtracting: StateFlow<Boolean> =
        androidx.work.WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkFlow("n8n_task_extraction")
            .map { infos -> infos.any { it.state == androidx.work.WorkInfo.State.RUNNING } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Updates the active drawer category filter and resets dependent app-filter state when needed.
     *
     * Keep filter coordination here so the screen does not need to understand category/app-category coupling.
     */
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
    private val _activeNotiUnits = MutableStateFlow<List<NotiDisplayUnit>>(emptyList())
    val activeNotiUnits: StateFlow<List<NotiDisplayUnit>> = _activeNotiUnits.asStateFlow()

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
    val unreadActiveCount: StateFlow<Int> = activeNotiUnits
        .map { units ->
            units.count { du -> !du.notiUnit.isDismissed && !du.notiUnit.isRead }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Total active (not dismissed) notifications currently in the drawer. */
    val activeNotDismissedCount: StateFlow<Int> = activeNotiUnits
        .map { units ->
            units.count { du -> !du.notiUnit.isDismissed }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _newNotificationRecords = MutableStateFlow<Map<String, List<NotiRecord>>>(emptyMap())
    val newNotificationRecords: StateFlow<Map<String, List<NotiRecord>>> = _newNotificationRecords.asStateFlow()

    val newNotificationUnits: StateFlow<List<NotiDisplayUnit>> = combine(activeNotiUnits, _newNotificationRecords) { units, recordsByKey ->
        units.mapNotNull { unit ->
            val newRecords = recordsByKey[unit.notiKey].orEmpty()
            if (newRecords.isEmpty()) null else NotiDisplayUnit(unit.notiUnit, newRecords)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun refreshNewNotificationRecords() {
        val records = withContext(Dispatchers.IO) { notiRepository.getNewRecords() }
        _newNotificationRecords.value = records.groupBy { it.notiKey }
    }

    // Search state (delegated)
    val searchResults: StateFlow<Map<String, List<NotiRecord>>> = searchController.searchResults
    val searchUnits: StateFlow<Map<String, NotiUnit>> = searchController.searchUnits

    init {
        // Main subscription to active notification units
        notiRepository.getActiveNotiUnits()
            .debounce(60)
            .onEach { newList ->
                val prev = _activeNotiUnits.value
                if (prev.size != newList.size || prev != newList) {
                    _activeNotiUnits.value = newList
                }
                refreshNewNotificationRecords()
            }.launchIn(viewModelScope)

        // Loading state management
        activeNotiUnits.onEach { notifications ->
            unreadCounts.refresh()
            Log.d("DrawerViewModel", "activeNotiUnits emitted: size=${notifications.size}, isTargetLoading=${isTargetLoading.value}")

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

    @RequiresApi(Build.VERSION_CODES.S)
    fun accessNotification(notiUnit: NotiUnit) {
        launchNotificationContent(notiUnit, NotiActionType.AccessClickSearch)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun accessAndDismissNotification(notiUnit: NotiUnit) {
        launchNotificationContent(notiUnit, NotiActionType.AccessClickDismiss)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun launchNotificationContent(notiUnit: NotiUnit, action: NotiActionType) {
        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
        NotificationLauncher.launchPendingIntentOrFallback(
            context = context,
            pendingIntent = contentIntent,
            packageName = notiUnit.metadata.pkgName,
            logTag = "DrawerViewModel",
        )
        if (!notiUnit.isPinned)
            NotiListenerService.removeIntents(notiUnit.notiKey)
        actOnNoti(notiUnit.notiKey, action)
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
                // Emit event for preference learning (Flow 3: Manual Extract)
                if (action == "extract_reminder" || action.startsWith("extract_reminder_with_records")) {
                    _manualExtractEvent.tryEmit(notiKey)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun actOnNoti(notiKey: String, action: NotiActionType) {
        applyOptimisticPinState(notiKey, action)
        viewModelScope.launch {
            actionsController.actOnNoti(notiKey, action)
            if (action.wireValue.contains("dismiss")) postOngoingNotification(context)
            // Emit event for preference learning (Flow 3: Manual Extract)
            if (action is NotiActionType.ExtractReminder) {
                _manualExtractEvent.tryEmit(notiKey)
            }
        }
    }

    private fun applyOptimisticPinState(notiKey: String, action: NotiActionType) {
        val pinned = when (action) {
            NotiActionType.Pin -> true
            NotiActionType.Unpin -> false
            else -> return
        }
        _activeNotiUnits.value = _activeNotiUnits.value.map { displayUnit ->
            if (displayUnit.notiKey != notiKey) {
                displayUnit
            } else {
                val updatedUnit = displayUnit.notiUnit.copy(
                    displayState = displayUnit.notiUnit.displayState.copy(isPinned = pinned),
                )
                NotiDisplayUnit(updatedUnit, displayUnit.notiRecords)
            }
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
            try {
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val baseFilename = "notigpt_$ts"

                // Pass 1: write to file(s) using DataExportManager — at most one 5 MB chunk
                // is held in memory at a time, so OOM is not possible here.
                val dataExportManager = org.muilab.notigpt.data.export.DataExportManager(context)
                val createdFiles = dataExportManager.exportNotificationData(
                    items = notiRepository.exportLog(includeContext, includeDismissed),
                    filename = baseFilename,
                )

                // Pass 2: attempt to collect up to 500 KB for the clipboard.
                // We consume the sequence a second time (fresh DB queries) but bail out
                // early the moment we exceed the IPC binder limit, so the full dataset is
                // never materialised in RAM for this path either.
                val clipboardLimitBytes = 500 * 1024
                val clipboardBuf = StringBuilder()
                var tooLarge = false
                clipboardBuf.append("[\n")
                var first = true
                for (item in notiRepository.exportLog(includeContext, includeDismissed)) {
                    val itemStr = item.toString(2)
                    if (clipboardBuf.length + itemStr.length > clipboardLimitBytes) {
                        tooLarge = true
                        break
                    }
                    if (!first) clipboardBuf.append(",\n")
                    clipboardBuf.append(itemStr)
                    first = false
                }
                if (!tooLarge) clipboardBuf.append("\n]")

                withContext(Dispatchers.Main) {
                    if (!tooLarge) {
                        clipboard.copyPlainText(label = "NotiGPT logs", text = clipboardBuf.toString())
                        val saved = createdFiles.firstOrNull()
                        if (saved != null) notifier.showShort("Copied to clipboard & saved to: $saved")
                        else notifier.showShort("Copied to clipboard")
                    } else {
                        val fileMsg = if (createdFiles.size == 1) "saved to: ${createdFiles[0]}"
                                      else "saved to ${createdFiles.size} files"
                        notifier.showShort("Data too large for clipboard — $fileMsg")
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    notifier.showShort("Export error: ${e.message}")
                }
            }
        }
    }

    /**
     * Export all notification content and user action data, splitting into multiple files if needed.
     * Consumes a lazy sequence so at most one 5 MB chunk is held in memory at once.
     */
    fun exportAllData(includeContext: Boolean, includeDismissed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val baseFilename = "notigpt_all_data_$ts"

                val dataExportManager = org.muilab.notigpt.data.export.DataExportManager(context)
                val createdFiles = dataExportManager.exportNotificationData(
                    items = notiRepository.exportLog(includeContext, includeDismissed),
                    filename = baseFilename,
                )

                withContext(Dispatchers.Main) {
                    val message = if (createdFiles.isEmpty()) {
                        "No data to export"
                    } else if (createdFiles.size == 1) {
                        "Data exported to: ${createdFiles[0]}"
                    } else {
                        "Data exported to ${createdFiles.size} files"
                    }
                    notifier.showShort(message)
                }
            } catch (e: Throwable) {
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

    fun archiveNewNotificationCard(notiKey: String) {
        viewModelScope.launch {
            val activeUnit = _activeNotiUnits.value.firstOrNull { it.notiKey == notiKey }?.notiUnit
            if (activeUnit?.isPinned == true) return@launch

            notiRepository.removeNotiUnit(notiKey)
            refreshNewNotificationRecords()
        }
    }

    fun removeExpiredRecords() {
        viewModelScope.launch {
            notiRepository.removeExpiredNotiRecords()
        }
    }


    /**
     * Called by list items when a card is fully visible.
     * We batch persistence via [persistReadStatus].
     */
    fun markNotificationAsRead(notiKey: String) {
        val foundUnit = _activeNotiUnits.value.firstOrNull { it.notiKey == notiKey }

        if (foundUnit != null) {
            readStateController.markSeenIfUnread(notiKey, foundUnit.notiUnit.isRead)
        }
    }

    fun loadSearchContext(notiKey: String, isOlder: Boolean) {
        viewModelScope.launch { searchController.loadSearchContext(notiKey, isOlder) }
    }

    suspend fun checkGapHasRecords(notiKey: String, startAtMs: Long, endAtMs: Long): Boolean {
        return searchController.checkGapHasRecords(notiKey, startAtMs, endAtMs)
    }

    fun loadGapRecords(notiKey: String, startAtMs: Long, endAtMs: Long, fromStart: Boolean) {
        viewModelScope.launch { searchController.loadGapRecords(notiKey, startAtMs, endAtMs, fromStart) }
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

    suspend fun searchRecordsForHistory(query: String): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRepository.searchNotifications(query)
                .values
                .flatten()
                .sortedByDescending { it.time }
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

    // --- Manual sort (active notifications) ---
    private val manualSortKeys = MutableStateFlow<List<String>>(emptyList())

    // Snapshot of keys that were already manual when entering sort mode.
    private var manualKeysAtSessionStart: Set<String> = emptySet()
    // Keys the user moved during the current session.
    private val manuallyTouchedKeysInSession = LinkedHashSet<String>()

    /** Called by UI when entering sorting mode; captures current active notification order and existing manual keys. */
    fun startManualSortSession() {
        val active = _activeNotiUnits.value
            .map { it.notiKey to it.notiUnit.sortPosition }

        manualSortKeys.value = active.map { it.first }

        // Snapshot all keys that were already manual (sortPosition != -1) at session start.
        manualKeysAtSessionStart = active.asSequence().filter { it.second != -1 }.map { it.first }.toSet()
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
     * Move an active notification within the in-memory order list.
     * Persistence:
     * - We do NOT write to DB on every move.
     * - We record the moved key as "touched".
     * - Final sortPosition for touched + initially-manual keys is persisted on exit/pause.
     */
    fun moveActiveNotiUnit(key: String, fromIndex: Int, toIndex: Int) {
        val current = manualSortKeys.value
        if (current.isEmpty()) return

        val from = fromIndex.coerceIn(0, current.lastIndex)
        val to = toIndex.coerceIn(0, current.lastIndex)
        if (from == to) return

        if (current.getOrNull(from) != key) {
            val actualFrom = current.indexOf(key)
            if (actualFrom == -1) return
            return moveActiveNotiUnit(key, actualFrom, to)
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
