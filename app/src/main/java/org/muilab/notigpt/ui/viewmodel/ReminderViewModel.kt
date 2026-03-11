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
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.repository.GoogleTasksRepository
import org.muilab.notigpt.repository.ReminderRepository
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
    private val googleTasksRepo: GoogleTasksRepository

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = ReminderRepository(db.reminderListDao(), application.applicationContext)
        googleTasksRepo = GoogleTasksRepository(application.applicationContext)
    }

    private val _filter = MutableStateFlow(FilterTab.All)
    val filter: StateFlow<FilterTab> = _filter

    fun setFilter(tab: FilterTab) {
        _filter.value = tab
    }

    private val allFlow = repo.observeAll()
    private val tasksFlow = repo.observeTasks()
    private val memosFlow = repo.observeMemos()
    private val completedFlow = repo.observeCompletedTasks()

    val reminders: StateFlow<List<ReminderUnit>> = combine(_filter, allFlow, tasksFlow, memosFlow, completedFlow) { f, all, tasks, memos, completed ->
        when (f) {
            FilterTab.All -> all
            FilterTab.Tasks -> tasks
            FilterTab.Memos -> memos
            FilterTab.Completed -> completed
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleCompleted(reminder: ReminderUnit, completed: Boolean) {
        viewModelScope.launch {
            repo.setCompleted(reminder.reminderId, completed, System.currentTimeMillis())
        }
    }

    fun delete(reminderId: String) {
        viewModelScope.launch {
            repo.deleteById(reminderId, System.currentTimeMillis())
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
            associatedNotis = emptySet(),
            extractionSnapshotId = null,
            origin = "manual",
            humanEditCount = 0,
            deletedAtMs = null,
            userEdited = false,
        )
        upsert(reminder)
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
                is GoogleTasksRepository.TaskResult.Success -> {
                    GoogleTasksExportResult.Success(result.taskTitle)
                }
                is GoogleTasksRepository.TaskResult.Error -> {
                    GoogleTasksExportResult.Error(result.message)
                }
                is GoogleTasksRepository.TaskResult.NotSignedIn -> {
                    GoogleTasksExportResult.NotSignedIn
                }
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
