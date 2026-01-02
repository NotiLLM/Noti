package org.muilab.notigpt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.muilab.notigpt.repository.TaskRepository

class TaskViewModelFactory(private val application: Application, private val taskRepository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(application, taskRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

