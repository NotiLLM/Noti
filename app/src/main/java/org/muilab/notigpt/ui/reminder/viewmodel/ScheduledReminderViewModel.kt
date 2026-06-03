package org.muilab.notigpt.ui.reminder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.reminder.ScheduledReminderRepository
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.SavedItem

class ScheduledReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ScheduledReminderRepository

    init {
        val db = AppDatabase.getInstance(application)
        repo = ScheduledReminderRepository(application.applicationContext, db.reminderDao())
    }

    val reminders: StateFlow<List<Reminder>> = repo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueUnseenCount: StateFlow<Int> = repo.observeDueUnseenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun refreshDueStates() {
        viewModelScope.launch { repo.refreshDueStates() }
    }

    fun createForSavedItem(savedItem: SavedItem, remindAtMs: Long) {
        viewModelScope.launch { repo.createForSavedItem(savedItem, remindAtMs) }
    }

    fun createForNotiRecords(title: String, content: String, notiRecordIds: List<String>, remindAtMs: Long) {
        viewModelScope.launch { repo.createForNotiRecords(title, content, notiRecordIds, remindAtMs) }
    }

    fun markSeen(reminderId: String) {
        viewModelScope.launch { repo.markSeen(reminderId) }
    }

    fun reschedule(reminderId: String, remindAtMs: Long) {
        viewModelScope.launch { repo.reschedule(reminderId, remindAtMs) }
    }

    fun cancel(reminderId: String) {
        viewModelScope.launch { repo.cancel(reminderId) }
    }
}
