package org.muilab.notigpt.ui.notification.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import org.muilab.notigpt.data.export.MediaStoreNotiLogExporter
import org.muilab.notigpt.ui.common.feedback.SnackbarUserToaster

/**
 * Factory for constructing DrawerViewModel with Android context-backed repositories.
 *
 * Keep dependency creation here minimal. If the drawer starts needing more collaborators, consider a small
 * dependency provider so the factory does not become the app's composition root.
 */
class DrawerViewModelFactory(
    private val application: Application,
    private val notiRepository: NotiRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrawerViewModel::class.java)) {
            val appContext = application.applicationContext
            @Suppress("UNCHECKED_CAST")
            return DrawerViewModel(
                application = application,
                notiRepository = notiRepository,
                clipboard = AndroidClipboardController(appContext),
                notifier = SnackbarUserToaster(),
                logExporter = MediaStoreNotiLogExporter(appContext),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}