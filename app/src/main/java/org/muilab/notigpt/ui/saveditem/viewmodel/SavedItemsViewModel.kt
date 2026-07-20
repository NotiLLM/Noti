package org.muilab.notigpt.ui.saveditem.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.enqueueRegenerateOne
import org.muilab.notigpt.data.export.ExportableItem
import org.muilab.notigpt.model.features.WhenBucket
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.ui.common.navigation.SavedListFilter
import org.muilab.notigpt.util.time.DayBoundaries
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksRepository
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.data.repository.saveditem.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRepository
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.saveditem.SavedSubItemRepository
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager
import java.util.UUID

/**
 * ViewModel for savedItems, sub-tasks, related notification context, Google Tasks export, and regeneration jobs.
 *
 * Keep screen orchestration here while persistence stays in repositories and remote/background work stays behind
 * platform or n8n enqueue helpers.
 */
class SavedItemsViewModel(application: Application) : AndroidViewModel(application) {

    enum class FilterTab { All, Pending, Tasks, Memos, Completed, Keep, Archived, Starred }
    enum class ListMode { All, Tasks, Keep }

    /** A staged update/merge rendered in the saved-item lists before it is accepted. */
    data class PendingListPreview(
        val item: SavedItem,
        val subItems: List<SavedSubItem>,
        val mergeSourceItemIds: Set<String> = emptySet(),
        val reason: String = "",
    )

    /**
     * Result of Google Tasks export operation.
     */
    sealed class GoogleTasksExportResult {
        object Idle : GoogleTasksExportResult()
        object Loading : GoogleTasksExportResult()
        data class Success(val taskTitle: String) : GoogleTasksExportResult()
        data class Error(val message: String) : GoogleTasksExportResult()
        object NotSignedIn : GoogleTasksExportResult()
    }

    private val repo: SavedItemRepository
    private val subTaskRepo: SavedSubItemRepository
    private val googleTasksRepo: GoogleTasksRepository
    private val relatedNotificationsRepo: SavedItemRelatedNotificationsRepository
    private val changeLogRepo: SavedItemChangeLogRepository
    private val pendingProposedOpRepo: PendingProposedOpRepository

    /** Extraction pipeline health, for the "server unreachable" banner. */
    val extractionStatus: StateFlow<org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.Status> =
        org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.status

    /** Records still waiting to be sent for extraction. */
    val pendingExtractionCount: StateFlow<Int>

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = SavedItemRepository(db.savedItemDao(), application.applicationContext)
        subTaskRepo = SavedSubItemRepository(db.subTaskDao())
        googleTasksRepo = GoogleTasksRepository(application.applicationContext)
        relatedNotificationsRepo = SavedItemRelatedNotificationsRepository(application.applicationContext)
        changeLogRepo = SavedItemChangeLogRepository(db.savedItemChangeLogDao())
        pendingProposedOpRepo = PendingProposedOpRepository(application.applicationContext)
        pendingExtractionCount = db.recordDao().observePendingExtractionCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    }

    /** Manual retry from the offline banner: re-drives the extraction pipeline for pending threads. */
    fun retryExtraction() {
        viewModelScope.launch {
            val db = AppDatabase.getInstance(getApplication<Application>().applicationContext)
            val keys = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.notiLlmStateDao().getActiveShouldExtractKeys()
            }
            keys.forEach { org.muilab.notigpt.data.remote.n8n.enqueueExtractionPipeline(getApplication(), it) }
        }
    }

    private val _filter = MutableStateFlow(FilterTab.All)
    val filter: StateFlow<FilterTab> = _filter

    fun setFilter(tab: FilterTab) {
        _filter.value = tab
    }

    private val _listMode = MutableStateFlow(ListMode.All)
    val listMode: StateFlow<ListMode> = _listMode

    fun setListMode(mode: ListMode) {
        _listMode.value = mode
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * When non-null, the savedItems list is a home-screen smart filter (planned-date bucket or
     * starred) that mixes tasks and keeps and ignores [_filter]/[_listMode]. Tasks/Keep collections
     * are NOT routed here — they use [_listMode] with their own in-screen chips.
     */
    private val _smartFilter = MutableStateFlow<SavedListFilter?>(null)
    val smartFilter: StateFlow<SavedListFilter?> = _smartFilter

    fun setSmartFilter(filter: SavedListFilter?) {
        _smartFilter.value = filter
    }

    private val allFlow = repo.observeAll()
    private val tasksFlow = repo.observeTasks()
    private val memosFlow = repo.observeMemos()
    private val activeKeepsFlow = repo.observeActiveKeeps()
    private val archivedKeepsFlow = repo.observeArchivedKeeps()
    private val completedFlow = repo.observeCompletedTasks()
    private val newItemsFlow = repo.observeNewItems()

    /**
     * Presentation-only previews for staged updates/merges. The persisted item remains unchanged
     * until the user accepts the staged group.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingPreviews: StateFlow<Map<String, PendingListPreview>> = combine(
        pendingProposedOpRepo.observePending(),
        allFlow,
    ) { ops, _ -> ops }
        .mapLatest { ops ->
            pendingProposedOpRepo.groupOps(ops)
                .filter { !it.isCreate }
                .mapNotNull { group ->
                    val preview = pendingProposedOpRepo.buildPreview(group) ?: return@mapNotNull null
                    val current = repo.getById(group.targetItemId!!) ?: return@mapNotNull null
                    val sourceIds = group.ops.flatMap { pending ->
                        try {
                            val arr = JSONArray(pending.mergeSourceItemIds)
                            buildList {
                                for (i in 0 until arr.length()) {
                                    arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }.toSet()
                    PendingListPreview(
                        // Keep completed/archived list membership stable while replacing only the
                        // content fields with the staged preview.
                        item = preview.item.copy(state = current.state),
                        subItems = preview.subItems,
                        mergeSourceItemIds = sourceIds,
                        reason = group.reason,
                    )
                }
                .associateBy { it.item.savedItemId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allSavedItems: StateFlow<List<SavedItem>> = allFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newSavedItems: StateFlow<List<SavedItem>> = newItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Filtered item list consumed by SavedItemsScreen.
     *
     * Search terms are split on '+', then AND-matched against title and content so users can narrow noisy item
     * lists without changing persisted item data.
     */
    val savedItems: StateFlow<List<SavedItem>> = combine(
        _filter, _searchQuery, _listMode, _smartFilter, allFlow, tasksFlow, memosFlow, completedFlow, activeKeepsFlow, archivedKeepsFlow, pendingPreviews
    ) { values ->
        val f = values[0] as FilterTab
        @Suppress("UNCHECKED_CAST")
        val query = values[1] as String
        val mode = values[2] as ListMode
        val smart = values[3] as SavedListFilter?
        @Suppress("UNCHECKED_CAST")
        val all = values[4] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[5] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val memos = values[6] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val completed = values[7] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val activeKeeps = values[8] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val archivedKeeps = values[9] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val pendingPreviews = values[10] as Map<String, PendingListPreview>

        val baseList = if (smart != null) {
            // Home smart filters: planned-date buckets (or starred), mixing tasks and keeps, over the
            // actionable set only (exclude completed/archived) to match the home-screen counts.
            val startOfTomorrow = DayBoundaries.startOfTomorrowMs()
            val scoped = all.filter { !it.isCompleted && !it.isArchived }
            when (smart) {
                SavedListFilter.TodayEarlier -> scoped.filter { SavedItem.plannedBucket(it.whenAtMs, startOfTomorrow, it.isTask) == WhenBucket.TodayEarlier }
                SavedListFilter.Upcoming -> scoped.filter { SavedItem.plannedBucket(it.whenAtMs, startOfTomorrow, it.isTask) == WhenBucket.Upcoming }
                SavedListFilter.Someday -> scoped.filter { SavedItem.plannedBucket(it.whenAtMs, startOfTomorrow, it.isTask) == WhenBucket.Someday }
                SavedListFilter.Undetermined -> scoped.filter { SavedItem.plannedBucket(it.whenAtMs, startOfTomorrow, it.isTask) == WhenBucket.Undetermined }
                SavedListFilter.Starred -> scoped.filter { it.isStarred }
                // Tasks/Keep collections are never routed through smart filters.
                SavedListFilter.Tasks, SavedListFilter.Keep -> scoped
            }.sortedWith(
                compareBy<SavedItem> {
                    when(smart) {
                        SavedListFilter.TodayEarlier -> it.whenAtMs
                        SavedListFilter.Upcoming -> it.whenAtMs
                        else -> -it.lastUpdateTimestamp
                    }
                }
                    .thenBy { it.lastUpdateTimestamp }
            )
        } else when (mode) {
            ListMode.Keep -> when (f) {
                FilterTab.Keep -> activeKeeps
                FilterTab.Archived -> archivedKeeps
                FilterTab.Starred -> memos.filter { it.isStarred }
                else -> memos // All
            }
            ListMode.Tasks -> when (f) {
                FilterTab.Pending -> tasks.filter { !it.isCompleted }
                FilterTab.Completed -> completed
                FilterTab.Starred -> tasks.filter { it.isStarred }
                else -> tasks // All (saved + completed)
            }
            ListMode.All -> {
                val modeIds = all.mapTo(mutableSetOf()) { it.savedItemId }
                when (f) {
                    FilterTab.Pending -> all.filter { !it.isCompleted }
                    FilterTab.Tasks -> tasks.filter { it.savedItemId in modeIds }
                    FilterTab.Memos -> memos.filter { it.savedItemId in modeIds }
                    FilterTab.Completed -> completed.filter { it.savedItemId in modeIds }
                    FilterTab.Starred -> all.filter { it.isStarred }
                    else -> all // All
                }
            }
        }.sortedBy { -it.lastUpdateTimestamp }

        val mergeSourceIds = pendingPreviews.values
            .flatMap { it.mergeSourceItemIds }
            .toSet()
        val displayList = baseList
            // A merge preview replaces the eventual survivor in the list; source rows remain
            // durable until acceptance but are hidden here to avoid showing duplicate cards.
            .filter { it.savedItemId !in mergeSourceIds || it.savedItemId in pendingPreviews }
            .map { pendingPreviews[it.savedItemId]?.item ?: it }

        if (query.isBlank()) {
            displayList
        } else {
            val terms = query.split("+").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (terms.isEmpty()) {
                displayList
            } else {
                displayList.filter { item ->
                    val searchable = "${item.title} ${item.content}".lowercase()
                    terms.all { term -> searchable.contains(term) }
                }
            }
        }
    }
        // Drop byte-identical re-emissions so a write to any of the six observed saved_item queries
        // doesn't recompose the list when the resulting content is unchanged.
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sub-tasks grouped by parent item ID. */
    val allSavedSubItemsBySavedItem: StateFlow<Map<String, List<SavedSubItem>>> =
        subTaskRepo.observeAllBySavedItem()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleCompleted(item: SavedItem, completed: Boolean) {
        viewModelScope.launch {
            repo.setCompleted(item.savedItemId, completed, System.currentTimeMillis())
        }
    }

    fun toggleStarred(item: SavedItem) {
        viewModelScope.launch {
            repo.setStarred(item.savedItemId, !item.isStarred, System.currentTimeMillis())
        }
    }

    /** Explicit review acknowledgment ("Got it"): clears the New/Updated badge and moves the change cursor. */
    fun acknowledgeReview(savedItemId: String) {
        viewModelScope.launch {
            repo.acknowledgeReview(savedItemId, System.currentTimeMillis())
        }
    }

    /** Change history rows for one item, newest first; drives the detail view's change sections. */
    fun changeLogFlow(savedItemId: String): Flow<List<SavedItemChangeLog>> =
        changeLogRepo.observeByItem(savedItemId)

    /** [whenAtMs] = 0 clears the When. */
    fun setWhen(savedItemId: String, whenAtMs: Long) {
        viewModelScope.launch {
            repo.setWhen(savedItemId, whenAtMs, System.currentTimeMillis())
        }
    }

    fun archiveKeep(savedItemId: String) {
        viewModelScope.launch {
            repo.setState(savedItemId, SavedItemState.Archived, System.currentTimeMillis())
        }
    }

    fun markSaved(savedItemId: String) {
        viewModelScope.launch {
            repo.setState(savedItemId, SavedItemState.Saved, System.currentTimeMillis())
        }
    }

    fun markSavedByIds(savedItemIds: List<String>) {
        viewModelScope.launch {
            repo.markSavedByIds(savedItemIds, System.currentTimeMillis())
        }
    }

    fun deleteByIds(savedItemIds: List<String>) {
        viewModelScope.launch {
            // Hard delete: the repository removes sub-items, links, and change logs with the row.
            repo.deleteByIds(savedItemIds, System.currentTimeMillis())
        }
    }

    fun delete(savedItemId: String) {
        viewModelScope.launch {
            repo.deleteById(savedItemId, System.currentTimeMillis())
        }
    }

    fun upsert(item: SavedItem) {
        viewModelScope.launch {
            repo.upsert(item.copy(lastUpdateTimestamp = System.currentTimeMillis()))
        }
    }

    /** Accepts a staged update/merge, then opens the now-current item for manual editing. */
    fun approvePendingForEdit(savedItemId: String, onApproved: (SavedItem) -> Unit) {
        viewModelScope.launch {
            val group = pendingProposedOpRepo.groupOps(pendingProposedOpRepo.getPending())
                .firstOrNull { it.targetItemId == savedItemId }
            if (group == null) {
                repo.getById(savedItemId)?.let(onApproved)
                return@launch
            }
            val outcome = pendingProposedOpRepo.applyGroup(group) ?: return@launch
            repo.getById(outcome.appliedItemId)?.let(onApproved)
        }
    }


    data class RelatedNotificationsState(
        val savedItemId: String? = null,
        val isLoading: Boolean = false,
        val related: SavedItemRelatedNotificationsRepository.RelatedNotifications = SavedItemRelatedNotificationsRepository.RelatedNotifications.Empty,
    )

    private val _relatedNotificationsState = MutableStateFlow(RelatedNotificationsState())
    val relatedNotificationsState: StateFlow<RelatedNotificationsState> = _relatedNotificationsState

    /**
     * Loads notification context linked to a item for provenance/preview UI.
     *
     * This is read-only item context; edits to savedItems or notifications should use their own repository paths.
     */
    fun loadRelatedNotifications(item: SavedItem) {
        val current = _relatedNotificationsState.value
        if (current.savedItemId == item.savedItemId && current.isLoading) return

        viewModelScope.launch {
            _relatedNotificationsState.value = RelatedNotificationsState(
                savedItemId = item.savedItemId,
                isLoading = true,
            )

            val related = try {
                relatedNotificationsRepo.getRelatedNotifications(item)
            } catch (t: Throwable) {
                Log.e("SavedItemRelatedNotis", "Failed loading related notifications", t)
                SavedItemRelatedNotificationsRepository.RelatedNotifications.Empty
            }

            _relatedNotificationsState.value = RelatedNotificationsState(
                savedItemId = item.savedItemId,
                isLoading = false,
                related = related,
            )
        }
    }

    // ========== Sub-task CRUD ==========

    fun addSavedSubItem(parentSavedItemId: String) {
        val id = "st_" + UUID.randomUUID().toString().take(8)
        val subTask = SavedSubItem(
            savedSubItemId = id,
            parentSavedItemId = parentSavedItemId,
            text = "",
        )
        viewModelScope.launch { repo.upsertSubItem(subTask, System.currentTimeMillis()) }
    }

    fun upsertSavedSubItem(subTask: SavedSubItem) {
        viewModelScope.launch {
            repo.upsertSubItem(subTask, System.currentTimeMillis())
        }
    }

    fun deleteSavedSubItem(savedSubItemId: String) {
        viewModelScope.launch {
            repo.deleteSubItem(savedSubItemId, System.currentTimeMillis())
        }
    }

    fun toggleSavedSubItemCompleted(savedSubItemId: String, completed: Boolean) {
        viewModelScope.launch {
            repo.setSubItemCompleted(savedSubItemId, completed, System.currentTimeMillis())
        }
    }

    fun markViewed(savedItemId: String) {
        viewModelScope.launch {
            repo.setViewed(savedItemId)
        }
    }

    /** Batch-mark multiple savedItems as viewed (called when leaving the screen). */
    fun markViewedBatch(savedItemIds: Set<String>) {
        if (savedItemIds.isEmpty()) return
        viewModelScope.launch {
            savedItemIds.forEach { repo.setViewed(it) }
        }
    }

    // ========== Regeneration ==========

    fun regenerateOne(savedItemId: String) {
        enqueueRegenerateOne(getApplication(), savedItemId)
    }

    // ========== Google Tasks Integration ==========

    private val _googleTasksExportResult = MutableStateFlow<GoogleTasksExportResult>(GoogleTasksExportResult.Idle)
    val googleTasksExportResult: StateFlow<GoogleTasksExportResult> = _googleTasksExportResult

    /**
     * Check if user is signed in to Google with Tasks permission.
     */
    fun isGoogleSignedIn(): Boolean {
        return GoogleTasksAuthManager.isSignedIn(getApplication())
    }


    fun handleGoogleTasksSignInResult(data: Intent?, pendingReminder: SavedItem?) {
        viewModelScope.launch {
            val account = GoogleTasksAuthManager.handleSignInResult(data)
            if (account != null && pendingReminder != null) {
                exportToGoogleTasks(pendingReminder)
            } else if (account == null) {
                _googleTasksExportResult.value = GoogleTasksExportResult.NotSignedIn
            }
        }
    }

    /**
     * Export a item to Google Tasks.
     */
    fun exportToGoogleTasks(item: SavedItem) {
        viewModelScope.launch {
            _googleTasksExportResult.value = GoogleTasksExportResult.Loading
            val result = googleTasksRepo.createTaskFromSavedItem(item)
            _googleTasksExportResult.value = when (result) {
                is GoogleTasksRepository.TaskResult.Success -> GoogleTasksExportResult.Success(result.taskTitle)
                is GoogleTasksRepository.TaskResult.Error -> GoogleTasksExportResult.Error(result.message)
                is GoogleTasksRepository.TaskResult.NotSignedIn -> GoogleTasksExportResult.NotSignedIn
            }
        }
    }

    /**
     * Export any [ExportableItem] (including SavedSubItem) to Google Tasks.
     */
    fun exportToGoogleTasks(item: ExportableItem) {
        viewModelScope.launch {
            _googleTasksExportResult.value = GoogleTasksExportResult.Loading
            val result = googleTasksRepo.createTaskFromExportable(item)
            _googleTasksExportResult.value = when (result) {
                is GoogleTasksRepository.TaskResult.Success -> GoogleTasksExportResult.Success(result.taskTitle)
                is GoogleTasksRepository.TaskResult.Error -> GoogleTasksExportResult.Error(result.message)
                is GoogleTasksRepository.TaskResult.NotSignedIn -> GoogleTasksExportResult.NotSignedIn
            }
        }
    }

    /**
     * Clear the export result (reset to Idle).
     */
    fun clearGoogleTasksExportResult() {
        _googleTasksExportResult.value = GoogleTasksExportResult.Idle
    }
}
