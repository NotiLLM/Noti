package org.muilab.notigpt.ui.common.feedback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A single, one-line status message to surface as a Material snackbar.
 *
 * Text is pre-resolved by the caller (composables via `stringResource`, ViewModels via
 * `getApplication().getString`) so the bus stays context-free.
 */
data class AppSnackbarMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMillis: Long? = null,
)

/**
 * Process-wide bus for transient status messages, replacing scattered `Toast`s.
 *
 * The app is single-activity; [org.muilab.notigpt.ui.common.component.AppScaffold] collects [messages]
 * and shows them on its shared `SnackbarHostState`. Emit from anywhere — UI or ViewModel — via [show].
 * Messages emitted while no collector is active (e.g. on the sign-in gate) are simply dropped, which is
 * the right behaviour for ephemeral status.
 */
object AppSnackbar {
    private val _messages = MutableSharedFlow<AppSnackbarMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<AppSnackbarMessage> = _messages.asSharedFlow()

    fun show(text: String) {
        _messages.tryEmit(AppSnackbarMessage(text))
    }

    fun show(message: AppSnackbarMessage) {
        _messages.tryEmit(message)
    }
}
