package org.muilab.notigpt.platform

import android.content.Context
import android.widget.Toast

/**
 * UI-facing notifications (Toast/snackbar etc.).
 *
 * This is intentionally tiny so ViewModels can depend on it without touching Android UI classes.
 */
interface UserNotifier {
    fun showShort(message: String)
}

class ToastUserNotifier(private val context: Context) : UserNotifier {
    override fun showShort(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
