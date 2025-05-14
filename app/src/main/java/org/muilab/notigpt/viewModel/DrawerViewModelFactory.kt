package org.muilab.notigpt.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.muilab.notigpt.repository.NotiRepository

class DrawerViewModelFactory(private val application: Application, private val notiRepository: NotiRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrawerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DrawerViewModel(application, notiRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}