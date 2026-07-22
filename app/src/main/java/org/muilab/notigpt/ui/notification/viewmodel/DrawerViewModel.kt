@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package org.muilab.notigpt.ui.notification.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
import org.muilab.notigpt.data.repository.notification.NotiClassificationRepository
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.domain.notification.withActiveRecords
import org.muilab.notigpt.ui.home.viewmodel.HomeViewModel
import org.muilab.notigpt.util.postOngoingNotification
import org.muilab.notigpt.ui.common.feedback.UserToaster
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.notification.controller.DrawerActionsController
import org.muilab.notigpt.ui.notification.controller.DrawerFiltersState
import org.muilab.notigpt.ui.notification.controller.DrawerSearchController
import org.muilab.notigpt.ui.notification.action.NotificationLauncher
import org.muilab.notigpt.ui.notification.controller.FullRecordsController

/**
 * ViewModel for notification drawer state, filters, actions, and record context loading.
 *
 * This class is the UI-facing coordinator over smaller drawer controllers. If a responsibility grows large,
 * prefer extracting another controller/repository method over adding more direct database logic here.
 */
@HiltViewModel
class DrawerViewModel @Inject constructor(
    application: Application,
    private val notiRepository: NotiRepository,
    private val notifier: UserToaster,
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

    /** True while any per-notiKey extraction pipeline job is actively running. */
    val isExtracting: StateFlow<Boolean> =
        androidx.work.WorkManager.getInstance(application)
            .getWorkInfosByTagFlow(org.muilab.notigpt.data.remote.n8n.EXTRACTION_WORK_TAG)
            .map { infos -> infos.any { it.state == androidx.work.WorkInfo.State.RUNNING } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Persisted per-thread LLM state, keyed by notiKey, for classifying the active drawer into
     * Communication/Content on the home screen and the category pages. Rows persist across dismissals,
     * so a thread keeps its category until the next scan reclassifies it.
     */
    val llmStatesByKey: StateFlow<Map<String, org.muilab.notigpt.model.features.NotiLlmState>> =
        org.muilab.notigpt.data.local.room.AppDatabase.getInstance(application.applicationContext)
            .notiLlmStateDao().observeAll()
            .map { list -> list.associateBy { it.notiKey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Per-notiKey counts of linked active saved items (task to keep), for the card cross-link badge.
     * Empty pair means no linked items; the card omits the badge then.
     */
    val linkedItemCountsByKey: StateFlow<Map<String, Pair<Int, Int>>> =
        org.muilab.notigpt.data.local.room.AppDatabase.getInstance(application.applicationContext)
            .notiSavedItemLinkDao().observeLinkedTypeCounts()
            .map { rows -> rows.associate { it.notiKey to (it.todoCount to it.keepCount) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The category page's "past 24 hrs" time-window toggle, lifted here so the top-bar Clear All action
     * (which lives outside the screen) can scope itself to exactly the visible list.
     */
    private val _notiRecentOnly = MutableStateFlow(true)
    val notiRecentOnly: StateFlow<Boolean> = _notiRecentOnly.asStateFlow()
    fun setNotiRecentOnly(value: Boolean) { _notiRecentOnly.value = value }

    fun clearTargetLoading() = filters.clearTargetLoading()

    private val _queryString = MutableStateFlow("")
    val queryString: StateFlow<String> = _queryString

    fun updateQueryString(newQueryString: String) {
        _queryString.value = newQueryString
    }

    // Main List State
    private val _activeNotiUnits = MutableStateFlow<List<NotiDisplayUnit>>(emptyList())
    val activeNotiUnits: StateFlow<List<NotiDisplayUnit>> = _activeNotiUnits.asStateFlow()

    /** False only until Room delivers its first active-drawer result, including a valid empty result. */
    private val _hasLoadedInitialNotifications = MutableStateFlow(false)
    val hasLoadedInitialNotifications: StateFlow<Boolean> =
        _hasLoadedInitialNotifications.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    val context: Context = getApplication<Application>().applicationContext

    private val searchController = DrawerSearchController(notiRepository)
    private val actionsController = DrawerActionsController(notiRepository)
    private val fullRecordsController = FullRecordsController(
        scope = viewModelScope,
        notiRepository = notiRepository,
    )

    /** Total active (not dismissed) notifications currently in the drawer. */
    val activeNotDismissedCount: StateFlow<Int> = activeNotiUnits
        .map { units ->
            units.count { du -> !du.notiUnit.isDismissed }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Active, unpinned notifications that "Clear all" would remove; drives the top-bar clear button. */
    val clearableActiveCount: StateFlow<Int> = activeNotiUnits
        .map { units -> org.muilab.notigpt.domain.notification.countClearableActiveNotifications(units) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The active Room stream already joins each drawer thread with its active records. Deriving this
     * view avoids re-querying the entire active record table after every Room invalidation while
     * preserving live updates for both existing and newly-created notification keys.
     */
    val newNotificationUnits: StateFlow<List<NotiDisplayUnit>> = activeNotiUnits
        .map { it.withActiveRecords() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True if a visible thread should be cleared by "Clear all": right category, time bucket, not pinned. */
    private fun isVisibleClearable(du: NotiDisplayUnit, category: String, recentOnly: Boolean, cutoff: Long, llm: Map<String, org.muilab.notigpt.model.features.NotiLlmState>): Boolean =
        !du.notiUnit.isPinned &&
            NotiClassificationRepository.categoryOf(du.notiUnit, llm[du.notiKey]) == category &&
            (du.notiRecords.maxOfOrNull { it.time }?.let { (it >= cutoff) == recentOnly } == true)

    /**
     * Count of threads "Clear all" would remove for each category, honoring the current time bucket and
     * excluding pinned. Drives the top-bar button's enabled state so it disables when the visible list is empty.
     */
    val clearableVisibleCountByCategory: StateFlow<Map<String, Int>> =
        combine(newNotificationUnits, llmStatesByKey, _notiRecentOnly) { units, llm, recentOnly ->
            val cutoff = System.currentTimeMillis() - HomeViewModel.newNotiWindowMs()
            units.filter { du ->
                !du.notiUnit.isPinned &&
                    (du.notiRecords.maxOfOrNull { it.time }?.let { (it >= cutoff) == recentOnly } == true)
            }
                .groupingBy { du -> NotiClassificationRepository.categoryOf(du.notiUnit, llm[du.notiKey]) }
                .eachCount()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                _hasLoadedInitialNotifications.value = true
            }.launchIn(viewModelScope)

        // Loading state management
        activeNotiUnits.onEach { notifications ->
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

    }

    fun accessNotification(notiUnit: NotiUnit) {
        launchNotificationContent(notiUnit, NotiActionType.AccessClickSearch)
    }

    fun accessAndDismissNotification(notiUnit: NotiUnit) {
        launchNotificationContent(notiUnit, NotiActionType.AccessClickDismiss)
    }

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

    fun actOnNoti(notiKey: String, action: String) {
        val typed = NotiActionType.fromWireValue(action)
        if (typed != null) {
            actOnNoti(notiKey, typed)
        } else {
            viewModelScope.launch {
                actionsController.actOnNotiLegacy(notiKey, action)
                if (action.contains("dismiss")) postOngoingNotification(context)
                // Emit event for preference learning (Flow 3: Manual Extract)
                if (action == "extract_saved_item" ||
                    action.startsWith("extract_saved_item_with_records") ||
                    action == "extract_reminder" ||
                    action.startsWith("extract_reminder_with_records")) {
                    _manualExtractEvent.tryEmit(notiKey)
                }
            }
        }
    }

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

    fun deleteAllNotis() {
        viewModelScope.launch(Dispatchers.IO) {
            notiRepository.deleteAllNotis()
            postOngoingNotification(context)
        }
    }

    /**
     * Clears only the threads currently visible on the given category page: matching category and time
     * bucket, excluding pinned. Whole units are dismissed (records included).
     */
    fun clearVisibleNotis(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - HomeViewModel.newNotiWindowMs()
            val llm = llmStatesByKey.value
            val recentOnly = _notiRecentOnly.value
            val keys = newNotificationUnits.value
                .filter { du -> isVisibleClearable(du, category, recentOnly, cutoff, llm) }
                .map { it.notiKey }
            if (keys.isNotEmpty()) {
                notiRepository.deleteNotisByKeys(keys)
                postOngoingNotification(context)
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

    fun archiveNewNotificationCard(notiKey: String) {
        viewModelScope.launch {
            val activeUnit = _activeNotiUnits.value.firstOrNull { it.notiKey == notiKey }?.notiUnit
            if (activeUnit?.isPinned == true) return@launch

            notiRepository.removeNotiUnit(notiKey)
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

    /** Batch-fetch NotiUnit rows for the given keys, for showing app icons in the history list. */
    suspend fun getNotiUnitsForHistory(notiKeys: List<String>): Map<String, NotiUnit> {
        if (notiKeys.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            notiRepository.getNotiUnitByKeys(notiKeys.distinct()).associateBy { it.notiKey }
        }
    }

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

    fun toggleSortingMode() {
        val wasSorting = isSortingMode.value
        filters.toggleSortingMode()
        // If exiting sorting mode, commit.
        if (wasSorting && !isSortingMode.value) {
            commitManualSortSessionIfNeeded()
        }
    }
}
