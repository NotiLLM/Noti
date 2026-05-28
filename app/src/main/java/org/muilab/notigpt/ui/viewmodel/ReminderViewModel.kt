package org.muilab.notigpt.ui.viewmodel

import android.app.Application
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
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.enqueueRegenerateAll
import org.muilab.notigpt.database.server.enqueueRegenerateOne
import org.muilab.notigpt.database.server.enqueueRerank
import org.muilab.notigpt.model.features.ExportableItem
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.features.SubTask
import org.muilab.notigpt.repository.GoogleTasksRepository
import org.muilab.notigpt.repository.ReminderRepository
import org.muilab.notigpt.repository.SubTaskRepository
import org.muilab.notigpt.platform.GoogleTasksAuthManager
import java.util.UUID

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    enum class FilterTab { All, Tasks, Memos, Completed }

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

    private val repo: ReminderRepository
    private val subTaskRepo: SubTaskRepository
    private val googleTasksRepo: GoogleTasksRepository

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = ReminderRepository(db.reminderListDao(), application.applicationContext)
        subTaskRepo = SubTaskRepository(db.subTaskDao())
        googleTasksRepo = GoogleTasksRepository(application.applicationContext)
    }

    private val _filter = MutableStateFlow(FilterTab.All)
    val filter: StateFlow<FilterTab> = _filter

    fun setFilter(tab: FilterTab) {
        _filter.value = tab
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

    val reminders: StateFlow<List<ReminderUnit>> = combine(_filter, _searchQuery, allFlow, tasksFlow, memosFlow, completedFlow) { values ->
        val f = values[0] as FilterTab
        @Suppress("UNCHECKED_CAST")
        val query = values[1] as String
        @Suppress("UNCHECKED_CAST")
        val all = values[2] as List<ReminderUnit>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[3] as List<ReminderUnit>
        @Suppress("UNCHECKED_CAST")
        val memos = values[4] as List<ReminderUnit>
        @Suppress("UNCHECKED_CAST")
        val completed = values[5] as List<ReminderUnit>

        val baseList = when (f) {
            FilterTab.All -> all
            FilterTab.Tasks -> tasks
            FilterTab.Memos -> memos
            FilterTab.Completed -> completed
        }

        if (query.isBlank()) {
            baseList
        } else {
            val terms = query.split("+").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (terms.isEmpty()) {
                baseList
            } else {
                baseList.filter { reminder ->
                    val searchable = "${reminder.reminderTitle} ${reminder.reminderContent}".lowercase()
                    terms.all { term -> searchable.contains(term) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sub-tasks grouped by parent reminder ID. */
    val allSubTasksByReminder: StateFlow<Map<String, List<SubTask>>> =
        subTaskRepo.observeAllByReminder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleCompleted(reminder: ReminderUnit, completed: Boolean) {
        viewModelScope.launch {
            repo.setCompleted(reminder.reminderId, completed, System.currentTimeMillis())
        }
    }

    fun delete(reminderId: String) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            subTaskRepo.softDeleteByParentId(reminderId, ts)
            repo.deleteById(reminderId, ts)
        }
    }

    fun upsert(reminder: ReminderUnit) {
        viewModelScope.launch {
            repo.upsert(reminder.copy(lastUpdateTimestamp = System.currentTimeMillis()))
        }
    }

    fun addNew(isTask: Boolean) {
        val id = "r_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val reminder = ReminderUnit(
            reminderId = id,
            reminderTitle = "",
            reminderContent = "",
            isTask = isTask,
            isCompleted = false,
            lastUpdateTimestamp = now,
            deadlineTimestamp = 0L,
            estimatedCompletionTime = 0L,
            associatedNotiRecords = emptySet(),
            extractionSnapshotId = null,
            origin = "manual",
            humanEditCount = 0,
            deletedAtMs = null,
            userEdited = false,
        )
        upsert(reminder)
    }

    // ========== Sub-task CRUD ==========

    fun addSubTask(parentReminderId: String) {
        val id = "st_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val subTask = SubTask(
            subTaskId = id,
            parentReminderId = parentReminderId,
            title = "",
            isTask = true,
            createdAt = now,
            lastUpdateTimestamp = now,
        )
        viewModelScope.launch { subTaskRepo.upsert(subTask) }
    }

    fun upsertSubTask(subTask: SubTask) {
        viewModelScope.launch {
            subTaskRepo.upsert(subTask.copy(lastUpdateTimestamp = System.currentTimeMillis()))
        }
    }

    fun deleteSubTask(subTaskId: String) {
        viewModelScope.launch {
            subTaskRepo.softDeleteById(subTaskId, System.currentTimeMillis())
        }
    }

    fun toggleSubTaskCompleted(subTaskId: String, completed: Boolean) {
        viewModelScope.launch {
            subTaskRepo.setCompleted(subTaskId, completed, System.currentTimeMillis())
        }
    }

    // ========== Sorting / Ranking ==========

    fun togglePinned(reminderId: String) {
        viewModelScope.launch {
            val existing = repo.getById(reminderId) ?: return@launch
            repo.setPinned(reminderId, !existing.isPinned)
        }
    }

    fun markViewed(reminderId: String) {
        viewModelScope.launch {
            repo.setViewed(reminderId)
        }
    }

    /** Batch-mark multiple reminders as viewed (called when leaving the screen). */
    fun markViewedBatch(reminderIds: Set<String>) {
        if (reminderIds.isEmpty()) return
        viewModelScope.launch {
            reminderIds.forEach { repo.setViewed(it) }
        }
    }

    /**
     * Handle drag-and-drop reorder within the scored section.
     * [scoreAbove] and [scoreBelow] are the sort scores of the neighbors,
     * or null if at the top/bottom of the scored section.
     */
    fun onDragDrop(reminderId: String, scoreAbove: Float?, scoreBelow: Float?) {
        viewModelScope.launch {
            val epsilon = 0.01f
            val newScore = when {
                scoreAbove != null && scoreBelow != null -> (scoreAbove + scoreBelow) / 2f
                scoreAbove == null && scoreBelow != null -> minOf(100f, scoreBelow + epsilon)
                scoreAbove != null && scoreBelow == null -> maxOf(0f, scoreAbove - epsilon)
                else -> 50f
            }

            val existing = repo.getById(reminderId) ?: return@launch
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

            repo.updateSortScoreAndHistory(reminderId, newScore, history.toString())

            // Score normalization check
            normalizeScoresIfNeeded()
        }
    }

    fun submitFeedback(reminderId: String, trigger: String) {
        viewModelScope.launch {
            enqueueRerank(getApplication(), reminderId, trigger)
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
            repo.updateSortScoreAndHistory(reminder.reminderId, newScore, reminder.reRankHistory)
        }
    }

    // ========== Regeneration ==========

    fun regenerateOne(reminderId: String) {
        enqueueRegenerateOne(getApplication(), reminderId)
    }

    fun regenerateAll() {
        enqueueRegenerateAll(getApplication())
    }

    // ========== Buttons ==========

    /**
     * Add a copy button to a reminder's button list.
     */
    fun addCopyButton(reminderId: String, text: String) {
        viewModelScope.launch {
            val existing = repo.getById(reminderId) ?: return@launch
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
            repo.updateButtons(reminderId, arr.toString())
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

    /**
     * Export a reminder to Google Tasks.
     */
    fun exportToGoogleTasks(reminder: ReminderUnit) {
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
     * Export any [ExportableItem] (including SubTask) to Google Tasks.
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
