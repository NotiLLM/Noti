package org.muilab.notigpt.ui.reminder.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.enqueueRegenerateAll
import org.muilab.notigpt.data.remote.n8n.enqueueRegenerateOne
import org.muilab.notigpt.data.remote.n8n.enqueueRerank
import org.muilab.notigpt.data.export.ExportableItem
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemRepository
import org.muilab.notigpt.data.repository.reminder.ReminderRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.reminder.SavedSubItemRepository
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager
import java.util.UUID

/**
 * ViewModel for reminders, sub-tasks, related notification context, Google Tasks export, and regeneration jobs.
 *
 * Keep screen orchestration here while persistence stays in repositories and remote/background work stays behind
 * platform or n8n enqueue helpers.
 */
class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    enum class FilterTab { All, Pending, Tasks, Memos, Completed }
    enum class ListMode { All, Tasks, Keep }

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
    private val relatedNotificationsRepo: ReminderRelatedNotificationsRepository

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = SavedItemRepository(db.reminderListDao(), application.applicationContext)
        subTaskRepo = SavedSubItemRepository(db.subTaskDao())
        googleTasksRepo = GoogleTasksRepository(application.applicationContext)
        relatedNotificationsRepo = ReminderRelatedNotificationsRepository(application.applicationContext)
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

    private val allFlow = repo.observeAll()
    private val tasksFlow = repo.observeTasks()
    private val memosFlow = repo.observeMemos()
    private val completedFlow = repo.observeCompletedTasks()
    private val newItemsFlow = repo.observeNewItems()

    val allReminders: StateFlow<List<SavedItem>> = allFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newSavedItems: StateFlow<List<SavedItem>> = newItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Filtered reminder list consumed by RemindersScreen.
     *
     * Search terms are split on '+', then AND-matched against title and content so users can narrow noisy reminder
     * lists without changing persisted reminder data.
     */
    val reminders: StateFlow<List<SavedItem>> = combine(_filter, _searchQuery, _listMode, allFlow, tasksFlow, memosFlow, completedFlow) { values ->
        val f = values[0] as FilterTab
        @Suppress("UNCHECKED_CAST")
        val query = values[1] as String
        val mode = values[2] as ListMode
        @Suppress("UNCHECKED_CAST")
        val all = values[3] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[4] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val memos = values[5] as List<SavedItem>
        @Suppress("UNCHECKED_CAST")
        val completed = values[6] as List<SavedItem>

        val modeList = when (mode) {
            ListMode.All -> all
            ListMode.Tasks -> tasks
            ListMode.Keep -> memos
        }
        val modeIds = modeList.mapTo(mutableSetOf()) { it.savedItemId }

        val baseList = when (f) {
            FilterTab.All -> modeList
            FilterTab.Pending -> modeList.filter { !it.isCompleted }
            FilterTab.Tasks -> tasks.filter { it.savedItemId in modeIds }
            FilterTab.Memos -> memos.filter { it.savedItemId in modeIds }
            FilterTab.Completed -> completed.filter { it.savedItemId in modeIds }
        }

        if (query.isBlank()) {
            baseList
        } else {
            val terms = query.split("+").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (terms.isEmpty()) {
                baseList
            } else {
                baseList.filter { reminder ->
                    val searchable = "${reminder.title} ${reminder.content}".lowercase()
                    terms.all { term -> searchable.contains(term) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sub-tasks grouped by parent reminder ID. */
    val allSavedSubItemsByReminder: StateFlow<Map<String, List<SavedSubItem>>> =
        subTaskRepo.observeAllByReminder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleCompleted(reminder: SavedItem, completed: Boolean) {
        viewModelScope.launch {
            repo.setCompleted(reminder.savedItemId, completed, System.currentTimeMillis())
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
            val ts = System.currentTimeMillis()
            savedItemIds.forEach { subTaskRepo.softDeleteByParentId(it, ts) }
            repo.deleteByIds(savedItemIds, ts)
        }
    }

    fun delete(savedItemId: String) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            subTaskRepo.softDeleteByParentId(savedItemId, ts)
            repo.deleteById(savedItemId, ts)
        }
    }

    fun upsert(reminder: SavedItem) {
        viewModelScope.launch {
            repo.upsert(reminder.copy(lastUpdateTimestamp = System.currentTimeMillis()))
        }
    }

    /** Creates an empty manual reminder or memo and lets the screen open it for editing. */
    fun addNew(isTask: Boolean) {
        val id = "r_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val reminder = SavedItem(
            savedItemId = id,
            title = "",
            content = "",
            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
            state = SavedItemState.Saved,
            lastUpdateTimestamp = now,
            deadlineAtMs = 0L,
            estimatedCompletionTime = 0L,
            sourceNotiRecordIds = emptySet(),
            sourceExtractionSnapshotId = null,
            origin = "manual",
            humanEditCount = 0,
            deletedAtMs = null,
            userEdited = false,
        )
        upsert(reminder)
    }


    data class RelatedNotificationsState(
        val savedItemId: String? = null,
        val isLoading: Boolean = false,
        val related: ReminderRelatedNotificationsRepository.RelatedNotifications = ReminderRelatedNotificationsRepository.RelatedNotifications.Empty,
    )

    private val _relatedNotificationsState = MutableStateFlow(RelatedNotificationsState())
    val relatedNotificationsState: StateFlow<RelatedNotificationsState> = _relatedNotificationsState

    /**
     * Loads notification context linked to a reminder for provenance/preview UI.
     *
     * This is read-only reminder context; edits to reminders or notifications should use their own repository paths.
     */
    fun loadRelatedNotifications(reminder: SavedItem) {
        val current = _relatedNotificationsState.value
        if (current.savedItemId == reminder.savedItemId && current.isLoading) return

        viewModelScope.launch {
            _relatedNotificationsState.value = RelatedNotificationsState(
                savedItemId = reminder.savedItemId,
                isLoading = true,
            )

            val related = try {
                relatedNotificationsRepo.getRelatedNotifications(reminder)
            } catch (t: Throwable) {
                Log.e("ReminderRelatedNotis", "Failed loading related notifications", t)
                ReminderRelatedNotificationsRepository.RelatedNotifications.Empty
            }

            _relatedNotificationsState.value = RelatedNotificationsState(
                savedItemId = reminder.savedItemId,
                isLoading = false,
                related = related,
            )
        }
    }

    // ========== Sub-task CRUD ==========

    fun addSavedSubItem(parentSavedItemId: String) {
        val id = "st_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val subTask = SavedSubItem(
            savedSubItemId = id,
            parentSavedItemId = parentSavedItemId,
            title = "",
            itemType = SavedItemType.Task,
            createdAt = now,
            lastUpdateTimestamp = now,
        )
        viewModelScope.launch { subTaskRepo.upsert(subTask) }
    }

    fun upsertSavedSubItem(subTask: SavedSubItem) {
        viewModelScope.launch {
            subTaskRepo.upsert(subTask.copy(lastUpdateTimestamp = System.currentTimeMillis()))
        }
    }

    fun deleteSavedSubItem(savedSubItemId: String) {
        viewModelScope.launch {
            subTaskRepo.softDeleteById(savedSubItemId, System.currentTimeMillis())
        }
    }

    fun toggleSavedSubItemCompleted(savedSubItemId: String, completed: Boolean) {
        viewModelScope.launch {
            subTaskRepo.setCompleted(savedSubItemId, completed, System.currentTimeMillis())
        }
    }

    // ========== Sorting / Ranking ==========

    fun togglePinned(savedItemId: String) {
        viewModelScope.launch {
            val existing = repo.getById(savedItemId) ?: return@launch
            repo.setPinned(savedItemId, !existing.isPinned)
        }
    }

    fun markViewed(savedItemId: String) {
        viewModelScope.launch {
            repo.setViewed(savedItemId)
        }
    }

    /** Batch-mark multiple reminders as viewed (called when leaving the screen). */
    fun markViewedBatch(savedItemIds: Set<String>) {
        if (savedItemIds.isEmpty()) return
        viewModelScope.launch {
            savedItemIds.forEach { repo.setViewed(it) }
        }
    }

    /**
     * Handle drag-and-drop reorder within the scored section.
     * [scoreAbove] and [scoreBelow] are the sort scores of the neighbors,
     * or null if at the top/bottom of the scored section.
     */
    fun onDragDrop(savedItemId: String, scoreAbove: Float?, scoreBelow: Float?) {
        viewModelScope.launch {
            val epsilon = 0.01f
            val newScore = when {
                scoreAbove != null && scoreBelow != null -> (scoreAbove + scoreBelow) / 2f
                scoreAbove == null && scoreBelow != null -> minOf(100f, scoreBelow + epsilon)
                scoreAbove != null && scoreBelow == null -> maxOf(0f, scoreAbove - epsilon)
                else -> 50f
            }

            val existing = repo.getById(savedItemId) ?: return@launch
            val history = try {
                JSONArray(existing.reRankHistory)
            } catch (_: Exception) {
                JSONArray()
            }
            history.put(JSONObject().apply {
                put("rankedAt", System.currentTimeMillis())
                put("trigger", "USER_DRAG")
                put("newScore", newScore)
                put("scoreExplanation", "User manually reordered reminder via drag")
            })

            repo.updateSortScoreAndHistory(savedItemId, newScore, history.toString())

            // Score normalization check
            normalizeScoresIfNeeded()
        }
    }

    fun submitFeedback(savedItemId: String, trigger: String) {
        viewModelScope.launch {
            enqueueRerank(getApplication(), savedItemId, trigger)
        }
    }

    private suspend fun normalizeScoresIfNeeded() {
        val all = repo.getAllVisible()
        val scored = all.filter { it.isViewed && !it.isPinned }
            .sortedByDescending { it.sortScore }

        if (scored.size < 2) return

        var minGap = Float.MAX_VALUE
        for (i in 0 until scored.size - 1) {
            val gap = scored[i].sortScore - scored[i + 1].sortScore
            if (gap < minGap) minGap = gap
        }

        if (minGap >= 0.001f) return

        // Renormalize: evenly space from 100 to 0
        val step = if (scored.size > 1) 100f / (scored.size - 1) else 0f
        for ((idx, reminder) in scored.withIndex()) {
            val newScore = 100f - (step * idx)
            // Do NOT append reRankHistory for normalization
            repo.updateSortScoreAndHistory(reminder.savedItemId, newScore, reminder.reRankHistory)
        }
    }

    // ========== Regeneration ==========

    fun regenerateOne(savedItemId: String) {
        enqueueRegenerateOne(getApplication(), savedItemId)
    }

    fun regenerateAll() {
        enqueueRegenerateAll(getApplication())
    }

    // ========== Buttons ==========

    /**
     * Add a copy button to a reminder's button list.
     */
    fun addCopyButton(savedItemId: String, text: String) {
        viewModelScope.launch {
            val existing = repo.getById(savedItemId) ?: return@launch
            val arr = try {
                JSONArray(existing.buttons)
            } catch (_: Exception) {
                JSONArray()
            }
            // Check for duplicate
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("type") == "copy" && obj.optString("intent") == text) return@launch
            }
            arr.put(JSONObject().apply {
                put("buttonText", text)
                put("intent", text)
                put("type", "copy")
            })
            repo.updateButtons(savedItemId, arr.toString())
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
     * Export a reminder to Google Tasks.
     */
    fun exportToGoogleTasks(reminder: SavedItem) {
        viewModelScope.launch {
            _googleTasksExportResult.value = GoogleTasksExportResult.Loading
            val result = googleTasksRepo.createTaskFromReminder(reminder)
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
