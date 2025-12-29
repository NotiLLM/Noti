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
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem
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

    private val _isAppCategoryView = MutableStateFlow(false)
    val isAppCategoryView: StateFlow<Boolean> = _isAppCategoryView

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateAppCategory(newAppCategory: String) {
        _isTargetLoading.value = true
        _targetLoadingToken.value = System.currentTimeMillis()
        if (isSortingMode.value) toggleSortingMode()
        persistReadStatus()
        updateUnreadCounts()
        _isAppCategoryView.value = newAppCategory != APP_CATEGORY_ALL
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

    init {
        // Main subscription to the grouped data flow
        notiRepository.getGroupedNotifications(category, appCategory, isAppCategoryView)
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
                val cat = category.value
                val appCat = appCategory.value
                // Approximate check
                if (notifications.isNotEmpty()) {
                    _isTargetLoading.value = false
                }
            }
        }.launchIn(viewModelScope)

        removeExpiredRecords()
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

    private val _seenNotiKeys = ConcurrentHashMap.newKeySet<Pair<String, Long>>()
    private val _seenRecordIds = ConcurrentHashMap.newKeySet<String>()

    fun markNotificationAsRead(notiKey: String, isManual: Boolean) {
        if (isManual) {
            viewModelScope.launch(Dispatchers.IO) {
                notiRepository.actOnNoti(notiKey, "mark_read")
            }
            return
        }

        // Search in flattened list
        val currentItems = _groupedNotifications.value
        val foundUnit = currentItems.asSequence().flatMap { item ->
            when(item) {
                is org.muilab.notigpt.model.notifications.NotiItem -> sequenceOf(item.displayUnit)
                is org.muilab.notigpt.model.notifications.NotiGroupItem -> item.children.asSequence()
            }
        }.firstOrNull { it.notiKey == notiKey }

        if (foundUnit != null) {
            if (foundUnit.notiUnit.isCompletelyRead) return
            Log.d("ViewModelReadState", "Marking card as read: $notiKey")
            val currentTime = System.currentTimeMillis()
            _seenNotiKeys.add(Pair(notiKey, currentTime))
        }
    }

    fun markRecordAsRead(recordId: String) {
        // Finding record in nested structure is expensive, but necessary
        // Optimization: UI calls this, so it exists.
        _seenRecordIds.add(recordId)
        // Persistence handled by persistReadStatus called on pause
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun persistReadStatus() {
        if (_seenNotiKeys.isEmpty() && _seenRecordIds.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ViewModelReadState", "Persisting seenNotis: ${_seenNotiKeys.size}, seenRecords: ${_seenRecordIds.size}")
            notiRepository.updateSeenNotifications(_seenNotiKeys.toSet(), _seenRecordIds.toSet())
            _seenNotiKeys.clear()
            _seenRecordIds.clear()
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

    private val fullRecordsCache = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.MutableStateFlow<List<org.muilab.notigpt.model.notifications.NotiRecord>>>()
    private val fullRecordsJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun getFullRecordsFlow(notiKey: String): kotlinx.coroutines.flow.StateFlow<List<org.muilab.notigpt.model.notifications.NotiRecord>> {
        return fullRecordsCache.getOrPut(notiKey) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }
    }

    fun loadFullRecordsForKey(notiKey: String) {
        if (fullRecordsJobs.containsKey(notiKey)) return
        val stateFlow = fullRecordsCache.getOrPut(notiKey) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }

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