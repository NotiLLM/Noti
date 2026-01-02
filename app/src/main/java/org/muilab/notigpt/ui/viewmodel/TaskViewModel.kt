package org.muilab.notigpt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.features.TaskUnit
import org.muilab.notigpt.repository.TaskRepository

class TaskViewModel(private val application: Application, private val taskRepository: TaskRepository): AndroidViewModel(application) {

    // Expose visible tasks as StateFlow for Compose collection
    val tasks: StateFlow<List<TaskUnit>> = taskRepository.observeVisibleTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unfinishedCount: StateFlow<Int> = taskRepository.observeUnfinishedCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun toggleComplete(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            taskRepository.setCompleted(taskId, completed)
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteById(taskId)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            taskRepository.clearCompleted()
        }
    }

    fun editTask(taskUnit: TaskUnit) {
        viewModelScope.launch {
            taskRepository.upsert(taskUnit)
        }
    }
}
