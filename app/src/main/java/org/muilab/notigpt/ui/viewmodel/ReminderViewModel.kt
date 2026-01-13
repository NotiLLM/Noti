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
import org.muilab.notigpt.repository.ReminderRepository
import java.util.UUID

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    enum class FilterTab { All, Tasks, Memos }

    private val repo: ReminderRepository

    init {
        val db = AppDatabase.getInstance(application.applicationContext)
        repo = ReminderRepository(db.reminderListDao())
    }

    private val _filter = MutableStateFlow(FilterTab.All)
    val filter: StateFlow<FilterTab> = _filter

    fun setFilter(tab: FilterTab) {
        _filter.value = tab
    }

    private val allFlow = repo.observeAll()
    private val tasksFlow = repo.observeTasks()
    private val memosFlow = repo.observeMemos()

    val reminders: StateFlow<List<ReminderUnit>> = combine(_filter, allFlow, tasksFlow, memosFlow) { f, all, tasks, memos ->
        when (f) {
            FilterTab.All -> all
            FilterTab.Tasks -> tasks
            FilterTab.Memos -> memos
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleCompleted(reminder: ReminderUnit, completed: Boolean) {
        viewModelScope.launch {
            repo.setCompleted(reminder.reminderId, completed, System.currentTimeMillis())
        }
    }

    fun delete(reminderId: String) {
        viewModelScope.launch {
            repo.deleteById(reminderId)
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
            userEdited = false,
        )
        upsert(reminder)
    }
}
