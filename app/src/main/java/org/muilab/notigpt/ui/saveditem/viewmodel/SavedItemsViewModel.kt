package org.muilab.notigpt.ui.saveditem.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
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
import org.muilab.notigpt.data.remote.n8n.enqueueSplitOne
import org.muilab.notigpt.data.remote.n8n.enqueueSuggestionRefresh
import org.muilab.notigpt.data.export.ExportableItem
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.PendingProposedOpType
import org.muilab.notigpt.ui.common.navigation.SavedListFilter
import org.muilab.notigpt.util.time.SmartFilterWindows
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksRepository
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.data.repository.saveditem.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRepository
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.saveditem.TodoStepRepository
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager
import org.muilab.notigpt.data.repository.suggestion.SuggestionSnapshotStore
import org.muilab.notigpt.data.repository.suggestion.SuggestionUiState
import java.util.UUID

/**
 * ViewModel for saved items, Todo steps, related notification context, Google Tasks export, and regeneration jobs.
 *
 * Keep screen orchestration here while persistence stays in repositories and remote/background work stays behind
 * platform or n8n enqueue helpers.
 */
class SavedItemsViewModel(application: Application) : AndroidViewModel(application) {

    enum class FilterTab { All, Pending, Todos, Keeps, Completed, Keep, Archived, Starred }
    enum class ListMode { All, Todos, Keep }

    /** A staged update/merge rendered in the saved-item lists before it is accepted. */
    data class PendingListPreview(
        val item: SavedItem,
        val steps: List<TodoStep>,
        val mergeSourceItemIds: Set<String> = emptySet(),
        val reason: String = "",
        val operationType: String = PendingProposedOpType.Update,
        val splitChildren: List<ReviewItemDraft> = emptyList(),
        val isProcessing: Boolean = false,
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
    private val stepRepo: TodoStepRepository
    private val googleTasksRepo: GoogleTasksRepository
    private val relatedNotificationsRepo: SavedItemRelatedNotificationsRepository
    private val changeLogRepo: SavedItemChangeLogRepository
    private val pendingProposedOpRepo: PendingProposedOpRepository
    private val suggestionStore = SuggestionSnapshotStore.getInstance(application.applicationContext)
    val suggestionState: StateFlow<SuggestionUiState> = suggestionStore.state

    /** Extraction pipeline health, for the "server unreachable" banner. */
    val extractionStatus: StateFlow<org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.Status> =
        org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.status

    /** Records still waiting to be sent for extraction. */
    val pendingExtractionCount: StateFlow<Int>

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = SavedItemRepository(db.savedItemDao(), application.applicationContext)
        stepRepo = TodoStepRepository(db.todoStepDao())
        googleTasksRepo = GoogleTasksRepository(application.applicationContext)
        relatedNotificationsRepo = SavedItemRelatedNotificationsRepository(application.applicationContext)
        changeLogRepo = SavedItemChangeLogRepository(db.savedItemChangeLogDao())
        pendingProposedOpRepo = PendingProposedOpRepository(application.applicationContext)
        suggestionStore.syncAccount()
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
     * When non-null, the list is a home-screen attention filter. Type chips use [_listMode]; the
     * Todo/Keep drawer collections are routed without a smart filter.
     */
    private val _smartFilter = MutableStateFlow<SavedListFilter?>(null)
    val smartFilter: StateFlow<SavedListFilter?> = _smartFilter

    fun setSmartFilter(filter: SavedListFilter?) {
        _smartFilter.value = filter
    }

    private val allFlow = repo.observeAll()
    private val todosFlow = repo.observeTodos()
    private val keepsFlow = repo.observeKeeps()
    private val activeKeepsFlow = repo.observeActiveKeeps()
    private val archivedKeepsFlow = repo.observeArchivedKeeps()
    private val completedFlow = repo.observeCompletedTodos()
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
                .mapNotNull { group ->
                    val preview = pendingProposedOpRepo.buildPreview(group) ?: return@mapNotNull null
                    if (group.isCreate) {
                        return@mapNotNull PendingListPreview(
                            item = preview.item,
                            steps = preview.steps,
                            reason = group.reason,
                            operationType = PendingProposedOpType.Create,
                        )
                    }
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
                    val operationType = group.ops.singleOrNull()?.opType ?: PendingProposedOpType.Update
                    val displayItem = if (operationType == PendingProposedOpType.Split) {
                        current.copy(
                            content = preview.splitChildren.joinToString(" · ") { it.item.title },
                            state = current.state,
                        )
                    } else preview.item.copy(state = current.state)
                    PendingListPreview(
                        // Keep completed/archived list membership stable while replacing only the
                        // content fields with the staged preview.
                        item = displayItem,
                        steps = if (operationType == PendingProposedOpType.Split) emptyList() else preview.steps,
                        mergeSourceItemIds = sourceIds,
                        reason = group.reason,
                        operationType = operationType,
                        splitChildren = preview.splitChildren,
                        isProcessing = preview.isProcessing,
                    )
                }
                .associateBy { preview ->
                    if (preview.operationType == PendingProposedOpType.Split) {
                        preview.item.savedItemId
                    } else preview.item.savedItemId
                }
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
        _filter, _searchQuery, _listMode, _smartFilter, allFlow, todosFlow, keepsFlow, completedFlow, activeKeepsFlow, archivedKeepsFlow, pendingPreviews, suggestionState
    ) { values ->
        val f = values[0] as FilterTab
        @Suppress("UNCHECKED_CAST")
        val query = values[1] as String
        val mode = values[2] as ListMode
        val smart = values[3] as SavedListFilter?
        @Suppress("UNCHECKED_CAST")
        val all = values[4] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val todos = values[5] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val keeps = values[6] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val completed = values[7] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val activeKeeps = values[8] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val archivedKeeps = values[9] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val pendingPreviews = values[10] as Map<String, PendingListPreview>
        val suggestions = values[11] as SuggestionUiState

        val unsortedBaseList = if (smart != null) {
            val now = System.currentTimeMillis()
            val scoped = all.filter { !it.isCompleted && !it.isArchived }
            val filtered = when (smart) {
                SavedListFilter.Suggested -> {
                    val byId = scoped.associateBy { it.savedItemId }
                    suggestions.snapshot?.items.orEmpty().mapNotNull { byId[it.savedItemId] }
                }
                SavedListFilter.Starred -> scoped.filter { it.isStarred }
                SavedListFilter.DueSoon -> scoped.filter {
                    it.isTodo && it.deadlineAtMs > 0L &&
                        it.deadlineAtMs < SmartFilterWindows.dueSoonEndExclusiveMs(now)
                }
                SavedListFilter.RecentlyUpdated -> scoped.filter {
                    it.lastUpdateTimestamp >= now - SmartFilterWindows.RECENTLY_UPDATED_WINDOW_MS
                }
                SavedListFilter.AllItems, SavedListFilter.Todos, SavedListFilter.Keep -> scoped
            }
            when {
                smart == SavedListFilter.DueSoon -> filtered
                mode == ListMode.Todos -> filtered.filter { it.isTodo }
                mode == ListMode.Keep -> filtered.filter { !it.isTodo }
                else -> filtered
            }
        } else when (mode) {
            ListMode.Keep -> when (f) {
                FilterTab.Keep -> activeKeeps
                FilterTab.Archived -> archivedKeeps
                FilterTab.Starred -> keeps.filter { it.isStarred }
                else -> keeps // All
            }
            ListMode.Todos -> when (f) {
                FilterTab.Pending -> todos.filter { !it.isCompleted }
                FilterTab.Completed -> completed
                FilterTab.Starred -> todos.filter { it.isStarred }
                else -> todos // All (active + completed)
            }
            ListMode.All -> {
                val modeIds = all.mapTo(mutableSetOf()) { it.savedItemId }
                when (f) {
                    FilterTab.Pending -> all.filter { !it.isCompleted }
                    FilterTab.Todos -> todos.filter { it.savedItemId in modeIds }
                    FilterTab.Keeps -> keeps.filter { it.savedItemId in modeIds }
                    FilterTab.Completed -> completed.filter { it.savedItemId in modeIds }
                    FilterTab.Starred -> all.filter { it.isStarred }
                    else -> all // All
                }
            }
        }
        // H's response order is the Suggested ranking. Other lists keep the established attention order.
        val baseList = if (smart == SavedListFilter.Suggested) unsortedBaseList else {
            unsortedBaseList.sortedWith(
                compareByDescending<SavedItem> { it.isStarred }
                    .thenBy { it.deadlineAtMs.takeIf { deadline -> deadline > 0L } ?: Long.MAX_VALUE }
                    .thenByDescending { it.lastUpdateTimestamp }
                    .thenByDescending { it.savedItemId }
            )
        }

        val mergeSourceIds = pendingPreviews.values
            .flatMap { it.mergeSourceItemIds }
            .toSet()
        val displayList = baseList
            // A merge preview replaces the eventual survivor in the list; source rows remain
            // durable until acceptance but are hidden here to avoid showing duplicate cards.
            .filter { it.savedItemId !in mergeSourceIds || it.savedItemId in pendingPreviews }
            .map { pendingPreviews[it.savedItemId]?.item ?: it }

        val pendingCreates = pendingPreviews.values
            .filter { it.operationType == PendingProposedOpType.Create }
            .map { it.item }
            .filter { item ->
                val typeMatches = when (mode) {
                    ListMode.Todos -> item.isTodo
                    ListMode.Keep -> !item.isTodo
                    ListMode.All -> true
                }
                val filterMatches = when (f) {
                    FilterTab.Todos, FilterTab.Pending -> item.isTodo
                    FilterTab.Keeps, FilterTab.Keep -> !item.isTodo
                    FilterTab.Completed, FilterTab.Archived -> false
                    FilterTab.Starred -> item.isStarred
                    else -> true
                }
                val smartMatches = when (smart) {
                    null, SavedListFilter.AllItems, SavedListFilter.Todos, SavedListFilter.Keep -> true
                    SavedListFilter.Starred -> item.isStarred
                    SavedListFilter.DueSoon -> item.isTodo && item.deadlineAtMs > 0L &&
                        item.deadlineAtMs < SmartFilterWindows.dueSoonEndExclusiveMs(System.currentTimeMillis())
                    SavedListFilter.RecentlyUpdated -> true
                    SavedListFilter.Suggested -> false
                }
                typeMatches && filterMatches && smartMatches
            }
        val completeDisplayList = (displayList + pendingCreates).distinctBy { it.savedItemId }

        if (query.isBlank()) {
            completeDisplayList
        } else {
            val terms = query.split("+").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (terms.isEmpty()) {
                displayList
            } else {
            completeDisplayList.filter { item ->
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

    fun refreshSuggestions(force: Boolean = true) {
        enqueueSuggestionRefresh(getApplication(), force)
    }

    fun dismissSuggestion(savedItemId: String) {
        suggestionStore.dismiss(savedItemId)
    }

    /** Todo steps grouped by parent item ID. */
    val allTodoStepsBySavedItem: StateFlow<Map<String, List<TodoStep>>> =
        stepRepo.observeAllBySavedItem()
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

    /** [deadlineAtMs] = 0 clears the deadline. */
    fun setDeadline(savedItemId: String, deadlineAtMs: Long) {
        viewModelScope.launch {
            repo.setDeadline(savedItemId, deadlineAtMs, System.currentTimeMillis())
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
            val now = System.currentTimeMillis()
            repo.upsert(item.copy(lastUpdateTimestamp = now, syncModifiedAt = now))
        }
    }

    fun autosaveDraft(item: SavedItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.upsert(item.copy(lastUpdateTimestamp = now, syncModifiedAt = now))
        }
    }

    /** Accepts a staged update/merge, then opens the now-current item for manual editing. */
    fun approvePendingForEdit(savedItemId: String, onApproved: (SavedItem) -> Unit) {
        viewModelScope.launch {
            val group = pendingProposedOpRepo.groupOps(pendingProposedOpRepo.getPending())
                .firstOrNull { candidate ->
                    candidate.targetItemId == savedItemId ||
                        (candidate.isCreate && savedItemId == "pending_${candidate.ops.first().opId}")
                }
            if (group == null) {
                repo.getById(savedItemId)?.let(onApproved)
                return@launch
            }
            val outcome = pendingProposedOpRepo.applyGroup(group) ?: return@launch
            repo.getById(outcome.appliedItemId)?.let(onApproved)
        }
    }

    fun rejectPending(savedItemId: String) {
        viewModelScope.launch {
            val group = pendingProposedOpRepo.groupOps(pendingProposedOpRepo.getPending())
                .firstOrNull { candidate ->
                    candidate.targetItemId == savedItemId ||
                        (candidate.isCreate && savedItemId == "pending_${candidate.ops.first().opId}")
                } ?: return@launch
            pendingProposedOpRepo.discardGroup(group)
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

    fun addTodoStep(parentSavedItemId: String) {
        val id = "st_" + UUID.randomUUID().toString().take(8)
        val step = TodoStep(
            todoStepId = id,
            parentSavedItemId = parentSavedItemId,
            text = "",
        )
        viewModelScope.launch { repo.upsertSubItem(step, System.currentTimeMillis()) }
    }

    fun upsertTodoStep(step: TodoStep) {
        viewModelScope.launch {
            repo.upsertSubItem(step, System.currentTimeMillis())
        }
    }

    fun deleteTodoStep(todoStepId: String) {
        viewModelScope.launch {
            repo.deleteSubItem(todoStepId, System.currentTimeMillis())
        }
    }

    fun toggleTodoStepCompleted(todoStepId: String, completed: Boolean) {
        viewModelScope.launch {
            repo.setSubItemCompleted(todoStepId, completed, System.currentTimeMillis())
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

    /** Persists the visible draft before starting a user-requested structural transformation. */
    fun requestRegenerate(item: SavedItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.upsert(item.copy(lastUpdateTimestamp = now, syncModifiedAt = now))
            if (pendingProposedOpRepo.beginTransform(item.savedItemId, PendingProposedOpType.Regenerate, now) != null) {
                enqueueRegenerateOne(getApplication(), item.savedItemId)
            }
        }
    }

    fun requestSplit(item: SavedItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.upsert(item.copy(lastUpdateTimestamp = now, syncModifiedAt = now))
            if (pendingProposedOpRepo.beginTransform(item.savedItemId, PendingProposedOpType.Split, now) != null) {
                enqueueSplitOne(getApplication(), item.savedItemId)
            }
        }
    }

    fun cancelTransform(savedItemId: String, type: String) {
        viewModelScope.launch {
            val workName = if (type == PendingProposedOpType.Split) {
                "n8n_split_one_$savedItemId"
            } else "n8n_regenerate_one_$savedItemId"
            WorkManager.getInstance(getApplication<Application>()).cancelUniqueWork(workName)
            pendingProposedOpRepo.clearProcessingTransform(savedItemId, type)
        }
    }

    fun checkActiveReminder(savedItemId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val active = AppDatabase.getInstance(getApplication<Application>()).reminderDao()
                .getBySavedItemId(savedItemId)
                .any { it.status == org.muilab.notigpt.model.features.ReminderStatus.Scheduled ||
                    it.status == org.muilab.notigpt.model.features.ReminderStatus.DueUnseen }
            onResult(active)
        }
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
     * Export any [ExportableItem] (including TodoStep) to Google Tasks.
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
