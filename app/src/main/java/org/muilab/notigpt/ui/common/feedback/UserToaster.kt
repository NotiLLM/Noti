package org.muilab.notigpt.ui.common.feedback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Platform boundary for short user-visible status messages.
 *
 * Compose/ViewModel code should depend on UserToaster rather than Toast directly. Keep timing/cancellation
 * behavior in the Android implementation so callers only express the message intent.
 */
interface UserToaster {
    fun showShort(message: String, duration: Long = 1_000L)
}

/**
 * Default [UserToaster] that routes messages to the app-wide [AppSnackbar] bus (shown as Material
 * snackbars by AppScaffold). Preferred over [ToastUserToaster] so ViewModel status messages match the
 * rest of the app's feedback. Context-free; [duration] is ignored (snackbars use their own timing).
 */
class SnackbarUserToaster : UserToaster {
    override fun showShort(message: String, duration: Long) {
        AppSnackbar.show(message)
    }
}

class ToastUserToaster(context: Context) : UserToaster {

    // Use applicationContext to avoid leaking an Activity
    private val appContext = context.applicationContext

    private val handler = Handler(Looper.getMainLooper())
    private var lastToast: Toast? = null
    private var lastCancelRunnable: Runnable? = null

    override fun showShort(message: String, duration: Long) {
        // Cancel previous toast + pending cancel callback
        lastCancelRunnable?.let(handler::removeCallbacks)
        lastToast?.cancel()

        val toast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
        lastToast = toast

        toast.show()

        val r = Runnable {
            // Only cancel if it's still the latest toast
            if (lastToast === toast) toast.cancel()
        }
        lastCancelRunnable = r
        handler.postDelayed(r, duration.coerceAtLeast(0L))
    }
}