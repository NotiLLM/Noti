package org.muilab.notigpt.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Platform boundary for short user-visible status messages.
 *
 * Compose/ViewModel code should depend on UserNotifier rather than Toast directly. Keep timing/cancellation
 * behavior in the Android implementation so callers only express the message intent.
 */
interface UserNotifier {
    fun showShort(message: String, duration: Long = 1_000L)
}

class ToastUserNotifier(context: Context) : UserNotifier {

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