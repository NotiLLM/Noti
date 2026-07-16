package org.muilab.notigpt.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log
import org.muilab.notigpt.service.NotiListenerService

/**
 * Alarm target used as a last-resort notification-listener recovery signal.
 *
 * Android owns the lifecycle of a [NotificationListenerService], so this receiver asks the system
 * to rebind it instead of attempting a restricted background service start.
 */
class NotiListenerRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REQUEST_REBIND) return

        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotiListenerService::class.java),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to request notification-listener rebind", error)
        }
    }

    companion object {
        private const val TAG = "NotiListenerRestart"
        private const val REQUEST_CODE = 44
        private const val ACTION_REQUEST_REBIND =
            "org.muilab.notigpt.action.REQUEST_NOTIFICATION_LISTENER_REBIND"

        /** Stable explicit PendingIntent used to schedule or cancel the recovery alarm. */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, NotiListenerRestartReceiver::class.java).apply {
                action = ACTION_REQUEST_REBIND
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
